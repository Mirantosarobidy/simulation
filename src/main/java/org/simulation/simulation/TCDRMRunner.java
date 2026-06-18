package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

public class TCDRMRunner {

    private final TCDRMStrategy   tcdrm;
    private final SLA sla;
    private final VmFactory.Result vmResult;

    private int periodCounter = 0;
    private static final int DELETION_CHECK_INTERVAL = 50;

    private Integer firstReplicationCloudletId = null;
    private int     cloudletsWithReplica       = 0;
    private double  totalLatencySaved          = 0.0;
    private int     thrashingCount             = 0;

    // pour détecter le thrashing : file→nb fois créé
    private final Map<String, Integer> replicaCreatedCount  = new LinkedHashMap<>();
    private final Map<String, Integer> replicaDeletedCount  = new LinkedHashMap<>();

    public TCDRMRunner(TCDRMStrategy tcdrm, SLA sla, VmFactory.Result vmResult) {
        this.tcdrm     = tcdrm;
        this.sla       = sla;
        this.vmResult  = vmResult;
    }

    public Integer getFirstReplicationCloudletId() { return firstReplicationCloudletId; }
    public int     getThrashingCount()              { return thrashingCount; }

    public List<CloudletMetrics> run(
            List<Query> queries,
            List<CloudletMetrics> norepMetrics,
            Map<Integer, Query> cloudletToQuery) {

        List<CloudletMetrics> tcdrmMetrics = new ArrayList<>();
        List<CloudletMetrics> sorted = norepMetrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();

        Log.println("\n=== [TCDRM] Début du traitement (" + sorted.size() + " cloudlets) ===");

        for (CloudletMetrics norepM : sorted) {
            Query query = cloudletToQuery.get(norepM.cloudletId);
            if (query == null) continue;

            int prevReplicas = tcdrm.getTotalReplicasCreated();
            List<String> toReplicate = tcdrm.checkAndReplicate(query, norepM);

            if (!toReplicate.isEmpty()) {
                if (firstReplicationCloudletId == null)
                    firstReplicationCloudletId = norepM.cloudletId;
                tcdrm.placeReplicas(toReplicate, query, norepM.start);
                tcdrm.recordReplicaCount();

                for (String fn : toReplicate)
                    replicaCreatedCount.merge(fn, 1, Integer::sum);
            }

            CloudletMetrics tcdrmM = recomputeWithReplicas(norepM, query);
            tcdrmMetrics.add(tcdrmM);

            periodCounter++;
            if (periodCounter % DELETION_CHECK_INTERVAL == 0) {
                int deleted = tcdrm.deleteUnpopularReplicas(norepM.start, (int) sla.deltaT);
                if (deleted > 0) {
                    Log.println("[TCDRM] " + deleted + " réplica(s) supprimé(s) à t=" + norepM.start);
                    // thrashing : un réplica recréé après suppression
                    for (String fn : replicaCreatedCount.keySet()) {
                        int cr = replicaCreatedCount.getOrDefault(fn, 0);
                        int dl = replicaDeletedCount.merge(fn, deleted, Integer::sum);
                        if (cr > 1 || dl > 0) thrashingCount++;
                    }
                }
                tcdrm.recordReplicaCount();
            }
        }

        Log.println(String.format(
                "=== [TCDRM] Terminé. Réplicas actifs=%d | cloudlets=%d | latence économisée=%.1fms | thrashing=%d ===\n",
                tcdrm.getActiveReplicaCount(), cloudletsWithReplica, totalLatencySaved, thrashingCount));

        return tcdrmMetrics;
    }

    private CloudletMetrics recomputeWithReplicas(CloudletMetrics base, Query query) {
        CloudletMetrics m = new CloudletMetrics(base.cloudletId, base.queryId);
        m.cpuUsage = base.cpuUsage;
        m.cpuTime  = base.cpuTime;
        m.cpuCost  = base.cpuCost;
        m.start    = base.start;
        m.end      = base.end;
        m.delayTime = base.delayTime;

        double newBwCost    = 0.0;
        double savedLatency = 0.0;
        int fileCount       = query.getRequiredFiles().size();
        boolean anyReplica  = false;

        for (String fileName : query.getRequiredFiles()) {
            boolean hasReplica = tcdrm.hasActiveReplicaInRegion(fileName, query.getRegion());
            if (hasReplica) {
                double gain = SimulationParameters.LAT_INTER_PROVIDER_MS
                        - SimulationParameters.LAT_INTRA_REGION_MS;
                savedLatency += gain;
                anyReplica = true;
            } else {
                newBwCost += base.bwCost / fileCount;
            }
        }

        m.bwCost    = newBwCost;
        m.totalCost = m.cpuCost + m.bwCost;
        m.respTime  = Math.max(0.01, base.respTime - savedLatency);

        if (anyReplica) {
            cloudletsWithReplica++;
            totalLatencySaved += savedLatency;
        }
        return m;
    }
}
