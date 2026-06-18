package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

public class QueryScheduler extends SimEntity {

    private final MulticloudBroker broker;
    private final VmFactory.Result vmResult;
    private final List<Query>      queries;
    private final List<Provider>   providers; // ← NOUVEAU

    private static final long RNG_SEED = 42L;

    public QueryScheduler(String name,
                          MulticloudBroker broker,
                          VmFactory.Result vmResult,
                          List<Query> queries,
                          List<Provider> providers) { // ← NOUVEAU
        super(name);
        this.broker    = broker;
        this.vmResult  = vmResult;
        this.queries   = queries;
        this.providers = providers;
    }

    @Override
    public void startEntity() {
        Log.println(getName() + " is starting...");
        QueryFactory.clearMaps();

        // ── Least-cost strategy (conforme article) ────────────
        NOREPStrategy norepStrategy = new NOREPStrategy(providers, vmResult);

        Random rng        = new Random(RNG_SEED);
        int    cloudletId = 0;

        for (Query query : queries) {
            int nbRepeat = query.getNumberOfQueries();

            for (int rep = 0; rep < nbRepeat; rep++) {

                // ── Sélection least-cost pour chaque fichier ──
                Map<String, ExtendedVm> cheapestVms =
                        norepStrategy.selectCheapestVms(query);

                if (cheapestVms.isEmpty()) {
                    Log.println("  [WARN] Aucune VM pour query "
                            + query.getQid());
                    continue;
                }

                // VM primaire = least-cost du premier fichier
                String     firstFile = query.getRequiredFiles().get(0);
                ExtendedVm primaryVm = cheapestVms.get(firstFile);
                if (primaryVm == null) {
                    primaryVm = cheapestVms.values().iterator().next();
                }

                List<ExtendedVm> targetVms =
                        new ArrayList<>(cheapestVms.values());

                long length = SimulationParameters.randomCloudletLength(rng);

                DataRelationCloudlet cloudlet = new DataRelationCloudlet(
                        cloudletId,
                        length,
                        SimulationParameters.PES_NUMBER,
                        SimulationParameters.CLOUDLET_FILE_SIZE,
                        SimulationParameters.CLOUDLET_OUTPUT_SIZE,
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        new UtilizationModelFull(),
                        targetVms
                );
                cloudlet.setUserId(broker.getId());
                cloudlet.setGuestId(primaryVm.getId());

                double delay = SimulationParameters.randomSubmitDelay(rng);

                QueryFactory.registerCloudlet(
                        cloudletId,
                        query,
                        firstFile,
                        delay);

                schedule(broker.getId(),
                        delay,
                        CloudActionTags.CLOUDLET_SUBMIT,
                        cloudlet);

                Log.println(String.format(
                        "  Cloudlet#%d → VM#%d @ %s [least-cost] | " +
                                "length=%d MI | delay=%.0fms",
                        cloudletId,
                        primaryVm.getId(),
                        primaryVm.getDatacenterName(),
                        length,
                        delay));

                cloudletId++;
            }
        }
        Log.println(getName() + " : " + cloudletId + " cloudlets schedulés.");
    }

    @Override public void processEvent(SimEvent ev) {}

    @Override public void shutdownEntity() {
        Log.println(getName() + " shutting down.");
    }
}