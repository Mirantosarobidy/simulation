package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

public class TCDRMPredRunner {

    private final TCDRMPredStrategy tcdrm;
    private final SLA sla;
    private final VmFactory.Result vmResult;

    private int periodCounter = 0;
    private static final int DELETION_CHECK_INTERVAL = 50;

    private Integer firstReplicationCloudletId = null;
    private final Map<String, Double> replicaCreationTime = new LinkedHashMap<>();

    // Compteurs méthodes de prédiction
    public final Map<String, Integer> methodCounts = new LinkedHashMap<>(
            Map.of("EMA", 0, "REGRESSION", 0, "LSTM", 0));

    // Thrashing
    private int thrashingCount = 0;
    private final Map<String, Integer> replicaCreatedCount = new LinkedHashMap<>();

    public TCDRMPredRunner(TCDRMPredStrategy tcdrm, SLA sla, VmFactory.Result vmResult) {
        this.tcdrm    = tcdrm;
        this.sla      = sla;
        this.vmResult = vmResult;
    }

    public Integer getFirstReplicationCloudletId() { return firstReplicationCloudletId; }
    public int     getThrashingCount()              { return thrashingCount; }

    public List<CloudletMetrics> run(
            List<Query> queries,
            List<CloudletMetrics> norepMetrics,
            Map<Integer, Query>   cloudletToQuery) {

        List<CloudletMetrics> tcdrmMetrics = new ArrayList<>();
        List<CloudletMetrics> sorted = norepMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();

        Log.println("\n=== [TCDRM-Pred] Début traitement (" + sorted.size() + " cloudlets) ===");

        for (CloudletMetrics norepM : sorted) {
            Query query = cloudletToQuery.get(norepM.cloudletId);
            if (query == null) continue;

            // Enregistrer accès pour prédiction
            for (String fn : query.getRequiredFiles())
                PopularityRegistry.getInstance().recordAccess(fn, norepM.start * 100.0);

            // Tracker la méthode utilisée
            for (String fn : query.getRequiredFiles()) {
                try {
                    String method = PopularityRegistry.getInstance().currentMethod(fn).name();
                    methodCounts.merge(method, 1, Integer::sum);
                } catch (Exception ignored) {}
            }

            List<String> toReplicate = tcdrm.checkAndReplicate(query, norepM);

            if (!toReplicate.isEmpty()) {
                if (firstReplicationCloudletId == null)
                    firstReplicationCloudletId = norepM.cloudletId;
                tcdrm.placeReplicas(toReplicate, query, norepM.start);
                tcdrm.recordReplicaCount();

                for (String fn : toReplicate) {
                    replicaCreationTime.merge(fn, norepM.start, Math::min);
                    replicaCreatedCount.merge(fn, 1, Integer::sum);
                }
            }

            CloudletMetrics tcdrmM = recomputeWithReplicas(norepM, query);
            tcdrmMetrics.add(tcdrmM);

            periodCounter++;
            if (periodCounter % DELETION_CHECK_INTERVAL == 0) {
                int deleted = tcdrm.deleteUnpopularReplicas(norepM.start, (int) sla.deltaT);
                if (deleted > 0) {
                    Log.println("[TCDRM-Pred] " + deleted + " réplica(s) supprimé(s) à t=" + norepM.start);
                    for (String fn : replicaCreatedCount.keySet())
                        if (replicaCreatedCount.getOrDefault(fn, 0) > 1) thrashingCount++;
                }
                tcdrm.recordReplicaCount();
            }
        }

        Log.println("=== [TCDRM-Pred] Terminé. Réplicas actifs=" + tcdrm.getActiveReplicaCount()
                + " | thrashing=" + thrashingCount + " | budget restant=$" + String.format("%.4f", tcdrm.getBudgetRemaining()) + " ===\n");
        return tcdrmMetrics;
    }

    private CloudletMetrics recomputeWithReplicas(CloudletMetrics base, Query query) {
        CloudletMetrics m = new CloudletMetrics(base.cloudletId, base.queryId);
        m.cpuUsage  = base.cpuUsage;
        m.cpuTime   = base.cpuTime;
        m.cpuCost   = base.cpuCost;
        m.start     = base.start;
        m.end       = base.end;
        m.delayTime = base.delayTime;

        double newBwCost    = 0.0;
        double savedLatency = 0.0;
        int fileCount       = query.getRequiredFiles().size();

        for (String fileName : query.getRequiredFiles()) {
            boolean hasLocalReplica = tcdrm.hasActiveReplicaInRegion(fileName, query.getRegion());
            if (hasLocalReplica) {
                Double createdAt = replicaCreationTime.get(fileName);
                boolean isTrigger = (createdAt != null) && (Math.abs(base.start - createdAt) < 1e-6);
                if (!isTrigger) {
                    savedLatency += SimulationParameters.LAT_INTER_PROVIDER_MS
                            - SimulationParameters.LAT_INTRA_REGION_MS;
                }
            } else {
                newBwCost += base.bwCost / fileCount;
            }
        }

        m.bwCost    = newBwCost;
        m.totalCost = m.cpuCost + m.bwCost;
        m.respTime  = Math.max(0.01, base.respTime - savedLatency);
        return m;
    }
}
