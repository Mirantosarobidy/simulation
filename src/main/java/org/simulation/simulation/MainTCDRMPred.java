package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.io.*;
import java.util.*;

import static org.simulation.simulation.SimulationParameters.NB_REPEAT;

public class MainTCDRMPred {

    public static void main(String[] args) {
        try {
            PrintStream fileOut = new PrintStream(new FileOutputStream("resultat_simulation_pred.txt"));
            System.setOut(fileOut);
            System.setErr(fileOut);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.println("╔════════════════════════════════════════════════╗");
        Log.println("║   Simulation Multi-Cloud - TCDRM  Predrictive  ║");
        Log.println("╚════════════════════════════════════════════════╝\n");

        try {
            // MainTCDRMPred.java — structure corrigée

// ═══════════════════════════════════════════
// PASSE 1 : NoRep — alimenter PopularityRegistry
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

// ── P1 : alimenter PopularityRegistry sur métriques NoRep ─
            PopularityRegistry.reset();
            TCDRMPredStrategy probe = new TCDRMPredStrategy(sla, providers, vmResult1);

            Map<String, Integer>  replicaTriggerAt  = new LinkedHashMap<>();
            Map<String, Replica>  placedReplicas    = new LinkedHashMap<>();

            for (CloudletMetrics m : norepMetrics.stream()
                    .sorted(Comparator.comparingInt(x -> x.cloudletId)).toList()) {

                Query q = QueryFactory.getCloudletToQuery().get(m.cloudletId);
                if (q == null) continue;

                // ── P1 : enregistrer accès pour prédiction ────────────
                for (String fn : q.getRequiredFiles()) {
                    PopularityRegistry.getInstance()
                            .recordAccess(fn, m.start * 100.0);
                }

                // ── P1+P2+P3 : décider et placer ─────────────────────
                List<String> toRep = probe.checkAndReplicate(q, m);
                if (!toRep.isEmpty()) {
                    probe.placeReplicas(toRep, q, m.start);
                    for (String fn : toRep) {
                        replicaTriggerAt.putIfAbsent(fn, m.cloudletId);

                        Replica r = probe.getReplicas()
                                .getOrDefault(fn, List.of())
                                .stream().filter(Replica::isActive)
                                .findFirst().orElse(null);
                        if (r != null) placedReplicas.put(fn, r);

                        Log.println(String.format(
                                "[Passe1-Pred] Réplica %s déclenché au CL#%d | " +
                                        "method=%s",
                                fn, m.cloudletId,
                                PopularityRegistry.getInstance()
                                        .currentMethod(fn).name()));
                    }
                }
            }

// ═══════════════════════════════════════════
// PASSE 2 : TCDRM-Pred — avec réplicas dans CloudSim
// ═══════════════════════════════════════════
            CloudSim.init(1, Calendar.getInstance(), false);

            Map<String, Datacenter> dcMap2 = DatacenterFactory.createAll(
                    providers, SimulationParameters.VM_PER_DC);
            MulticloudBroker broker2 = new MulticloudBroker(
                    "Broker2", providers, dcMap2);
            DatacenterFactory.initNetworkTopology(dcMap2, broker2.getId());

            VmFactory.Result vmResult2 = VmFactory.createAll(broker2.getId(), providers);

// ── Créer les VMs réplicas selon placement P2 ─────────────
            Map<String, ExtendedVm> replicaVmMap = new LinkedHashMap<>();
            List<ExtendedVm> allVms = new ArrayList<>(vmResult2.vmList);

            for (Map.Entry<String, Replica> entry : placedReplicas.entrySet()) {
                String fileName = entry.getKey();
                Replica replica = entry.getValue();

                ExtendedVm sourceVm = vmResult2.relationVmMap.get(fileName);
                if (sourceVm == null) continue;

                Provider prov = providers.stream()
                        .filter(p -> p.getName().equals(replica.getTargetProvider()))
                        .findFirst().orElse(providers.get(0));

                String targetRegion = replica.getTargetRegion();

                ExtendedVm replicaVm = new ExtendedVm(
                        vmResult2.vmList.size() + replicaVmMap.size(),
                        broker2.getId(),
                        prov.getMips(targetRegion),
                        prov.getPes(targetRegion),
                        prov.getRam(targetRegion),
                        prov.getBw(targetRegion),
                        10000L, "Xen",
                        new CloudletSchedulerTimeShared(),
                        targetRegion,
                        replica.getTargetProvider(),
                        replica.getTargetDcName()
                );
                replicaVm.setRelationName(fileName + "_replica");
                replicaVm.setRelationSizeMB(sourceVm.getRelationSizeMB());

                replicaVmMap.put(fileName, replicaVm);
                allVms.add(replicaVm);

                Log.println(String.format(
                        "[Passe2-Pred] VM réplica : %s → %s/%s | cohérence=%s",
                        fileName,
                        replica.getTargetProvider(),
                        targetRegion,
                        probe.getReplicas().get(fileName).stream()
                                .findFirst().map(r -> "OK").orElse("?")));
            }

            broker2.submitGuestList(allVms);

// ── QueryScheduler identique à TCDRM statique ─────────────
            QueryFactory.clearMaps();
            new QuerySchedulerTCDRM(
                    "QS2-Pred", broker2, vmResult2,
                    queries, providers,
                    replicaTriggerAt, replicaVmMap);

            CloudSim.startSimulation();
            List<Cloudlet> finished2 = broker2.getCloudletReceivedList();
            CloudSim.stopSimulation();

            QueryAnalyzer.analyze(queries, finished2, vmResult2, providers,
                    QueryFactory.getCloudletToQuery());
            List<CloudletMetrics> tcdrmPredMetrics =
                    QueryAnalyzer.getCloudletMetricsList();
            Log.println("\n✓ Simulation TCDRM terminée avec succès.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.println("[ERREUR] " + e.getMessage());
        }
    }

}