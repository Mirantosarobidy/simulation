package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.provisioners.*;

import java.util.*;

public class DatacenterFactory {

    // ── Création des 18 datacenters ────────────────────────────

    public static Map<String, Datacenter> createAll(List<Provider> providers, int vmsPerDc) {

        Map<String, Datacenter> map = new LinkedHashMap<>();
        int dcCount = 100;
        int fileCounter = 1;

        for (Provider provider : providers) {
            for (String region : SimulationParameters.REGIONS) {
                for (int dcIdx = 0; dcIdx < SimulationParameters.DC_PER_REGION; dcIdx++) {
                    String dcName = provider.getName() + "_" + region + "_DC" + dcIdx;

                    List<String> files = new ArrayList<>();
                    for (int f = 0; f < vmsPerDc; f++) {
                        files.add("F" + fileCounter++);
                    }

                    Datacenter dc = createDatacenter(dcName, provider, region, files);
                    if (dc != null) {
                        map.put(dcName, dc);
                        dcCount++;
                    }
                }
            }
        }

        Log.println("=== " + dcCount + " datacenters créés ===");
        for (String name : map.keySet()) {
            Log.println("  " + name + " (id=" + map.get(name).getId() + ")");
        }
        return map;
    }

    private static Datacenter createDatacenter(String name, Provider provider, String region, List<String> fileNames) {

        int  mips = provider.getMips(region);
        int  nbPes = provider.getPes(region);
        int  ramPerVm = provider.getRam(region);
        long bw = provider.getBw(region);
        long storage = 2_000_000L;
        int  nbVms = fileNames.size();

        List<Host> hostList = new ArrayList<>();
        for (int h = 0; h < nbVms; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int i = 0; i < nbPes; i++) {
                peList.add(new Pe(i, new PeProvisionerSimple(mips)));
            }
            hostList.add(new Host(
                    h,
                    new RamProvisionerSimple(ramPerVm),
                    new BwProvisionerSimple((int) bw),
                    storage,
                    peList,
                    new VmSchedulerTimeShared(peList)
            ));
        }

        Log.println("[DC] " + name + " : " + nbVms + " hosts | RAM=" + ramPerVm + "MB | PEs=" + nbPes);

        double costPerSec = provider.getCpuCostPerMI(region) / 3600.0;
        double costPerMem = provider.getIoCostPerGB(region);
        double costPerStorage = 0.0001;
        double costPerBw = provider.getBwIntraDcCost(region);

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                        "x86", "Linux", "Xen",
                        hostList, 10.0,
                        costPerSec, costPerMem,
                        costPerStorage, costPerBw);

        LinkedList<Storage> storageList = new LinkedList<>();
        try {
            HarddriveStorage hdd = new HarddriveStorage(name + "_HDD", 100_000.0);
            for (String fname : fileNames) {
                double fsize = 300 + Math.random() * 400;
                File f = new File(fname, (int) fsize);
                hdd.addFile(f);
            }
            storageList.add(hdd);
        } catch (ParameterException e) {
            Log.println("[ERREUR] HDD pour " + name + " : " + e.getMessage());
        }

        Datacenter dc = null;
        try {
            dc = new Datacenter(
                    name, characteristics,
                    new VmAllocationPolicySimple(hostList),
                    storageList,
                    0
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dc;
    }

    // ── Configuration NetworkTopology ──────────────────────────

    public static void initNetworkTopology(Map<String, Datacenter> dcMap, int brokerId) {

        Log.println("\n=== Configuration NetworkTopology ===");

        String[] providers = SimulationParameters.PROVIDERS;
        String[] regions   = SimulationParameters.REGIONS;

        // 1. Liens intra-région
        for (String provider : providers) {
            for (String region : regions) {
                String dc0Name = provider + "_" + region + "_DC0";
                String dc1Name = provider + "_" + region + "_DC1";
                Datacenter dc0 = dcMap.get(dc0Name);
                Datacenter dc1 = dcMap.get(dc1Name);
                if (dc0 != null && dc1 != null) {
                    addLink(dc0.getId(), dc1.getId(), SimulationParameters.BW_INTRA_REGION_MBPS, SimulationParameters.LAT_INTRA_REGION_MS, dc0Name + " ↔ " + dc1Name + " [intra-région]");
                }
            }
        }

        // 2. Liens inter-région (même provider)
        for (String provider : providers) {
            addInterRegionLinks(dcMap, provider, "UE", "US", SimulationParameters.LAT_UE_US_MS);
            addInterRegionLinks(dcMap, provider, "US", "AS", SimulationParameters.LAT_US_AS_MS);
            addInterRegionLinks(dcMap, provider, "UE", "AS", SimulationParameters.LAT_UE_AS_MS);
        }

        // 3. Liens inter-provider (même région)
        for (String region : regions) {
            for (int i = 0; i < providers.length; i++) {
                for (int j = i + 1; j < providers.length; j++) {
                    addInterProviderLinks(dcMap, providers[i], providers[j], region);
                }
            }
        }

        // 4. Liens Broker ↔ DC
        for (Map.Entry<String, Datacenter> entry : dcMap.entrySet()) {
            String dcName = entry.getKey();
            int    dcId   = entry.getValue().getId();
            String region = dcName.split("_")[1];
            double latency = SimulationParameters.LAT_BROKER_DC.getOrDefault(region, 80.0);
            addLink(brokerId, dcId, SimulationParameters.BW_BROKER_DC_MBPS, latency, "Broker ↔ " + dcName);
        }

        Log.println("=== NetworkTopology configurée ===\n");
    }

    // ── Helpers privés ────────────────────────────────────────

    private static void addInterRegionLinks(Map<String, Datacenter> dcMap, String provider, String regionA, String regionB, double latency) {

        for (int i = 0; i < SimulationParameters.DC_PER_REGION; i++) {
            for (int j = 0; j < SimulationParameters.DC_PER_REGION; j++) {
                String nameA = provider + "_" + regionA + "_DC" + i;
                String nameB = provider + "_" + regionB + "_DC" + j;
                Datacenter dcA = dcMap.get(nameA);
                Datacenter dcB = dcMap.get(nameB);
                if (dcA != null && dcB != null) {
                    addLink(dcA.getId(), dcB.getId(), SimulationParameters.BW_INTER_REGION_MBPS, latency, nameA + " ↔ " + nameB + " [inter-région " + latency + "ms]");
                }
            }
        }
    }

    private static void addInterProviderLinks(Map<String, Datacenter> dcMap, String providerA, String providerB, String region) {

        for (int i = 0; i < SimulationParameters.DC_PER_REGION; i++) {
            for (int j = 0; j < SimulationParameters.DC_PER_REGION; j++) {
                String nameA = providerA + "_" + region + "_DC" + i;
                String nameB = providerB + "_" + region + "_DC" + j;
                Datacenter dcA = dcMap.get(nameA);
                Datacenter dcB = dcMap.get(nameB);
                if (dcA != null && dcB != null) {
                    addLink(dcA.getId(), dcB.getId(), SimulationParameters.BW_INTER_PROVIDER_MBPS, SimulationParameters.LAT_INTER_PROVIDER_MS, nameA + " ↔ " + nameB + " [inter-provider]");
                }
            }
        }
    }

    private static void addLink(int idA, int idB, double bwMbps, double latencyMs, String label) {
        NetworkTopology.addLink(idA, idB, bwMbps, latencyMs);
        NetworkTopology.addLink(idB, idA, bwMbps, latencyMs);
        Log.println("  Lien : " + label + " | BW=" + bwMbps + "Mbps" + " | lat=" + latencyMs + "ms");
    }
}