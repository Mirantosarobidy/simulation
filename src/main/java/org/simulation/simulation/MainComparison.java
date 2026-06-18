package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.io.*;
import java.util.*;

import static org.simulation.simulation.SimulationParameters.NB_REPEAT;

public class MainComparison {

    public static void main(String[] args) throws Exception {

        // Redirection des logs
        try {
            PrintStream ps = new PrintStream(new FileOutputStream("simulation_comparison.log"));
            System.setOut(ps);
            System.setErr(ps);
        } catch (FileNotFoundException e) { e.printStackTrace(); }

        printBanner();

        SLA sla = new SLA(
                SimulationParameters.RESP_TIME_SLO,
                SimulationParameters.COST_SLO,
                SimulationParameters.POPULARITY_SLO,
                SimulationParameters.DELTA_T);

        List<Query> queries = List.of(
                new Query(1, "UE", NB_REPEAT, List.of("F1", "F11", "F21")));

        // ════════════════════════════════════════════════════════════
        // PASSE 0 : NoRepLc  (simulation CloudSim réelle)
        // ════════════════════════════════════════════════════════════
        logSection("PASSE 0 : NoRepLc — simulation CloudSim");

        CloudSim.init(1, Calendar.getInstance(), false);
        List<Provider> providers = Creation.createProviders();
        Map<String, Datacenter> dcMap0 = DatacenterFactory.createAll(providers, SimulationParameters.VM_PER_DC);
        MulticloudBroker broker0 = new MulticloudBroker("Broker0", providers, dcMap0);
        DatacenterFactory.initNetworkTopology(dcMap0, broker0.getId());
        VmFactory.Result vmResult0 = VmFactory.createAll(broker0.getId(), providers);
        broker0.submitGuestList(vmResult0.vmList);

        QueryFactory.clearMaps();
        new QueryScheduler("QS0", broker0, vmResult0, queries, providers);

        CloudSim.startSimulation();
        List<Cloudlet> fin0 = broker0.getCloudletReceivedList();
        CloudSim.stopSimulation();
        Log.println("[Passe0] Cloudlets reçus : " + fin0.size());

        // Snapshot de la map AVANT tout clearMaps ultérieur
        Map<Integer, Query> cloudletToQuerySnap = new LinkedHashMap<>(QueryFactory.getCloudletToQuery());

        QueryAnalyzer.analyze(queries, fin0, vmResult0, providers, cloudletToQuerySnap);
        List<CloudletMetrics> norepMetrics = new ArrayList<>(QueryAnalyzer.getCloudletMetricsList());
        Log.println("[Passe0] Métriques calculées : " + norepMetrics.size());

        SimulationMetrics smNorep = MetricsCollector.buildNorep(norepMetrics, sla);
        smNorep.printReport(sla.tsla, sla.csla);

        // ════════════════════════════════════════════════════════════
        // PASSE 1 : TCDRM  (réplication heuristique sur métriques NoRep)
        // ════════════════════════════════════════════════════════════
        logSection("PASSE 1 : TCDRM — heuristique statique");

        TCDRMStrategy tcdrmStrategy = new TCDRMStrategy(sla, providers, vmResult0);
        TCDRMRunner   tcdrmRunner   = new TCDRMRunner(tcdrmStrategy, sla, vmResult0);
        List<CloudletMetrics> tcdrmMetrics = tcdrmRunner.run(queries, norepMetrics, cloudletToQuerySnap);

        SimulationMetrics smTcdrm = MetricsCollector.buildTCDRM(
                norepMetrics, tcdrmMetrics, tcdrmStrategy, tcdrmRunner, sla);
        smTcdrm.thrashingCount = tcdrmRunner.getThrashingCount();
        smTcdrm.printReport(sla.tsla, sla.csla);

        // ════════════════════════════════════════════════════════════
        // PASSE 2 : TCDRM-Pred  (P1 + P2 + P3)
        // ════════════════════════════════════════════════════════════
        logSection("PASSE 2 : TCDRM-Pred — prédictif Pareto + cohérence");

        PopularityRegistry.reset();

        TCDRMPredStrategy predStrategy = new TCDRMPredStrategy(sla, providers, vmResult0);
        TCDRMPredRunner   predRunner   = new TCDRMPredRunner(predStrategy, sla, vmResult0);
        List<CloudletMetrics> predMetrics = predRunner.run(queries, norepMetrics, cloudletToQuerySnap);

        SimulationMetrics smPred = MetricsCollector.buildTCDRMPred(
                norepMetrics, predMetrics, predStrategy, predRunner, sla, predRunner.methodCounts);
        smPred.thrashingCount = predRunner.getThrashingCount();
        smPred.printReport(sla.tsla, sla.csla);

        // ════════════════════════════════════════════════════════════
        // RAPPORT COMPARATIF GLOBAL
        // ════════════════════════════════════════════════════════════
        MetricsCollector.printComparativeReport(smNorep, smTcdrm, smPred, sla);

        // ════════════════════════════════════════════════════════════
        // GRAPHIQUES (8 figures)
        // ════════════════════════════════════════════════════════════
        Log.println("\n[Main] Lancement des graphiques...");
        try {
            DisplayChart.plotAllComparisons(smNorep, smTcdrm, smPred);
        } catch (Exception e) {
            Log.println("[WARN] Erreur graphique : " + e.getMessage());
        }

        Log.println("\n✓ Simulation complète.");
    }

    private static void printBanner() {
        Log.println("╔══════════════════════════════════════════════════════════════════╗");
        Log.println("║   Simulation Multi-Cloud — Comparaison 3 Stratégies             ║");
        Log.println("║   NoRepLc | TCDRM | TCDRM-Predictive (P1+P2+P3)                ║");
        Log.println("╚══════════════════════════════════════════════════════════════════╝");
    }

    private static void logSection(String title) {
        Log.println("\n\n╔══════════════════════════════════════════════╗");
        Log.println("║  " + String.format("%-44s", title) + "║");
        Log.println("╚══════════════════════════════════════════════╝\n");
    }
}
