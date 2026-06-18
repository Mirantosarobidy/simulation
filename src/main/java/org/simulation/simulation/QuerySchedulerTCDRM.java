package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

public class QuerySchedulerTCDRM extends SimEntity {

    private final MulticloudBroker       broker;
    private final VmFactory.Result       vmResult;
    private final List<Query> queries;
    private final List<Provider>         providers;
    private final Map<String, Integer> replicaTriggerAt; // fileName → cloudletId trigger
    private final Map<String, ExtendedVm> replicaVmMap;   // fileName → VM réplica

    private static final long RNG_SEED = 42L;

    public QuerySchedulerTCDRM(String name,
                               MulticloudBroker broker,
                               VmFactory.Result vmResult,
                               List<Query> queries,
                               List<Provider> providers,
                               Map<String, Integer> replicaTriggerAt,
                               Map<String, ExtendedVm> replicaVmMap) {
        super(name);
        this.broker           = broker;
        this.vmResult         = vmResult;
        this.queries          = queries;
        this.providers        = providers;
        this.replicaTriggerAt = replicaTriggerAt;
        this.replicaVmMap     = replicaVmMap;
    }

    @Override
    public void startEntity() {
        QueryFactory.clearMaps();
        NOREPStrategy norepStrategy = new NOREPStrategy(providers, vmResult);
        Random rng        = new Random(RNG_SEED);
        int    cloudletId = 0;

        for (Query query : queries) {
            for (int rep = 0; rep < query.getNumberOfQueries(); rep++) {

                Map<String, ExtendedVm> selectedVms = new LinkedHashMap<>();

                for (String fileName : query.getRequiredFiles()) {

                    // ── Vérifier si réplica disponible pour ce cloudletId ──
                    Integer triggerAt = replicaTriggerAt.get(fileName);
                    ExtendedVm replicaVm = replicaVmMap.get(fileName);

                    if (triggerAt != null
                            && replicaVm != null
                            && cloudletId > triggerAt) {
                        // Utiliser le réplica
                        selectedVms.put(fileName, replicaVm);
                        Log.println(String.format(
                                "  [TCDRM] CL#%d %s → réplica VM#%d @ %s",
                                cloudletId, fileName,
                                replicaVm.getId(),
                                replicaVm.getDatacenterName()));
                    } else {
                        // Utiliser la VM least-cost originale
                        Map<String, ExtendedVm> cheapest =
                                norepStrategy.selectCheapestVms(
                                        new Query(query.getQid(),
                                                query.getRegion(),
                                                1,
                                                List.of(fileName)));
                        ExtendedVm vm = cheapest.get(fileName);
                        if (vm != null) selectedVms.put(fileName, vm);
                    }
                }

                if (selectedVms.isEmpty()) { cloudletId++; continue; }

                ExtendedVm primaryVm = selectedVms.get(
                        query.getRequiredFiles().get(0));
                if (primaryVm == null)
                    primaryVm = selectedVms.values().iterator().next();

                long length = SimulationParameters.randomCloudletLength(rng);

                DataRelationCloudlet cloudlet = new DataRelationCloudlet(
                        cloudletId, length,
                        SimulationParameters.PES_NUMBER,
                        SimulationParameters.CLOUDLET_FILE_SIZE,
                        SimulationParameters.CLOUDLET_OUTPUT_SIZE,
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        new ArrayList<>(selectedVms.values())
                );
                cloudlet.setUserId(broker.getId());
                cloudlet.setGuestId(primaryVm.getId());

                double delay = SimulationParameters.randomSubmitDelay(rng);
                QueryFactory.registerCloudlet(
                        cloudletId, query,
                        query.getRequiredFiles().get(0), delay);

                schedule(broker.getId(), delay,
                        CloudActionTags.CLOUDLET_SUBMIT, cloudlet);

                cloudletId++;
            }
        }
        Log.println(getName() + " : " + cloudletId + " cloudlets schedulés.");
    }

    @Override public void processEvent(SimEvent ev) {}
    @Override public void shutdownEntity() {}
}
