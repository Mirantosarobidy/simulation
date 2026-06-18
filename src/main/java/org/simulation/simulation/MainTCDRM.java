package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

import static org.simulation.simulation.SimulationParameters.NB_REPEAT;

public class MainTCDRM {

    public static void main(String[] args) {
        Log.println("╔══════════════════════════════════════════╗");
        Log.println("║   Simulation Multi-Cloud - TCDRM         ║");
        Log.println("╚══════════════════════════════════════════╝\n");

        try {
            // ═══════════════════════════════════════════
// PASSE 1 : NoRep — trouver le point de bascule
// ═══════════════════════════════════════════
            CloudSim.init(1, Calendar.getInstance(), false);
            List<Provider> providers = Creation.createProviders();
            Map<String, Datacenter> dcMap1 = DatacenterFactory.createAll(providers, SimulationParameters.VM_PER_DC);
            MulticloudBroker broker1 = new MulticloudBroker("Broker1", providers, dcMap1);
            DatacenterFactory.initNetworkTopology(dcMap1, broker1.getId());
            VmFactory.Result vmResult1 = VmFactory.createAll(broker1.getId(), providers);
            broker1.submitGuestList(vmResult1.vmList);

            List<Query> queries = new ArrayList<>();
            queries.add(new Query(1, "UE", NB_REPEAT, List.of("F1", "F11", "F21")));

            SLA sla = new SLA(
                    SimulationParameters.RESP_TIME_SLO,
                    SimulationParameters.COST_SLO,
                    SimulationParameters.POPULARITY_SLO,
                    SimulationParameters.DELTA_T);

            new QueryScheduler("QS1", broker1, vmResult1, queries, providers);

            CloudSim.startSimulation();
            List<Cloudlet> finished1 = broker1.getCloudletReceivedList();
            CloudSim.stopSimulation();

            QueryAnalyzer.analyze(queries, finished1, vmResult1, providers,
                    QueryFactory.getCloudletToQuery());
            List<CloudletMetrics> norepMetrics = new ArrayList<>(
                    QueryAnalyzer.getCloudletMetricsList());

// ── Identifier les réplicas nécessaires ──────────────────
            TCDRMStrategy probe = new TCDRMStrategy(sla, providers, vmResult1);
// Map : fileName → cloudletId du premier déclenchement
            Map<String, Integer> replicaTriggerAt = new LinkedHashMap<>();

            for (CloudletMetrics m : norepMetrics.stream()
                    .sorted(Comparator.comparingInt(x -> x.cloudletId)).toList()) {

                Query q = QueryFactory.getCloudletToQuery().get(m.cloudletId);
                if (q == null) continue;

                List<String> toRep = probe.checkAndReplicate(q, m);
                if (!toRep.isEmpty()) {
                    probe.placeReplicas(toRep, q, m.start);
                    for (String fn : toRep) {
                        replicaTriggerAt.putIfAbsent(fn, m.cloudletId);
                        Log.println(String.format(
                                "[Passe1] Réplica %s déclenché au cloudlet #%d",
                                fn, m.cloudletId));
                    }
                }
            }

            Log.println("\n=== Réplicas identifiés : " + replicaTriggerAt + " ===");

// ═══════════════════════════════════════════
// PASSE 2 : TCDRM — avec réplicas dans CloudSim
// ═══════════════════════════════════════════
            CloudSim.init(1, Calendar.getInstance(), false);

            Map<String, Datacenter> dcMap2 = DatacenterFactory.createAll(
                    providers, SimulationParameters.VM_PER_DC);
            MulticloudBroker broker2 = new MulticloudBroker("Broker2", providers, dcMap2);
            DatacenterFactory.initNetworkTopology(dcMap2, broker2.getId());

            VmFactory.Result vmResult2 = VmFactory.createAll(broker2.getId(), providers);

// ── Créer les VMs réplicas et les injecter ────────────────
            Map<String, ExtendedVm> replicaVmMap = new LinkedHashMap<>();
            List<ExtendedVm> allVms = new ArrayList<>(vmResult2.vmList);

            for (Map.Entry<String, Integer> entry : replicaTriggerAt.entrySet()) {
                String fileName    = entry.getKey();
                ExtendedVm sourceVm = vmResult2.relationVmMap.get(fileName);
                if (sourceVm == null) continue;

                // Utiliser le résultat de placement de la passe 1
                Replica placedReplica = probe.getReplicas()
                        .getOrDefault(fileName, List.of())
                        .stream().filter(Replica::isActive).findFirst().orElse(null);

                if (placedReplica == null) continue;

                String targetProvider = placedReplica.getTargetProvider();
                String targetRegion   = placedReplica.getTargetRegion();
                String targetDcName   = placedReplica.getTargetDcName();

                Provider prov = providers.stream()
                        .filter(p -> p.getName().equals(targetProvider))
                        .findFirst().orElse(providers.get(0));

                ExtendedVm replicaVm = new ExtendedVm(
                        vmResult2.vmList.size() + replicaVmMap.size(),
                        broker2.getId(),
                        prov.getMips(targetRegion),
                        prov.getPes(targetRegion),
                        prov.getRam(targetRegion),
                        prov.getBw(targetRegion),
                        10000L, "Xen",
                        new CloudletSchedulerTimeShared(),
                        targetRegion, targetProvider, targetDcName
                );
                replicaVm.setRelationName(fileName + "_replica");
                replicaVm.setRelationSizeMB(sourceVm.getRelationSizeMB());

                replicaVmMap.put(fileName, replicaVm);
                allVms.add(replicaVm);

                Log.println(String.format(
                        "[Passe2] VM réplica créée : %s → %s/%s",
                        fileName, targetProvider, targetRegion));
            }

            broker2.submitGuestList(allVms);

// ── QueryScheduler avec routing vers réplicas ─────────────
            QueryFactory.clearMaps();
            new QuerySchedulerTCDRM(
                    "QS2", broker2, vmResult2,
                    queries, providers,
                    replicaTriggerAt, replicaVmMap);  // ← router post-trigger

            CloudSim.startSimulation();
            List<Cloudlet> finished2 = broker2.getCloudletReceivedList();
            CloudSim.stopSimulation();

            QueryAnalyzer.analyze(queries, finished2, vmResult2, providers,
                    QueryFactory.getCloudletToQuery());
            List<CloudletMetrics> tcdrmMetrics = QueryAnalyzer.getCloudletMetricsList();

        } catch (Exception e) {
            e.printStackTrace();
            Log.println("[ERREUR] " + e.getMessage());
        }
    }
}