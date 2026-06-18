package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

/**
 * Test minimal pour diagnostiquer le problème de routing.
 * Affiche exactement ce que CloudSim voit.
 */
public class DiagnosticTest {

    public static void main(String[] args) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<Provider> providers = Creation.createProviders();
        Map<String, Datacenter> dcMap = DatacenterFactory.createAll(providers, 5);

        // Afficher les IDs des DCs créés
        System.out.println("\n=== IDs des DCs ===");
        for (Map.Entry<String, Datacenter> e : dcMap.entrySet()) {
            System.out.println("  '" + e.getKey() + "' → id=" + e.getValue().getId());
        }

        MulticloudBroker broker = new MulticloudBroker("Broker", providers, dcMap);
        DatacenterFactory.initNetworkTopology(dcMap, broker.getId());

        VmFactory.Result vmResult = VmFactory.createAll(broker.getId(), providers);

        // Afficher les 5 premières VMs pour vérifier IDs et dcName
        System.out.println("\n=== 5 premières VMs ===");
        for (int i = 0; i < Math.min(5, vmResult.vmList.size()); i++) {
            ExtendedVm vm = vmResult.vmList.get(i);
            System.out.println(String.format(
                    "  VM id=%d | relation=%s | dcName='%s' | provider=%s | region=%s",
                    vm.getId(), vm.getRelationName(),
                    vm.getDatacenterName(), vm.getProvider(), vm.getRegion()));
        }

        // Vérifier si dcName correspond à une clé connue
        System.out.println("\n=== Vérification correspondance dcName → dcId ===");
        Set<String> dcKeys = dcMap.keySet();
        int ok = 0, notFound = 0;
        for (ExtendedVm vm : vmResult.vmList) {
            if (dcKeys.contains(vm.getDatacenterName())) {
                ok++;
            } else {
                System.out.println("  [MANQUANT] '" + vm.getDatacenterName() + "'");
                notFound++;
            }
        }
        System.out.println("  Correspondances : " + ok + " OK | " + notFound + " manquants");

        System.out.println("\n=== Test terminé — pas de simulation ===");
    }
}
