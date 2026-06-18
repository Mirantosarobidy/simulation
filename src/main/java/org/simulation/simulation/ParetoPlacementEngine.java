package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

public class ParetoPlacementEngine {

    public enum Policy { PARETO, WEIGHTED }
    private static final double W_COST = 0.35;
    private static final double W_RT   = 0.35;
    private static final double W_BW   = 0.15;
    private static final double W_SYNC = 0.15;

    public record PlacementResult(
            String fileName,
            String provider,
            String region,
            String dcName,
            double cost,
            double rt,
            double bw,
            double syncCost,
            double utilityScore,
            ConsistencyModel.ConsistencyMode consistencyMode
    ) {}

    private record Candidate(
            String   provider,
            String   region,
            double   cost,
            double   rt,
            double   bw,
            double   syncCost,
            double[] omega     // poids dynamiques propres à ce candidat (WeightEngine)
    ) {}

    // ═══════════════════════════════════════════════════════════
    //  Point d'entrée principal
    // ═══════════════════════════════════════════════════════════

    /**
     *
     * @param fileName         relation à répliquer
     * @param query            query déclenchante (fournit la région cible prioritaire)
     * @param providers        liste des providers disponibles
     * @param vmResult         mapping relation → VM source
     * @param sla              contraintes SLA
     * @param budgetRemaining  budget monétaire restant
     * @param policy           Pareto ou Weighted
     * @param writeRate        taux d'écriture observé sur la relation
     * @param dataClass        classe de cohérence (peut être null → inférée)
     * @return PlacementResult optimal, ou null si aucun candidat valide
     */
    public static PlacementResult selectBestPlacement(
            String fileName,
            Query query,
            List<Provider> providers,
            VmFactory.Result vmResult,
            SLA sla,
            double budgetRemaining,
            Policy policy,
            double writeRate,
            ConsistencyModel.DataClass dataClass) {
        return selectBestPlacement(fileName, query, providers, vmResult, sla,
                budgetRemaining, policy, writeRate, dataClass, 0.0, 1.0, budgetRemaining);
    }

    public static PlacementResult selectBestPlacement(
            String fileName,
            Query query,
            List<Provider> providers,
            VmFactory.Result vmResult,
            SLA sla,
            double budgetRemaining,
            Policy policy,
            double writeRate,
            ConsistencyModel.DataClass dataClass,
            double popHat,
            double dynamicPSLA,
            double budgetInitial) {

        ExtendedVm sourceVm = vmResult.relationVmMap.get(fileName);
        if (sourceVm == null) return null;

        if (dataClass == null)
            dataClass = ConsistencyModel.inferClass(fileName, writeRate);

        List<Candidate> candidates = buildCandidates(fileName, query, providers, vmResult, sla,
                budgetRemaining, writeRate, dataClass, popHat, dynamicPSLA, budgetInitial);

        if (candidates.isEmpty()) {
            Log.println("  [P2] Aucun candidat valide pour " + fileName + " (budget=" + String.format("%.5f", budgetRemaining) + ")");
            return null;
        }

        Log.println("  [P2] " + candidates.size() + " candidats pour " + fileName);

        List<Candidate> paretoFront = computeParetoFront(candidates);
        Log.println("  [P2] Front Pareto : " + paretoFront.size() + " solutions");

        // Chaque candidat porte son propre omega (calcule dans buildCandidates).
        // null est passe comme fallback weights - computeUtility utilisera c.omega()
        Candidate best = selectFinal(paretoFront, policy, null);
        if (best == null) return null;
        double utility = computeUtility(best, paretoFront, best.omega());

        ConsistencyModel.SyncResult syncR = ConsistencyModel.compute(dataClass, writeRate, getInterCloudLatency(sourceVm.getRegion(), best.region()), sourceVm.getRelationSizeMB());

        Log.println(String.format("  [P2] ✓ Sélection : %s/%s | cost=%.5f | rt=%.1fms | bw=%.1f | sync=%.5f | U=%.4f | mode=%s",
                best.provider(), best.region(), best.cost(), best.rt(), best.bw(), best.syncCost(), utility, syncR.mode()));

        return new PlacementResult(fileName,
                best.provider(), best.region(),
                best.provider() + "_" + best.region() + "_DC0",
                best.cost(), best.rt(), best.bw(), best.syncCost(),
                utility, syncR.mode());
    }

