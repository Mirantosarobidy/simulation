package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

public class TCDRMStrategy {

    private final SLA sla;
    private final List<Provider> providers;
    private final VmFactory.Result vmResult;
    private final PopularityTracker popularityTracker;

    private final Map<String, List<Replica>> replicas = new LinkedHashMap<>();
    private final List<Integer> replicaCountHistory   = new ArrayList<>();
    private int totalReplicasCreated = 0;

    public TCDRMStrategy(SLA sla, List<Provider> providers, VmFactory.Result vmResult) {
        this.sla = sla;
        this.providers = providers;
        this.vmResult = vmResult;
        this.popularityTracker = new PopularityTracker();
    }


    public List<String> checkAndReplicate(Query query, CloudletMetrics metrics) {
        List<String> toReplicate = new ArrayList<>();

        for (String fileName : query.getRequiredFiles()) {
            popularityTracker.registerAccess(fileName);
        }

        double tQ = metrics.respTime;   // ms
        double cQ = metrics.totalCost;  // $

        boolean slaViolated = (tQ > sla.tsla) || (cQ > sla.csla);

        Log.println(String.format("[TCDRM] tQ=%.2fms (TSLA=%.0f) | cQ=%.6f (CSLA=%.6f) | SLA=%s",
                tQ, sla.tsla, cQ, sla.csla, slaViolated ? "VIOLÉE" : "OK"));

        if (!slaViolated) return toReplicate;

        int totalQ = popularityTracker.getQueryCounter();

        for (String fileName : query.getRequiredFiles()) {
            int count = popularityTracker.getAccessCount(fileName);
            double pdi = popularityTracker.getPopularity(fileName);

            boolean popularEnough = count > (int) sla.psla;

            Log.println(String.format("  [TCDRM] %s : count=%d (PSLA=%.0f) | pdi=%.4f | tQ=%.1fms | %s",
                    fileName, count, sla.psla, pdi, tQ,
                    popularEnough ? "→ RÉPLIQUER" : "→ skip"));

            if (popularEnough) {
                toReplicate.add(fileName);
            }
        }
        return toReplicate;
    }

    public void placeReplicas(List<String> toReplicate, Query query, double currentTime) {

        for (String fileName : toReplicate) {
            ExtendedVm sourceVm = vmResult.relationVmMap.get(fileName);
            if (sourceVm == null) continue;

            String sourceRegion = sourceVm.getRegion();
            String targetRegion = query.getRegion();

            if (sourceRegion.equals(targetRegion)) {
                Log.println("  [Algo2] " + fileName + " déjà dans région cible → skip");
                continue;
            }

            if (hasActiveReplicaInRegion(fileName, targetRegion)) {
                Log.println("  [Algo2] Réplique active déjà présente dans " + targetRegion);
                continue;
            }

            List<Replica> existing = replicas.getOrDefault(fileName, Collections.emptyList());

            boolean placed = false;
            for (Provider candidate : providers) {
                String cName = candidate.getName();
                if (cName.equals(sourceVm.getProvider()) && targetRegion.equals(sourceVm.getRegion())) continue;

                boolean exists = existing.stream().anyMatch(r -> r.getTargetProvider().equals(cName)
                                && r.getTargetRegion().equals(targetRegion)
                                && r.isActive());
                if (exists) { placed = true; break; }

                double eMc = estimatedMonetaryCost(fileName, candidate, targetRegion);
                double eRespT = (eMc < sla.csla)
                        ? estimatedResponseTime(query, fileName, candidate, targetRegion)
                        : Double.MAX_VALUE;

                Log.println(String.format("    %s/%s : eMc=%.5f(CSLA=%.3f) %s | eRespT=%.1f(TSLA=%.0f) %s",
                        cName, targetRegion, eMc, sla.csla,
                        eMc < sla.csla ? "✓" : "✗",
                        eRespT == Double.MAX_VALUE ? 0 : eRespT, sla.tsla,
                        eRespT < sla.tsla ? "✓ PLACE" : "✗"));

                if (eMc < sla.csla && eRespT < sla.tsla) {
                    Replica r = new Replica(fileName,
                            sourceVm.getProvider(), sourceVm.getRegion(),
                            cName, targetRegion,
                            cName + "_" + targetRegion + "_DC0");
                    r.setCreationTime(currentTime);
                    replicas.computeIfAbsent(fileName, k -> new ArrayList<>()).add(r);
                    totalReplicasCreated++;
                    Log.println("    ✓ Réplica créé : " + r);
                    placed = true;
                    break;
                }
            }

            if (!placed) Log.println("    [WARN] Aucun provider valide pour " + fileName);
        }
    }

