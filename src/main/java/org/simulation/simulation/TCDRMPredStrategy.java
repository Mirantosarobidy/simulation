package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

import static org.simulation.simulation.SimulationParameters.NB_REPEAT;

public class TCDRMPredStrategy {

    private final SLA sla;
    private final List<Provider>   providers;
    private final VmFactory.Result vmResult;

    private final Map<String, List<Replica>> replicas = new LinkedHashMap<>();
    private final List<Integer> replicaCountHistory = new ArrayList<>();
    private int totalReplicasCreated = 0;
    private double budgetRemaining;
    private double budgetInitial;

    private final Map<String, Double> writeRates  = new LinkedHashMap<>();
    private final Map<String, ConsistencyModel.DataClass> dataClasses = new LinkedHashMap<>();
    private final ParetoPlacementEngine.Policy placementPolicy;

    private static final double WINDOW_MS = 1_000.0;

    public TCDRMPredStrategy(SLA sla, List<Provider> providers, VmFactory.Result vmResult) {
        this(sla, providers, vmResult, ParetoPlacementEngine.Policy.PARETO, sla.csla * NB_REPEAT);
    }

    public TCDRMPredStrategy(SLA sla, List<Provider> providers, VmFactory.Result vmResult, ParetoPlacementEngine.Policy policy, double initialBudget) {
        this.sla = sla;
        this.providers = providers;
        this.vmResult = vmResult;
        this.placementPolicy = policy;
        this.budgetRemaining = initialBudget;
        this.budgetInitial   = initialBudget;
    }

    public List<String> checkAndReplicate(Query query, CloudletMetrics metrics) {
        List<String> toReplicate = new ArrayList<>();

        double tQ = metrics.respTime;
        double cQ = metrics.totalCost;
        double tauMs = SimulationParameters.LSTM_TAU * WINDOW_MS;

        boolean slaViolated = (tQ > sla.tsla) || (cQ > sla.csla);

        Log.println(String.format("[P1] tQ=%.2fms (TSLA=%.0f) | cQ=%.6f (CSLA=%.6f) | SLA=%s",tQ, sla.tsla, cQ, sla.csla, slaViolated ? "VIOLÉE" : "OK"));

        for (String fileName : query.getRequiredFiles())
            PopularityRegistry.getInstance().recordAccess(fileName, metrics.start * 100.0);

        if (!slaViolated) {
            Log.println("  [P1] SLA respectée");
            return toReplicate;
        }

        for (String fileName : query.getRequiredFiles()) {
            double popHat = PopularityRegistry.getInstance().predict(fileName, tauMs);
            double dynamicPsla = PopularityRegistry.getInstance().getDynamicPSLA(fileName, tauMs);
            String method = PopularityRegistry.getInstance().currentMethod(fileName).name();

            Log.println(String.format(
                    "  [P1] %s | popHat(t+%d)=%.4f | dynamicPSLA=%.4f | méthode=%s",
                    fileName, SimulationParameters.LSTM_TAU,
                    popHat, dynamicPsla, method));

            if (popHat > dynamicPsla) {
                toReplicate.add(fileName);
                Log.println(String.format("  [P1] ✓ %s → RÉPLIQUER (popHat=%.4f > dynamicPSLA=%.4f)", fileName, popHat, dynamicPsla));
            } else {
                Log.println(String.format("  [P1] — %s → skip (popHat=%.4f ≤ dynamicPSLA=%.4f)", fileName, popHat, dynamicPsla));
            }
        }
        return toReplicate;
    }