    private static List<Candidate> buildCandidates(
            String fileName,
            Query query,
            List<Provider> providers,
            VmFactory.Result vmResult,
            SLA sla,
            double budgetRemaining,
            double writeRate,
            ConsistencyModel.DataClass dataClass,
            double popHat,
            double dynamicPSLA,
            double budgetInitial) {

        ExtendedVm sourceVm = vmResult.relationVmMap.get(fileName);
        List<Candidate> result = new ArrayList<>();

        for (Provider p : providers) {
            for (String region : SimulationParameters.REGIONS) {

                if (p.getName().equals(sourceVm.getProvider())
                        && region.equals(sourceVm.getRegion())) continue;

                double cpuSec  = sourceVm.getRelationSizeMB() / p.getMips(region);
                double cpuCost = (p.getCpuCostPerMI(region) / 3600.0) * cpuSec;
                double bwCostE = (sourceVm.getRelationSizeMB() / 1024.0) * p.getBwIntraDcCost(region);
                double cost    = cpuCost + bwCostE;

                if (cost > budgetRemaining) continue;

                double cpuMs = (sourceVm.getRelationSizeMB() / p.getMips(region)) * 1000.0;
                double latencyMs = getNetworkLatency(query.getRegion(), region, getProviderForQuery(query, vmResult), p.getName());
                double rt = cpuMs + latencyMs;

                double bw = p.getBw(region);

                double latInterCloud = getInterCloudLatency(sourceVm.getRegion(), region);
                ConsistencyModel.SyncResult syncR = ConsistencyModel.compute(dataClass, writeRate, latInterCloud, sourceVm.getRelationSizeMB());
                double syncCost = syncR.syncCost();

                double[] omega = WeightEngine.compute(
                        query.getRegion(),
                        sourceVm.getRegion(), region,
                        sourceVm.getProvider(), p.getName(),
                        writeRate, popHat, dynamicPSLA,
                        budgetRemaining, budgetInitial);
                result.add(new Candidate(p.getName(), region, cost, rt, bw, syncCost, omega));

                Log.println(String.format("    [P2-Cand] %s/%s : cost=%.5f | rt=%.1fms | bw=%.0f | sync=%.5f | w=[%.2f,%.2f,%.2f,%.2f]", p.getName(), region, cost, rt, bw, syncCost, omega[0], omega[1], omega[2], omega[3]));
            }
        }
        return result;
    }

    private static List<Candidate> computeParetoFront(List<Candidate> candidates) {
        List<Candidate> front = new ArrayList<>();

        for (Candidate a : candidates) {
            boolean dominated = false;
            for (Candidate b : candidates) {
                if (b == a) continue;
                if (dominates(b, a)) { dominated = true; break; }
            }
            if (!dominated) front.add(a);
        }

        return front.isEmpty() ? candidates : front;
    }

    private static boolean dominates(Candidate a, Candidate b) {
        boolean strictlyBetter = false;

        // cost : minimiser
        if (a.cost() > b.cost())   return false;
        if (a.cost() < b.cost())   strictlyBetter = true;

        // rt : minimiser
        if (a.rt() > b.rt())       return false;
        if (a.rt() < b.rt())       strictlyBetter = true;

        // bw : maximiser (on compare -bw)
        if (a.bw() < b.bw())       return false;
        if (a.bw() > b.bw())       strictlyBetter = true;

        // syncCost : minimiser
        if (a.syncCost() > b.syncCost()) return false;
        if (a.syncCost() < b.syncCost()) strictlyBetter = true;

        return strictlyBetter;
    }

    private static Candidate selectFinal(List<Candidate> front, Policy policy, double[] weights) {
        if (front.isEmpty()) return null;
        if (front.size() == 1) return front.get(0);

        return switch (policy) {
            case PARETO   -> selectFromParetoByUtility(front, weights);
            case WEIGHTED -> selectByWeightedScore(front, weights);
        };
    }

    private static Candidate selectFromParetoByUtility(List<Candidate> front, double[] weights) {
        return front.stream()
                .min(Comparator.comparingDouble(c -> computeUtility(c, front, weights)))
                .orElse(front.get(0));
    }

    private static Candidate selectByWeightedScore(List<Candidate> candidates, double[] weights) {
        return candidates.stream()
                .min(Comparator.comparingDouble(c -> computeUtility(c, candidates, weights)))
                .orElse(candidates.getFirst());
    }

    static double computeUtility(Candidate c, List<Candidate> ref, double[] weights) {
        double minCost = ref.stream().mapToDouble(Candidate::cost).min().orElse(0);
        double maxCost = ref.stream().mapToDouble(Candidate::cost).max().orElse(1);
        double minRt   = ref.stream().mapToDouble(Candidate::rt).min().orElse(0);
        double maxRt   = ref.stream().mapToDouble(Candidate::rt).max().orElse(1);
        double minBw   = ref.stream().mapToDouble(Candidate::bw).min().orElse(0);
        double maxBw   = ref.stream().mapToDouble(Candidate::bw).max().orElse(1);
        double minSync = ref.stream().mapToDouble(Candidate::syncCost).min().orElse(0);
        double maxSync = ref.stream().mapToDouble(Candidate::syncCost).max().orElse(1);

        double nCost = normalize(c.cost(), minCost, maxCost);
        double nRt = normalize(c.rt(), minRt, maxRt);
        double nBw = 1.0 - normalize(c.bw(), minBw,  maxBw);
        double nSync = normalize(c.syncCost(), minSync, maxSync);

        // Poids propres au candidat (Option B pseudocode P2)
        // fallback sur weights si omega absent (appels legacy)
        double[] w = (c.omega() != null && c.omega().length == 4) ? c.omega() : weights;
        if (w == null) w = new double[]{0.35, 0.35, 0.15, 0.15};
        return w[0]*nCost + w[1]*nRt + w[2]*nBw + w[3]*nSync;
    }

