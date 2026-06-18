package org.simulation.simulation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;

import java.util.*;

public class QueryFactory {

    private static final Map<Integer, Query>  cloudletToQuery = new LinkedHashMap<>();
    private static final Map<Integer, String> cloudletToFile  = new LinkedHashMap<>();
    private static final Map<String, ExtendedVm> fileToVm     = new LinkedHashMap<>();
    private static final Map<Integer, Double> cloudletScheduleDelay = new LinkedHashMap<>();

    public static List<Cloudlet> createCloudlets(
            List<Query> queries,
            VmFactory.Result vmResult,
            int brokerId) {

        cloudletToQuery.clear();
        cloudletToFile.clear();
        fileToVm.clear();
        cloudletScheduleDelay.clear();

        List<Cloudlet> cloudletList = new ArrayList<>();
        int cloudletId = 0;

        for (Query query : queries) {
            Log.println("\n[Query #" + query.getQid() + "]"
                    + " région=" + query.getRegion()
                    + " | répétitions=" + query.getNumberOfQueries()
                    + " | fichiers=" + query.getRequiredFiles());

            List<ExtendedVm> targetVms = new ArrayList<>();
            boolean valid = true;
            for (String fileName : query.getRequiredFiles()) {
                ExtendedVm vm = vmResult.relationVmMap.get(fileName);
                if (vm == null) {
                    Log.println("  [WARN] Fichier introuvable : " + fileName);
                    valid = false;
                    break;
                }
                targetVms.add(vm);
                fileToVm.put(fileName, vm);
            }
            if (!valid) continue;

            for (int n = 0; n < query.getNumberOfQueries(); n++) {

                long totalLength = 0;
                long totalFileSize = 0;
                long totalOutput = 0;

                for (ExtendedVm vm : targetVms) {
                    totalLength += (long)(vm.getRelationSizeMB() * 10);
                    totalFileSize += (long)(vm.getRelationSizeMB() * 10);
                    totalOutput += (long)(vm.getRelationSizeMB() * 5);
                }

                ExtendedVm primaryVm = targetVms.get(0);

                DataRelationCloudlet cloudlet = new DataRelationCloudlet(
                        cloudletId,
                        totalLength,
                        1,
                        totalFileSize,
                        totalOutput,
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        targetVms
                );
                cloudlet.setUserId(brokerId);
                cloudlet.setGuestId(primaryVm.getId());

                cloudletToQuery.put(cloudletId, query);
                cloudletToFile.put(cloudletId, query.getRequiredFiles().get(0));

                cloudletList.add(cloudlet);
                cloudletId++;
            }
        }

        Log.println("\nQueryFactory : " + cloudletList.size() + " cloudlets créés pour " + queries.size() + " quer" + (queries.size() > 1 ? "ies" : "y") + ".");
        return cloudletList;
    }

    public static void registerCloudlet(
            int cloudletId,
            Query query,
            String primaryFile) {
        cloudletToQuery.put(cloudletId, query);
        cloudletToFile.put(cloudletId, primaryFile);
    }

    public static void registerCloudlet(
            int cloudletId,
            Query query,
            String primaryFile,
            double scheduleDelay) {
        cloudletToQuery.put(cloudletId, query);
        cloudletToFile.put(cloudletId, primaryFile);
        cloudletScheduleDelay.put(cloudletId, scheduleDelay); // ← stockage
    }

    public static Map<Integer, Query>  getCloudletToQuery()  { return cloudletToQuery; }
    public static Map<Integer, String> getCloudletToFile()   { return cloudletToFile; }


    public static double getScheduleDelay(int cloudletId) {
        return cloudletScheduleDelay.getOrDefault(cloudletId, -1.0);
    }

    public static void clearMaps() {
        cloudletToQuery.clear();
        cloudletToFile.clear();
        fileToVm.clear();
        cloudletScheduleDelay.clear();
    }
}