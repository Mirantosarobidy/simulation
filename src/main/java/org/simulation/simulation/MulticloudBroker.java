package org.simulation.simulation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;

import java.util.*;

public class MulticloudBroker extends DatacenterBroker {

    private final Map<String, Integer> dcNameToId;
    private final Map<Integer, String> dcIdToName;
    private int vmsSent = 0;        // VMs envoyées aux DCs
    private int vmsConfirmed = 0;   // VMs confirmées allouées par les DCs

    public MulticloudBroker(String name,
                            List<Provider> providers,
                            Map<String, Datacenter> dcMap) throws Exception {
        super(name);
        this.dcNameToId = new LinkedHashMap<>();
        this.dcIdToName = new LinkedHashMap<>();
        for (Map.Entry<String, Datacenter> entry : dcMap.entrySet()) {
            int id = entry.getValue().getId();
            dcNameToId.put(entry.getKey(), id);
            dcIdToName.put(id, entry.getKey());
        }
        Log.println("MulticloudBroker : " + dcNameToId.size() + " DCs enregistrés.");
    }

    @Override
    protected void createVmsInDatacenter(int datacenterId) {
        Log.println("\n=== [ROUTING] createVmsInDatacenter ===");
        vmsSent = 0;

        for (GuestEntity vm : getGuestList()) {
            if (getGuestsCreatedList().contains(vm)) continue;

            int targetId = datacenterId;
            if (vm instanceof ExtendedVm) {
                String dcName = ((ExtendedVm) vm).getDatacenterName();
                Integer found = dcNameToId.get(dcName);
                if (found != null) targetId = found;
            }
            sendNow(targetId, CloudActionTags.VM_CREATE, vm);
            getGuestsCreatedList().add(vm);
            getVmsToDatacentersMap().put(vm.getId(), targetId);
            vmsSent++;
        }

        setVmsRequested(vmsSent);
        setVmsAcks(vmsSent);

        Log.println("=== " + vmsSent + " VMs enregistrées ===\n");
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == CloudActionTags.CLOUDLET_SUBMIT && ev.getData() instanceof Cloudlet) {

            Cloudlet cloudlet = (Cloudlet) ev.getData();
            int targetVmId = cloudlet.getGuestId();
            Integer dcId = getVmsToDatacentersMap().get(targetVmId);

            if (dcId != null) {
                Log.println("  Cloudlet#" + cloudlet.getCloudletId() + " → VM#" + targetVmId + " @ DC#" + dcId);
                sendNow(dcId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
                getCloudletSubmittedList().add(cloudlet);
            }
            return;
        }
        super.processEvent(ev);
    }

    @Override
    protected void submitCloudlets() {
        Log.println("\n>>> submitCloudlets() — " + getCloudletList().size() + " cloudlets à soumettre");

        for (Cloudlet cloudlet : getCloudletList()) {
            int targetVmId = cloudlet.getGuestId();

            GuestEntity targetVm = null;
            for (GuestEntity vm : getGuestsCreatedList()) {
                if (vm.getId() == targetVmId) {
                    targetVm = vm;
                    break;
                }
            }

            if (targetVm == null) {
                Log.println("  [WARN] VM#" + targetVmId + " introuvable pour Cloudlet#" + cloudlet.getCloudletId() + " — assignation à VM#0 par défaut");
                targetVmId = getGuestsCreatedList().get(0).getId();
            }

            Integer dcId = getVmsToDatacentersMap().get(targetVmId);
            if (dcId == null) {
                Log.println("  [WARN] DC introuvable pour VM#" + targetVmId);
                continue;
            }

            cloudlet.setGuestId(targetVmId);
            Log.println("  Cloudlet#" + cloudlet.getCloudletId() + " → VM#" + targetVmId + " @ DC#" + dcId);

            sendNow(dcId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            getCloudletSubmittedList().add(cloudlet);
        }

        getCloudletList().clear();
    }
}