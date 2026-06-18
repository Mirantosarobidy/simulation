package org.simulation.simulation;

import java.util.*;

/**
 * Collecteur centralisé des métriques pour les 3 stratégies.
 * Toutes les données nécessaires aux graphiques et logs sont ici.
 */
public class SimulationMetrics {

    public final String strategyName;

    // ── Métriques par cloudlet (dans l'ordre cloudletId) ─────
    public final List<Double> respTimes     = new ArrayList<>();
    public final List<Double> bwCosts       = new ArrayList<>();
    public final List<Double> totalCosts    = new ArrayList<>();
    public final List<Double> cpuCosts      = new ArrayList<>();
    public final List<Double> cumBwCosts    = new ArrayList<>();
    public final List<Double> cumTotalCosts = new ArrayList<>();

    // ── Facteur de réplication ────────────────────────────────
    public final List<Integer> replicaHistory = new ArrayList<>();  // nb réplicas actifs par période

    // ── Statistiques globales ─────────────────────────────────
    public int    totalReplicas      = 0;
    public int    deletedReplicas    = 0;
    public int    slaViolationsRT    = 0;   // tQ > TSLA
    public int    slaViolationsCost  = 0;   // cQ > CSLA
    public double avgRespTime        = 0;
    public double p95RespTime        = 0;
    public double p99RespTime        = 0;
    public double totalBwCost        = 0;
    public double totalCpuCost       = 0;
    public double totalSyncCost      = 0;   // TCDRM-Pred uniquement
    public int    breakEvenQuery     = -1;  // requête où gain net commence vs NoRep
    public int    firstReplicaQuery  = -1;  // requête qui déclenche le 1er réplica
    public int    thrashingCount     = 0;   // cycles create→delete→create
    public double budgetRemaining    = 0;   // TCDRM-Pred uniquement

    // ── Bande passante ────────────────────────────────────────
    public double totalInterProviderBw  = 0;  // volume inter-provider (GB)
    public double totalIntraProviderBw  = 0;  // volume intra-provider  (GB)

    // ── Méthodes de prédiction (TCDRM-Pred) ──────────────────
    public int methodEMA        = 0;
    public int methodRegression = 0;
    public int methodLSTM       = 0;

    public SimulationMetrics(String strategyName) {
        this.strategyName = strategyName;
    }

    /** Accumule les données de chaque cloudlet et calcule les cumulatifs. */
    public void addCloudlet(CloudletMetrics m, double tsla, double csla) {
        respTimes.add(m.respTime);
        bwCosts.add(m.bwCost);
        totalCosts.add(m.totalCost);
        cpuCosts.add(m.cpuCost);

        double prevBw    = cumBwCosts.isEmpty()    ? 0 : cumBwCosts.getLast();
        double prevTotal = cumTotalCosts.isEmpty()  ? 0 : cumTotalCosts.getLast();
        cumBwCosts.add(prevBw + m.bwCost);
        cumTotalCosts.add(prevTotal + m.totalCost);

        if (m.respTime  > tsla) slaViolationsRT++;
        if (m.totalCost > csla) slaViolationsCost++;
    }

    /** Calcule toutes les statistiques agrégées une fois les données remplies. */
    public void computeStats() {
        int n = respTimes.size();
        if (n == 0) return;

        avgRespTime   = respTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        totalBwCost   = bwCosts.stream().mapToDouble(Double::doubleValue).sum();
        totalCpuCost  = cpuCosts.stream().mapToDouble(Double::doubleValue).sum();

        List<Double> sorted = respTimes.stream().sorted().toList();
        p95RespTime = sorted.get((int) Math.min(n - 1, Math.ceil(0.95 * n) - 1));
        p99RespTime = sorted.get((int) Math.min(n - 1, Math.ceil(0.99 * n) - 1));
    }

    /** Calcul du break-even par rapport à une stratégie de référence. */
    public void computeBreakEven(SimulationMetrics reference) {
        for (int i = 0; i < cumTotalCosts.size() && i < reference.cumTotalCosts.size(); i++) {
            if (cumTotalCosts.get(i) < reference.cumTotalCosts.get(i)) {
                breakEvenQuery = i + 1;
                return;
            }
        }
    }

    /** Log détaillé dans la console. */
    public void printReport(double tsla, double csla) {
        String sep = "─".repeat(60);
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.printf( "║  RAPPORT : %-46s║%n", strategyName);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println(sep);

        System.out.printf("  Requêtes traitées       : %d%n", respTimes.size());
        System.out.printf("  Latence moyenne          : %.2f ms  (TSLA=%.0f ms)%n", avgRespTime, tsla);
        System.out.printf("  Latence p95              : %.2f ms%n", p95RespTime);
        System.out.printf("  Latence p99              : %.2f ms%n", p99RespTime);
        System.out.printf("  Violations SLA latence   : %d  (%.1f%%)%n",
                slaViolationsRT, 100.0 * slaViolationsRT / Math.max(1, respTimes.size()));
        System.out.printf("  Violations SLA coût      : %d  (%.1f%%)%n",
                slaViolationsCost, 100.0 * slaViolationsCost / Math.max(1, respTimes.size()));
        System.out.println(sep);
        System.out.printf("  Coût BW total            : $%.4f%n", totalBwCost);
        System.out.printf("  Coût CPU total           : $%.4f%n", totalCpuCost);
        System.out.printf("  Coût total               : $%.4f%n", totalBwCost + totalCpuCost);
        if (totalSyncCost > 0)
            System.out.printf("  Coût synchronisation     : $%.6f%n", totalSyncCost);
        System.out.println(sep);
        System.out.printf("  Réplicas créés           : %d%n", totalReplicas);
        System.out.printf("  Réplicas supprimés       : %d%n", deletedReplicas);
        System.out.printf("  1er réplica à requête    : #%d%n", firstReplicaQuery);
        System.out.printf("  Gain net à partir de     : #%d%n", breakEvenQuery < 0 ? 0 : breakEvenQuery);
        System.out.printf("  Cycles thrashing         : %d%n", thrashingCount);
        if (budgetRemaining > 0)
            System.out.printf("  Budget restant           : $%.4f%n", budgetRemaining);
        if (methodEMA + methodRegression + methodLSTM > 0) {
            int total = methodEMA + methodRegression + methodLSTM;
            System.out.printf("  Méthode EMA              : %d (%.0f%%)%n", methodEMA,      100.0*methodEMA/total);
            System.out.printf("  Méthode Régression       : %d (%.0f%%)%n", methodRegression,100.0*methodRegression/total);
            System.out.printf("  Méthode LSTM             : %d (%.0f%%)%n", methodLSTM,     100.0*methodLSTM/total);
        }
        System.out.println(sep);
    }
}
