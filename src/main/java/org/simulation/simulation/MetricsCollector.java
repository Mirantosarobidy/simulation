package org.simulation.simulation;

import java.util.*;

/**
 * Construit un SimulationMetrics pour chaque stratégie et affiche
 * le rapport comparatif.
 */
public class MetricsCollector {

    public static SimulationMetrics buildNorep(List<CloudletMetrics> metrics, SLA sla) {
        SimulationMetrics sm = new SimulationMetrics("NoRepLc");
        List<CloudletMetrics> sorted = metrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();
        for (CloudletMetrics m : sorted) {
            sm.addCloudlet(m, sla.tsla, sla.csla);
            sm.totalInterProviderBw += m.bwCost;
        }
        sm.computeStats();
        return sm;
    }

    public static SimulationMetrics buildTCDRM(
            List<CloudletMetrics> norepMetrics,
            List<CloudletMetrics> tcdrmMetrics,
            TCDRMStrategy strategy,
            TCDRMRunner runner,
            SLA sla) {

        SimulationMetrics sm = new SimulationMetrics("TCDRM");
        SimulationMetrics norepSm = buildNorep(norepMetrics, sla);

        List<CloudletMetrics> norepSorted  = norepMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();
        List<CloudletMetrics> tcdrmSorted  = tcdrmMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();

        for (CloudletMetrics m : tcdrmSorted) sm.addCloudlet(m, sla.tsla, sla.csla);

        sm.totalReplicas    = strategy.getTotalReplicasCreated();
        sm.replicaHistory.addAll(strategy.getReplicaCountHistory());
        sm.firstReplicaQuery = runner.getFirstReplicationCloudletId() != null
                ? runner.getFirstReplicationCloudletId() : -1;

        // BW inter vs intra
        int n = Math.min(tcdrmSorted.size(), norepSorted.size());
        for (int i = 0; i < n; i++) {
            CloudletMetrics t = tcdrmSorted.get(i), nr = norepSorted.get(i);
            if (t.bwCost < nr.bwCost * 0.5) sm.totalIntraProviderBw += t.bwCost;
            else                             sm.totalInterProviderBw  += t.bwCost;
        }

        sm.totalBwCost  = sm.bwCosts.stream().mapToDouble(Double::doubleValue).sum();
        sm.totalCpuCost = sm.cpuCosts.stream().mapToDouble(Double::doubleValue).sum();
        sm.computeStats();
        sm.computeBreakEven(norepSm);
        return sm;
    }

    public static SimulationMetrics buildTCDRMPred(
            List<CloudletMetrics> norepMetrics,
            List<CloudletMetrics> predMetrics,
            TCDRMPredStrategy strategy,
            TCDRMPredRunner runner,
            SLA sla,
            Map<String, Integer> predMethodCounts) {

        SimulationMetrics sm     = new SimulationMetrics("TCDRM-Predictive");
        SimulationMetrics norepSm = buildNorep(norepMetrics, sla);

        List<CloudletMetrics> norepSorted = norepMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();
        List<CloudletMetrics> predSorted  = predMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();

        for (CloudletMetrics m : predSorted) {
            sm.addCloudlet(m, sla.tsla, sla.csla);
            sm.totalSyncCost += m.replicaCost;
        }

        sm.totalReplicas    = strategy.getTotalReplicasCreated();
        sm.budgetRemaining  = strategy.getBudgetRemaining();
        sm.replicaHistory.addAll(strategy.getReplicaCountHistory());
        sm.firstReplicaQuery = runner.getFirstReplicationCloudletId() != null
                ? runner.getFirstReplicationCloudletId() : -1;

        if (predMethodCounts != null) {
            sm.methodEMA        = predMethodCounts.getOrDefault("EMA", 0);
            sm.methodRegression = predMethodCounts.getOrDefault("REGRESSION", 0);
            sm.methodLSTM       = predMethodCounts.getOrDefault("LSTM", 0);
        }

        int n = Math.min(predSorted.size(), norepSorted.size());
        for (int i = 0; i < n; i++) {
            CloudletMetrics t = predSorted.get(i), nr = norepSorted.get(i);
            if (t.bwCost < nr.bwCost * 0.5) sm.totalIntraProviderBw += t.bwCost;
            else                             sm.totalInterProviderBw  += t.bwCost;
        }

        sm.totalBwCost  = sm.bwCosts.stream().mapToDouble(Double::doubleValue).sum();
        sm.totalCpuCost = sm.cpuCosts.stream().mapToDouble(Double::doubleValue).sum();
        sm.computeStats();
        sm.computeBreakEven(norepSm);
        return sm;
    }

