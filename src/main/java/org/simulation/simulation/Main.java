package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
public class Main {
    public static void main(String[] args) {
        Log.println("╔══════════════════════════════════════════╗");
        Log.println("║   Simulation Multi-Cloud - NoRepLc       ║");
        Log.println("╚══════════════════════════════════════════╝\n");

        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<Provider> providers = Creation.createProviders();

            Map<String, Datacenter> dcMap = DatacenterFactory.createAll(providers, 5);
            DisplayResult.printDatacenters(dcMap);

            MulticloudBroker broker = new MulticloudBroker("Broker", providers, dcMap);

            DatacenterFactory.initNetworkTopology(dcMap, broker.getId());

            VmFactory.Result vmResult = VmFactory.createAll(broker.getId(), providers);
            broker.submitGuestList(vmResult.vmList);
            Log.println("Broker contient " + broker.getGuestList().size() + " VMs.\n");

            final int QID = 1;
            final int NB_REPEAT = 1000;

            List<Query> queries = new ArrayList<>();

            Query baseQuery = new Query(QID, "UE", NB_REPEAT, List.of("F1", "F11", "F21"));
            queries.add(baseQuery);

            new QueryScheduler("QueryScheduler", broker, vmResult, queries, providers);

            Log.println(">>> Démarrage simulation...");
            CloudSim.startSimulation();
            List<Cloudlet> finished = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            Log.println(">>> Simulation terminée. (" + finished.size() + " cloudlets)\n");

            Log.println("Cloudlets terminés : " + finished.size());
            Log.println("Cloudlets mappés   : " + QueryFactory.getCloudletToQuery().size());

            if (finished.isEmpty()) {
                Log.println("[ERREUR] Aucun cloudlet reçu !");
                return;
            }
            if (QueryFactory.getCloudletToQuery().isEmpty()) {
                Log.println("[ERREUR] QueryFactory vide !");
                return;
            }

            QueryAnalyzer.analyze(queries, finished, vmResult, providers, QueryFactory.getCloudletToQuery());
            List<CloudletMetrics> allMetrics = QueryAnalyzer.getCloudletMetricsList();

            Log.println("Métriques calculées : " + allMetrics.size());

            Log.println("\n>>> Résultats NoRepLc :");
            DisplayResult.printCloudletListNoRep(allMetrics, queries);

            Log.println("\n── Vérification correspondance code original ──");
            if (!allMetrics.isEmpty()) {
                CloudletMetrics first = allMetrics.get(0);

                Log.println(String.format(
                        "CPU_USAGE attendu : 1000-8499 MI (variable) | obtenu : %.0f %s",
                        first.cpuUsage,
                        (first.cpuUsage >= 1000 && first.cpuUsage <= 8499) ? "✓" : "✗"));

                Log.println(String.format(
                        "CPU_TIME  attendu : ~810-4700ms | obtenu : %.2fms %s",
                        first.cpuTime,
                        (first.cpuTime > 100 && first.cpuTime < 10000) ? "✓" : "✗"));

                Log.println(String.format(
                        "BW_COST   attendu : ~0.0145$    | obtenu : %.5f %s",
                        first.bwCost,
                        first.bwCost > 0.01 ? "✓" : "✗"));

                Log.println(String.format(
                        "DELAY     attendu : 400-5400ms  | obtenu : %.0fms %s",
                        first.delayTime,
                        (first.delayTime >= 400 && first.delayTime <= 5500) ? "✓" : "✗"));

                Log.println(String.format(
                        "RESP_TIME attendu : >180ms (TSLA) | obtenu : %.2fms %s",
                        first.respTime,
                        first.respTime > SimulationParameters.RESP_TIME_SLO ? "→ réplication TCDRM déclenchée" : "→ sous TSLA"));
            }

            DisplayChart.plotNoRepResults(allMetrics);

            Log.println("\n✓ Simulation NoRepLc terminée avec succès.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.println("[ERREUR] " + e.getMessage());
        }
    }
}