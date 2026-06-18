package org.simulation.simulation;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Log;

import java.util.*;

public class VmFactory {

    public static Result createAll(int brokerId, List<Provider> providers) {
        List<ExtendedVm> vmList = new ArrayList<>();
        Map<String, ExtendedVm> relationVmMap = new LinkedHashMap<>();

        int vmId = 0;
        int relationCounter = 1;

        for (Provider provider : providers) {
            for (String region : new String[]{"UE", "US", "AS"}) {
                for (int dcIdx = 0; dcIdx < 2; dcIdx++) {
                    String dcName = provider.getName() + "_" + region + "_DC" + dcIdx;

                    for (int f = 0; f < 5; f++) { // ← 5 VMs par DC
                        ExtendedVm vm = new ExtendedVm(
                                vmId++, brokerId,
                                provider.getMips(region),
                                provider.getPes(region),
                                provider.getRam(region),
                                provider.getBw(region),
                                10000L,
                                "Xen",
                                new CloudletSchedulerTimeShared(),
                                region, provider.getName(), dcName
                        );
                        vm.setRelationName("F" + relationCounter);
                        vm.setRelationSizeMB(300 + Math.random() * 400);
                        relationVmMap.put("F" + relationCounter, vm);
                        relationCounter++;
                        vmList.add(vm);
                    }
                }
            }
        }

        Log.println("\n=== " + vmList.size() + " VMs créées ===");
        printVmTable(vmList);
        return new Result(vmList, relationVmMap);
    }

    private static void printVmTable(List<ExtendedVm> vmList) {
        Log.println(String.format("%-6s │ %-8s │ %-25s │ %-8s │ %-6s │ %s", "VM ID", "Relation", "Datacenter", "Provider", "Région", "Taille(MB)"));
        Log.println("─".repeat(75));

        String currentDc = "";
        for (ExtendedVm vm : vmList) {
            if (!vm.getDatacenterName().equals(currentDc)) {
                currentDc = vm.getDatacenterName();
                Log.println("  ── " + currentDc + " ──");
            }
            Log.println(String.format(
                    "%-6d │ %-8s │ %-25s │ %-8s │ %-6s │ %.1f",
                    vm.getId(),
                    vm.getRelationName(),
                    vm.getDatacenterName(),
                    vm.getProvider(),
                    vm.getRegion(),
                    vm.getRelationSizeMB()));
        }
    }

    public static class Result {
        public final List<ExtendedVm> vmList;
        public final Map<String, ExtendedVm> relationVmMap;

        public Result(List<ExtendedVm> vmList,
                      Map<String, ExtendedVm> relationVmMap) {
            this.vmList = vmList;
            this.relationVmMap = relationVmMap;
        }
    }
}