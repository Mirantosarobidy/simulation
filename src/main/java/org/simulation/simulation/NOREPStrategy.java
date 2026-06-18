package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.*;

public class NOREPStrategy {

    private final List<Provider>   providers;
    private final VmFactory.Result vmResult;

    public NOREPStrategy(List<Provider> providers, VmFactory.Result vmResult) {
        this.providers = providers;
        this.vmResult  = vmResult;
    }

    public Map<String, ExtendedVm> selectCheapestVms(Query query) {
        Map<String, ExtendedVm> cheapestVms = new LinkedHashMap<>();
        String queryRegion = query.getRegion();

        for (String fileName : query.getRequiredFiles()) {
            ExtendedVm cheapestVm = null;
            double     minCost   = Double.MAX_VALUE;

            for (ExtendedVm vm : vmResult.vmList) {
                if (!vm.getRelationName().equals(fileName)) continue;

                double cost = getExecutionCost(vm, queryRegion);
                if (cost < minCost) {
                    minCost    = cost;
                    cheapestVm = vm;
                }
            }

            if (cheapestVm != null) {
                cheapestVms.put(fileName, cheapestVm);
                Log.println(String.format(
                        "  [NoRep-LC] %s → VM#%d @ %s/%s | cost=%.6f",
                        fileName,
                        cheapestVm.getId(),
                        cheapestVm.getProvider(),
                        cheapestVm.getRegion(),
                        minCost));
            } else {
                Log.println("  [NoRep-LC] WARN : aucune VM pour " + fileName);
            }
        }
        return cheapestVms;
    }

    private double getExecutionCost(ExtendedVm vm, String queryRegion) {
        Provider provider = providers.stream()
                .filter(p -> p.getName().equals(vm.getProvider()))
                .findFirst()
                .orElse(null);
        if (provider == null) return Double.MAX_VALUE;

        // CPU cost
        double cpuSec  = vm.getRelationSizeMB() / provider.getMips(vm.getRegion());
        double cpuCost = (provider.getCpuCostPerMI(vm.getRegion()) / 3600.0)
                * cpuSec;

        // BW cost : inter-provider si VM pas dans la région de la query
        double bwCostPerGB = vm.getRegion().equals(queryRegion)
                ? provider.getBwIntraDcCost(vm.getRegion())
                : provider.getBwInterProviderCost(vm.getRegion());
        double bwCost = (vm.getRelationSizeMB() / 1024.0) * bwCostPerGB;

        return cpuCost + bwCost;
    }
}