    public static void printComparativeReport(
            SimulationMetrics norep, SimulationMetrics tcdrm,
            SimulationMetrics pred,  SLA sla) {

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              TABLEAU COMPARATIF — 3 STRATÉGIES                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.printf("  %-32s │ %-12s │ %-12s │ %-12s%n",
                "Indicateur", "NoRepLc", "TCDRM", "TCDRM-Pred");
        System.out.println("  " + "─".repeat(77));

        row("Latence moyenne (ms)",
                f2(norep.avgRespTime), f2(tcdrm.avgRespTime), f2(pred.avgRespTime));
        row("Latence p95 (ms)",
                f2(norep.p95RespTime), f2(tcdrm.p95RespTime), f2(pred.p95RespTime));
        row("Latence p99 (ms)",
                f2(norep.p99RespTime), f2(tcdrm.p99RespTime), f2(pred.p99RespTime));
        row("Violations SLA latence",
                sv(norep.slaViolationsRT), sv(tcdrm.slaViolationsRT), sv(pred.slaViolationsRT));
        row("Violations SLA coût",
                sv(norep.slaViolationsCost), sv(tcdrm.slaViolationsCost), sv(pred.slaViolationsCost));
        System.out.println("  " + "─".repeat(77));
        row("Coût BW total ($)",
                f4(norep.totalBwCost), f4(tcdrm.totalBwCost), f4(pred.totalBwCost));
        row("Coût CPU total ($)",
                f4(norep.totalCpuCost), f4(tcdrm.totalCpuCost), f4(pred.totalCpuCost));
        row("Coût total ($)",
                f4(norep.totalBwCost+norep.totalCpuCost),
                f4(tcdrm.totalBwCost+tcdrm.totalCpuCost),
                f4(pred.totalBwCost+pred.totalCpuCost));
        if (pred.totalSyncCost > 0)
            row("Coût sync ($)", "0", "0", f6(pred.totalSyncCost));
        System.out.println("  " + "─".repeat(77));
        row("Réplicas créés",        "0", sv(tcdrm.totalReplicas), sv(pred.totalReplicas));
        row("1er réplica à requête #","N/A", sv(tcdrm.firstReplicaQuery), sv(pred.firstReplicaQuery));
        row("Break-even requête #",  "N/A",
                tcdrm.breakEvenQuery<0?"jamais":sv(tcdrm.breakEvenQuery),
                pred.breakEvenQuery<0?"jamais":sv(pred.breakEvenQuery));
        row("Thrashing (cycles)",    "0", sv(tcdrm.thrashingCount), sv(pred.thrashingCount));
        System.out.println("  " + "─".repeat(77));

        double gainRtT = pct(norep.avgRespTime, tcdrm.avgRespTime);
        double gainBwT = pct(norep.totalBwCost, tcdrm.totalBwCost);
        double gainRtP = pct(norep.avgRespTime, pred.avgRespTime);
        double gainBwP = pct(norep.totalBwCost, pred.totalBwCost);
        row("Gain latence vs NoRep (%)","0", f1(gainRtT), f1(gainRtP));
        row("Gain BW vs NoRep (%)",    "0", f1(gainBwT), f1(gainBwP));

        double gainRtPvT = pct(tcdrm.avgRespTime, pred.avgRespTime);
        double gainBwPvT = pct(tcdrm.totalBwCost, pred.totalBwCost);
        System.out.printf("%n  ► TCDRM-Pred vs TCDRM : Δlatence=%.1f%% | ΔBW=%.1f%%%n",
                gainRtPvT, gainBwPvT);

        int mtotal = pred.methodEMA + pred.methodRegression + pred.methodLSTM;
        if (mtotal > 0) {
            System.out.printf("  ► Méthodes prédiction : EMA=%d%% | Reg=%d%% | LSTM=%d%%%n",
                    100*pred.methodEMA/mtotal, 100*pred.methodRegression/mtotal, 100*pred.methodLSTM/mtotal);
        }
        System.out.println();
    }

    private static void row(String label, String v1, String v2, String v3) {
        System.out.printf("  %-32s │ %-12s │ %-12s │ %-12s%n", label, v1, v2, v3);
    }
    private static String f2(double v) { return String.format("%.2f", v); }
    private static String f4(double v) { return String.format("%.4f", v); }
    private static String f6(double v) { return String.format("%.6f", v); }
    private static String f1(double v) { return String.format("%.1f", v); }
    private static String sv(int v)    { return String.valueOf(v); }
    private static double pct(double ref, double val) {
        if (ref < 1e-9) return 0;
        return 100.0 * (ref - val) / ref;
    }
}