    private static double normalize(double v, double min, double max) {
        if (Math.abs(max - min) < 1e-12) return 0.0;
        return (v - min) / (max - min);
    }

    private static double[] getDynamicWeights(Query query) {
        return switch (query.getRegion()) {
            case "AS" -> new double[]{ 0.20, 0.50, 0.15, 0.15 };
            case "UE" -> new double[]{ 0.30, 0.40, 0.15, 0.15 };
            default   -> new double[]{ W_COST, W_RT, W_BW, W_SYNC };
        };
    }

    private static double getNetworkLatency(String rA, String rB, String pA, String pB) {
        if (!pA.equals(pB)) return SimulationParameters.LAT_INTER_PROVIDER_MS;
        if (rA.equals(rB))  return SimulationParameters.LAT_INTRA_REGION_MS;
        if (Set.of("UE","US").containsAll(Set.of(rA,rB))) return SimulationParameters.LAT_UE_US_MS;
        if (Set.of("US","AS").containsAll(Set.of(rA,rB))) return SimulationParameters.LAT_US_AS_MS;
        return SimulationParameters.LAT_UE_AS_MS;
    }

    private static double getInterCloudLatency(String regionA, String regionB) {
        if (regionA.equals(regionB)) return SimulationParameters.LAT_INTRA_REGION_MS;
        if (Set.of("UE","US").containsAll(Set.of(regionA,regionB))) return SimulationParameters.LAT_UE_US_MS;
        if (Set.of("US","AS").containsAll(Set.of(regionA,regionB))) return SimulationParameters.LAT_US_AS_MS;
        return SimulationParameters.LAT_UE_AS_MS;
    }

    private static String getProviderForQuery(Query query, VmFactory.Result vmResult) {
        ExtendedVm vm = vmResult.relationVmMap.get(query.getRequiredFiles().get(0));
        return vm != null ? vm.getProvider() : "AWS";
    }
    /**
     * Placement multi-fichiers avec budget équitablement réparti.
     * Appelé quand plusieurs relations doivent être répliquées en même temps
     * (requête complexe). Le budget total est divisé en N tranches égales —
     * une par fichier — pour éviter que les premiers fichiers consomment tout
     * le budget au détriment des suivants.
     *
     * Si un fichier utilise moins que sa tranche, le reliquat est redistribué
     * aux fichiers suivants.
     *
     * @param fileNames        liste ordonnée des fichiers à répliquer
     * @param query            requête déclenchante
     * @param providers        liste des providers disponibles
     * @param vmResult         mapping relation→VM
     * @param sla              contraintes SLA
     * @param totalBudget      budget total disponible
     * @param policy           politique Pareto ou Weighted
     * @param writeRates       taux d'écriture par fichier (peut être null → 0.02)
     * @param dataClasses      classe de cohérence par fichier (peut être null → inférée)
     * @return liste ordonnée de PlacementResult (null si fichier non placé)
     */
    public static List<PlacementResult> selectBatchPlacements(
            List<String> fileNames,
            Query query,
            List<Provider> providers,
            VmFactory.Result vmResult,
            SLA sla,
            double totalBudget,
            Policy policy,
            Map<String, Double> writeRates,
            Map<String, ConsistencyModel.DataClass> dataClasses) {

        int n = fileNames.size();
        if (n == 0) return Collections.emptyList();

        List<PlacementResult> results = new ArrayList<>();
        double remainingPool  = totalBudget;

        for (int i = 0; i < n; i++) {
            String fileName = fileNames.get(i);

            // Budget alloué = tranche fixe + reliquat non consommé des fichiers précédents
            int remaining = n - i;
            double budget = remainingPool / remaining;

            double wr = writeRates  != null ? writeRates.getOrDefault(fileName, 0.02)  : 0.02;
            ConsistencyModel.DataClass dc = (dataClasses != null)
                    ? dataClasses.getOrDefault(fileName, ConsistencyModel.inferClass(fileName, wr))
                    : ConsistencyModel.inferClass(fileName, wr);

            double bPop  = PopularityRegistry.getInstance().predict(fileName, SimulationParameters.LSTM_TAU * 1000.0);
            double bPSLA = PopularityRegistry.getInstance().getDynamicPSLA(fileName, SimulationParameters.LSTM_TAU * 1000.0);
            PlacementResult pr = selectBestPlacement(
                    fileName, query, providers, vmResult, sla, budget, policy, wr, dc,
                    bPop, bPSLA, totalBudget);

            results.add(pr);

            if (pr != null) {
                double consumed = pr.cost() + pr.syncCost();
                remainingPool -= consumed;
                Log.println(String.format(
                        "  [P2-Batch] %s → %s/%s | budget_tranche=%.5f | consommé=%.5f | pool_restant=%.5f",
                        fileName, pr.provider(), pr.region(),
                        budget, consumed, remainingPool));
            } else {
                Log.println(String.format(
                        "  [P2-Batch] %s → non placé (budget_tranche=%.5f insuffisant)",
                        fileName, budget));
            }
        }
        return results;
    }


}