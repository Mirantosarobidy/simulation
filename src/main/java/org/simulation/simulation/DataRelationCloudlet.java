package org.simulation.simulation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;

import java.util.ArrayList;
import java.util.List;

public class DataRelationCloudlet extends Cloudlet {

    /** VMs sources accédées par cette requête */
    private final List<ExtendedVm> dataSourceVms;

    public DataRelationCloudlet(int cloudletId,
                                long cloudletLength,
                                int pesNumber,
                                long cloudletFileSize,
                                long cloudletOutputSize,
                                UtilizationModel cpuModel,
                                UtilizationModel ramModel,
                                UtilizationModel bwModel,
                                List<ExtendedVm> dataSourceVms) {
        super(cloudletId,
                cloudletLength,
                pesNumber,
                cloudletFileSize,
                cloudletOutputSize,
                cpuModel,
                ramModel,
                bwModel);
        // Copie défensive de la liste
        this.dataSourceVms = new ArrayList<>(dataSourceVms);
    }

    // ── Clone public ──────────────────────────────────────────
    public DataRelationCloudlet clone(int newId) {
        DataRelationCloudlet copy = new DataRelationCloudlet(
                newId,
                this.getCloudletLength(),
                this.getNumberOfPes(),
                this.getCloudletFileSize(),
                this.getCloudletOutputSize(),
                this.getUtilizationModelCpu(),
                this.getUtilizationModelRam(),
                this.getUtilizationModelBw(),
                this.dataSourceVms   // liste partagée — lecture seule
        );

        // Copier l'affectation VM si elle a été définie
        copy.setVmId(this.getVmId());
        copy.setUserId(this.getUserId());

        return copy;
    }

    // ── Getter ────────────────────────────────────────────────

    public List<ExtendedVm> getDataSourceVms() {
        return dataSourceVms;
    }
}


