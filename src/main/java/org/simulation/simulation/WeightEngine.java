package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;
import java.util.Set;

/**
 * WeightEngine — calcule les poids ω = [ω_cost, ω_RT, ω_BW, ω_sync]
 * de façon entièrement dynamique, en combinant quatre signaux observés
 * sur la relation et son contexte d'exécution.
 *
 * Formule :
 *   ω_base  = poids région (UE / AS / US)
 *   ω_raw   = ω_base + Δ(latRatio) + Δ(writeRate) + Δ(budgetPressure) + Δ(popRatio)
 *   ω_clamp = max(MIN_WEIGHT, ω_raw[i])      ← plancher 5 %
 *   ω_final = ω_clamp / Σ ω_clamp            ← normalisation L1 → Σ = 1
 *
 * Les quatre signaux :
 *  1. latRatio      : latence source→cible / LAT_MAX
 *                     → loin = ω_RT monte, ω_cost descend légèrement
 *  2. writeRate     : taux d'écriture de la relation ∈ [0,1]
 *                     → écriture intensive = ω_sync monte, ω_cost descend
 *  3. budgetPressure: 1 − remaining/initial (quadratique)
 *                     → budget serré = ω_cost monte fortement
 *  4. popRatio      : popHat / dynamicPSLA (plafonné à 1)
 *                     → donnée très populaire = ω_BW monte
 */
public class WeightEngine {

    private static final double[] BASE_US = { 0.35, 0.35, 0.15, 0.15 };
    private static final double[] BASE_UE = { 0.30, 0.40, 0.15, 0.15 };
    private static final double[] BASE_AS = { 0.20, 0.50, 0.15, 0.15 };

    private static final double LAT_MAX    = SimulationParameters.LAT_UE_AS_MS; // 75 ms
    private static final double MIN_WEIGHT = 0.05;

    private static final int I_COST = 0, I_RT = 1, I_BW = 2, I_SYNC = 3;

    /**
     * Calcule ω dynamiquement.
     *
     * @param queryRegion     région d'origine de la requête
     * @param sourceRegion    région où la donnée est stockée
     * @param targetRegion    région du provider candidat
     * @param sourceProvider  provider source
     * @param targetProvider  provider candidat
     * @param writeRate       taux d'écriture observé ∈ [0,1]
     * @param popHat          popularité prédite (P1)
     * @param dynamicPSLA     seuil dynamique de P1
     * @param budgetRemaining budget restant
     * @param budgetInitial   budget initial (pour ratio)
     * @return ω normalisé [ω_cost, ω_RT, ω_BW, ω_sync], Σ = 1
     */
    public static double[] compute(
            String queryRegion,
            String sourceRegion,
            String targetRegion,
            String sourceProvider,
            String targetProvider,
            double writeRate,
            double popHat,
            double dynamicPSLA,
            double budgetRemaining,
            double budgetInitial) {

        double[] w = baseWeights(queryRegion);

        // Signal 1 — latence réseau
        double lat      = networkLatency(sourceRegion, targetRegion, sourceProvider, targetProvider);
        double latRatio = Math.min(1.0, lat / LAT_MAX);
        w[I_RT]   += 0.30 * latRatio;
        w[I_COST] -= 0.10 * latRatio;

        // Signal 2 — taux d'écriture
        double wr = Math.min(1.0, Math.max(0.0, writeRate));
        w[I_SYNC] += 0.25 * wr;
        w[I_COST] -= 0.15 * wr;
        w[I_BW]   -= 0.05 * wr;

        // Signal 3 — pression budgétaire (quadratique)
        double budgetUsed = (budgetInitial > 1e-9)
                ? Math.min(1.0, 1.0 - budgetRemaining / budgetInitial)
                : 0.0;
        w[I_COST] += 0.35 * budgetUsed * budgetUsed;
        w[I_BW]   -= 0.10 * budgetUsed;

        // Signal 4 — popularité prédite
        double popRatio = (dynamicPSLA > 1e-9)
                ? Math.min(1.0, popHat / (dynamicPSLA + 1e-9))
                : 0.0;
        w[I_BW]   += 0.20 * popRatio;
        w[I_RT]   += 0.05 * popRatio;

        // Plancher + normalisation L1
        double sum = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(MIN_WEIGHT, w[i]);
            sum += w[i];
        }
        for (int i = 0; i < w.length; i++) w[i] /= sum;

        Log.println(String.format(
            "  [WeightEngine] %s→%s | wr=%.2f | latRatio=%.2f | budgetUsed=%.2f | popRatio=%.2f"
            + " → ω=[cost=%.2f rt=%.2f bw=%.2f sync=%.2f]",
            sourceRegion, targetRegion, wr, latRatio, budgetUsed, popRatio,
            w[I_COST], w[I_RT], w[I_BW], w[I_SYNC]));

        return w;
    }

    private static double[] baseWeights(String queryRegion) {
        return switch (queryRegion) {
            case "AS"  -> BASE_AS.clone();
            case "UE"  -> BASE_UE.clone();
            default    -> BASE_US.clone();
        };
    }

    private static double networkLatency(String rA, String rB, String pA, String pB) {
        if (!pA.equals(pB))                                 return SimulationParameters.LAT_INTER_PROVIDER_MS;
        if (rA.equals(rB))                                  return SimulationParameters.LAT_INTRA_REGION_MS;
        if (Set.of("UE","US").containsAll(Set.of(rA,rB)))  return SimulationParameters.LAT_UE_US_MS;
        if (Set.of("US","AS").containsAll(Set.of(rA,rB)))  return SimulationParameters.LAT_US_AS_MS;
        return SimulationParameters.LAT_UE_AS_MS;
    }
}
