package org.simulation.simulation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;

import java.util.*;

public class QueryAnalyzer {

    private static final List<CloudletMetrics> cloudletMetricsList = new ArrayList<>();

    public static List<CloudletMetrics> getCloudletMetricsList() {
        return cloudletMetricsList;
    }

    public static void analyze(
            List<Query> queries,
            List<Cloudlet> finishedCloudlets,
            VmFactory.Result vmResult,
            List<Provider> providers,
            Map<Integer, Query> cloudletToQuery) {

        cloudletMetricsList.clear();

        Map<Integer, double[]> accMap        = new LinkedHashMap<>();
        Map<Integer, int[]>    providerCount = new LinkedHashMap<>();
        for (Query q : queries) {
            accMap.put(q.getQid(), new double[4]);
            providerCount.put(q.getQid(), new int[3]);
        }

        for (Cloudlet cl : finishedCloudlets) {
            if (cl.getStatus() != Cloudlet.CloudletStatus.SUCCESS) continue;

            int   cid   = cl.getCloudletId();
            Query query = cloudletToQuery.get(cid);
            if (query == null) continue;

            double startSec      = cl.getExecStartTime();
            double endSec        = cl.getExecFinishTime();
            double cpuTimeSec    = cl.getActualCPUTime();
            double submitTimeSec = cl.getSubmissionTime();

            double cpuTimeMs  = cpuTimeSec * 100.0;
            double respTimeMs = (endSec - submitTimeSec) * 100.0;

            double storedDelay = QueryFactory.getScheduleDelay(cid);
            double delayTimeMs = (storedDelay >= 0)
                    ? storedDelay
                    : (startSec - submitTimeSec) * 100.0;

            double cpuUsage = cl.getCloudletLength();

            Log.println(String.format("CL#%d submit=%.4fs end=%.4fs" + " | cpu=%.2fms delay=%.0fms resp=%.2fms",
                    cid, submitTimeSec, endSec,
                    cpuTimeMs, delayTimeMs, respTimeMs));

            double cpuCost = 0, bwCost = 0;
            for (String fileName : query.getRequiredFiles()) {
                ExtendedVm vm = vmResult.relationVmMap.get(fileName);
                if (vm == null) continue;

                Provider p = providers.stream()
                        .filter(pr -> pr.getName().equals(vm.getProvider()))
                        .findFirst().orElse(null);
                if (p == null) continue;

                cpuCost += cpuTimeSec * (p.getCpuCostPerMI(vm.getRegion()) / 3600.0);

                bwCost  += (vm.getRelationSizeMB() / 1024.0) * p.getBwInterProviderCost(vm.getRegion());
            }

            int vmId   = cl.getGuestId();
            ExtendedVm execVm = vmResult.vmList.stream()
                    .filter(v -> v != null && v.getId() == vmId)
                    .findFirst().orElse(null);

            int[] prov = providerCount.get(query.getQid());
            if (prov != null && execVm != null) {
                switch (execVm.getProvider().toLowerCase()) {
                    case "google" -> prov[0]++;
                    case "aws"    -> prov[1]++;
                    case "azure"  -> prov[2]++;
                }
            }

            // ── Métriques — durées toutes en ms ──────────────
            CloudletMetrics m = new CloudletMetrics(cid, query.getQid());
            m.cpuUsage = cpuUsage;
            m.cpuTime = cpuTimeMs;     // ms
            m.delayTime = delayTimeMs;   // ms
            m.respTime = respTimeMs;    // ms ← comparé à TSLA (ms)
            m.start = startSec;      // s  (valeur interne CloudSim)
            m.end = endSec;        // s
            m.cpuCost = cpuCost;
            m.bwCost = bwCost;
            m.totalCost = cpuCost + bwCost;
            cloudletMetricsList.add(m);

            double[] acc = accMap.get(query.getQid());
            if (acc != null) {
                acc[0] += respTimeMs;
                acc[1] += cpuCost;
                acc[2] += bwCost;
                acc[3]++;
            }
        }

        for (Query q : queries) {
            double[] acc = accMap.get(q.getQid());
            int[] prov = providerCount.get(q.getQid());
            double count = acc[3];
            if (count > 0) {
                q.setAvgResponseTime(acc[0] / count);  // ms
                q.setAvgCostCpu(acc[1] / count);
                q.setAvgCostBw(acc[2] / count);
                q.setAvgQueryCost((acc[1] + acc[2]) / count);
            }
            q.setGoogle(prov[0]);
            q.setAws(prov[1]);
            q.setAzure(prov[2]);
        }
    }
}