    public int deleteUnpopularReplicas(double currentTime, int deltaT) {
        int deleted = 0;
        for (Map.Entry<String, List<Replica>> e : replicas.entrySet()) {
            String fileName = e.getKey();
            popularityTracker.snapshotPopularity(fileName);
            for (Replica r : e.getValue()) {
                if (!r.isActive()) continue;
                if (popularityTracker.isBelowThresholdForDeltaT(fileName, sla.psla, deltaT)) {
                    r.setActive(false);
                    deleted++;
                    Log.println("  [Algo3] Réplica supprimé : " + r);
                }
            }
        }
        return deleted;
    }

    private double estimatedMonetaryCost(String fileName, Provider p, String region) {
        ExtendedVm vm = vmResult.relationVmMap.get(fileName);
        if (vm == null) return Double.MAX_VALUE;
        double cpuSec = vm.getRelationSizeMB() / p.getMips(region);
        double cpuCost = (p.getCpuCostPerMI(region) / 3600.0) * cpuSec;
        double bwCost = (vm.getRelationSizeMB() / 1024.0) * p.getBwIntraDcCost(region);
        return cpuCost + bwCost;
    }

    private double estimatedResponseTime(Query query, String fileName, Provider p, String region) {
        ExtendedVm vm = vmResult.relationVmMap.get(fileName);
        if (vm == null) return Double.MAX_VALUE;
        double cpuMs = (vm.getRelationSizeMB() / p.getMips(region)) * 1000.0;

        double latencyMs = getNetworkLatency(query.getRegion(), region, getProviderForQuery(query), p.getName());

        return cpuMs + latencyMs;
    }

    private String getProviderForQuery(Query query) {
        ExtendedVm vm = vmResult.relationVmMap.get(query.getRequiredFiles().getFirst());
        return vm != null ? vm.getProvider() : "AWS";
    }

    private static double getNetworkLatency(String rA, String rB, String pA, String pB) {
        if (!pA.equals(pB)) return SimulationParameters.LAT_INTER_PROVIDER_MS;
        if (rA.equals(rB))  return SimulationParameters.LAT_INTRA_REGION_MS;
        if (Set.of("UE","US").containsAll(Set.of(rA,rB))) return SimulationParameters.LAT_UE_US_MS;
        if (Set.of("US","AS").containsAll(Set.of(rA,rB))) return SimulationParameters.LAT_US_AS_MS;
        return SimulationParameters.LAT_UE_AS_MS;
    }

    public boolean hasActiveReplicaInRegion(String fileName, String region) {
        List<Replica> list = replicas.get(fileName);
        if (list == null) return false;
        return list.stream().anyMatch(r -> r.isActive() && r.getTargetRegion().equals(region));
    }

    public int getTotalReplicasCreated()  { return totalReplicasCreated; }
    public int getActiveReplicaCount() {
        return replicas.values().stream()
                .mapToInt(l -> (int) l.stream().filter(Replica::isActive).count()).sum();
    }
    public Map<String, List<Replica>> getReplicas() { return replicas; }
    public PopularityTracker getPopularityTracker() { return popularityTracker; }
    public void recordReplicaCount() { replicaCountHistory.add(getActiveReplicaCount()); }
    public List<Integer> getReplicaCountHistory() { return replicaCountHistory; }
}