    public void placeReplicas(List<String> toReplicate, Query query, double currentTime) {

        if (toReplicate.isEmpty()) return;

        // ── Choix de la stratégie de placement ───────────────────
        // Pour 1 fichier : placement simple.
        // Pour N>1 fichiers (requête complexe) : budget réparti équitablement via selectBatchPlacements.

        if (toReplicate.size() > 1) {
            // Requête complexe : budget partagé entre les N relations
            Log.println(String.format("  [P2] Requête complexe : %d fichiers — budget total=$%.5f réparti en %d tranches",
                    toReplicate.size(), budgetRemaining, toReplicate.size()));

            List<ParetoPlacementEngine.PlacementResult> batch =
                    ParetoPlacementEngine.selectBatchPlacements(
                            toReplicate, query, providers, vmResult,
                            sla, budgetRemaining, placementPolicy,
                            writeRates, dataClasses);

            for (int i = 0; i < toReplicate.size(); i++) {
                String fileName = toReplicate.get(i);
                ParetoPlacementEngine.PlacementResult best = batch.get(i);
                if (best == null) continue;

                ExtendedVm sourceVm = vmResult.relationVmMap.get(fileName);
                if (sourceVm == null) continue;

                if (sourceVm.getRegion().equals(best.region())) {
                    Log.println("    Donnée déjà dans région cible — skip");
                    continue;
                }
                if (hasActiveReplicaInRegion(fileName, best.region())) {
                    Log.println("    Réplique déjà active dans " + best.region() + " — skip");
                    continue;
                }

                Replica r = new Replica(fileName, sourceVm.getProvider(), sourceVm.getRegion(),
                        best.provider(), best.region(), best.dcName());
                r.setCreationTime(currentTime);
                replicas.computeIfAbsent(fileName, k -> new ArrayList<>()).add(r);
                totalReplicasCreated++;
                budgetRemaining -= (best.cost() + best.syncCost());

                Log.println(String.format(
                        "  [P2-Batch] ✓ Π=(%s, %s, %s) | U=%.4f | cohérence=%s | cost=%.5f | sync=%.6f | budget restant=$%.5f",
                        fileName, best.provider(), best.region(), best.utilityScore(),
                        best.consistencyMode(), best.cost(), best.syncCost(), budgetRemaining));
            }

        } else {
            // Requête simple : placement individual comme avant
            String fileName = toReplicate.get(0);
            ExtendedVm sourceVm = vmResult.relationVmMap.get(fileName);
            if (sourceVm == null) return;

            String sourceRegion = sourceVm.getRegion();
            String targetRegion = query.getRegion().equals("RANDOM") ? sourceRegion : query.getRegion();

            Log.println(String.format("\n  [P3→P2] %s | source=%s/%s | région cible=%s | budget=$%.5f",
                    fileName, sourceVm.getProvider(), sourceRegion, targetRegion, budgetRemaining));

            if (sourceRegion.equals(targetRegion)) {
                Log.println("    Donnée déjà dans région cible");
                return;
            }
            if (hasActiveReplicaForFile(fileName)) {
                Log.println("    Réplique déjà active pour " + fileName + " (toutes régions confondues)");
                return;
            }

            double writeRate = writeRates.getOrDefault(fileName, 0.02);
            ConsistencyModel.DataClass dataClass = dataClasses.getOrDefault(
                    fileName, ConsistencyModel.inferClass(fileName, writeRate));

            double latInterCloud = getInterCloudLatency(sourceRegion, targetRegion);
            ConsistencyModel.SyncResult syncResult = ConsistencyModel.compute(
                    dataClass, writeRate, latInterCloud, sourceVm.getRelationSizeMB());

            Log.println(String.format("  [P3] %s | classe=%s | writeRate=%.3f | latInterCloud=%.0fms | mode=%s | syncCost=%.6f",
                    fileName, dataClass, writeRate, latInterCloud, syncResult.mode(), syncResult.syncCost()));

            double tauMs = SimulationParameters.LSTM_TAU * 1_000.0;
            double _popHat      = PopularityRegistry.getInstance().predict(fileName, tauMs);
            double _dynPSLA     = PopularityRegistry.getInstance().getDynamicPSLA(fileName, tauMs);

            ParetoPlacementEngine.PlacementResult best = ParetoPlacementEngine.selectBestPlacement(
                    fileName, query, providers, vmResult,
                    sla, budgetRemaining, placementPolicy, writeRate, dataClass,
                    _popHat, _dynPSLA, budgetInitial);

            if (best == null) { Log.println("    [P2] Aucun placement Pareto valide"); return; }
            if (hasActiveReplicaInRegion(fileName, best.region())) {
                Log.println("    [P2] Réplique déjà active dans " + best.region() + " (doublon P2)");
                return;
            }

            Replica r = new Replica(fileName, sourceVm.getProvider(), sourceVm.getRegion(),
                    best.provider(), best.region(), best.dcName());
            r.setCreationTime(currentTime);
            replicas.computeIfAbsent(fileName, k -> new ArrayList<>()).add(r);
            totalReplicasCreated++;
            budgetRemaining -= (best.cost() + best.syncCost());

            Log.println(String.format(
                    "  [P2] ✓ Π=(%s, %s, %s) | U=%.4f | cohérence=%s | cost=%.5f | rt=%.1fms | sync=%.6f | budget restant=$%.5f",
                    fileName, best.provider(), best.region(), best.utilityScore(),
                    best.consistencyMode(), best.cost(), best.rt(), best.syncCost(), budgetRemaining));
        }
    }

        public int deleteUnpopularReplicas(double currentTime, int deltaT) {
        int deleted = 0;
        double tauMs = SimulationParameters.LSTM_TAU * WINDOW_MS;

        for (Map.Entry<String, List<Replica>> e : replicas.entrySet()) {
            String        fileName    = e.getKey();
            List<Replica> replicaList = e.getValue();

            boolean historyLow = PopularityRegistry.getInstance()
                    .shouldDelete(fileName, tauMs, deltaT);
            if (!historyLow) continue;

            double popHat = PopularityRegistry.getInstance().predict(fileName, tauMs);
            double dynamicPsla = PopularityRegistry.getInstance().getDynamicPSLA(fileName, tauMs);
            boolean futureAlsoLow = (popHat <= dynamicPsla * 0.5);

            for (Replica r : replicaList) {
                if (!r.isActive()) continue;
                if (futureAlsoLow) {
                    r.setActive(false);
                    deleted++;
                    double recovered = estimateReplicaCost(fileName, r.getTargetProvider(), r.getTargetRegion());
                    budgetRemaining += recovered;
                    Log.println(String.format("  [P4] Supprimé %s | popHat=%.4f ≤ seuil=%.4f | +$%.5f récupéré", r, popHat, dynamicPsla * 0.5, recovered));
                } else {
                    Log.println(String.format("  [P4] Conservé %s | popHat=%.4f > seuil=%.4f", r, popHat, dynamicPsla * 0.5));
                }
            }
        }
        return deleted;
    }

    public void setWriteRate(String fileName, double rate) { writeRates.put(fileName, rate);}
    public void setDataClass(String fileName, ConsistencyModel.DataClass cls) { dataClasses.put(fileName, cls); }

    public boolean hasActiveReplicaInRegion(String fileName, String region) {
        List<Replica> list = replicas.get(fileName);
        if (list == null) return false;
        return list.stream().anyMatch(r -> r.isActive() && r.getTargetRegion().equals(region));
    }

    public boolean hasActiveReplicaForFile(String fileName) {
        List<Replica> list = replicas.get(fileName);
        if (list == null) return false;
        return list.stream().anyMatch(Replica::isActive);
    }

    public int getTotalReplicasCreated() { return totalReplicasCreated; }
    public double getBudgetRemaining() { return budgetRemaining; }
    public double getBudgetInitial()   { return budgetInitial;   }

    public int getActiveReplicaCount() {
        return replicas.values().stream()
                .mapToInt(l -> (int) l.stream().filter(Replica::isActive).count())
                .sum();
    }

    public Map<String, List<Replica>> getReplicas() { return replicas; }
    public void recordReplicaCount() { replicaCountHistory.add(getActiveReplicaCount()); }
    public List<Integer> getReplicaCountHistory() { return replicaCountHistory; }

    private double estimateReplicaCost(String fileName, String providerName, String region) {
        ExtendedVm vm = vmResult.relationVmMap.get(fileName);
        if (vm == null) return 0.0;
        Provider p = providers.stream()
                .filter(pr -> pr.getName().equals(providerName))
                .findFirst().orElse(null);
        if (p == null) return 0.0;
        double cpuSec  = vm.getRelationSizeMB() / p.getMips(region);
        double cpuCost = (p.getCpuCostPerMI(region) / 3600.0) * cpuSec;
        double bwCost  = (vm.getRelationSizeMB() / 1024.0) * p.getBwIntraDcCost(region);
        return cpuCost + bwCost;
    }

    private static double getInterCloudLatency(String regionA, String regionB) {
        if (regionA.equals(regionB)) return SimulationParameters.LAT_INTRA_REGION_MS;
        if (Set.of("UE","US").containsAll(Set.of(regionA,regionB))) return SimulationParameters.LAT_UE_US_MS;
        if (Set.of("US","AS").containsAll(Set.of(regionA,regionB))) return SimulationParameters.LAT_US_AS_MS;
        return SimulationParameters.LAT_UE_AS_MS;
    }
}