package cloud.utils;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerHostUtilizationHistory;
import org.cloudbus.cloudsim.power.PowerVm;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ml969$
 * @date 13/03/2025$
 * @description TODO
 */
public class helper {

    public static List<Cloudlet> createCloudletListPlanetLab(int brokerId, String inputFolderName) throws IOException {
        List<Cloudlet> list = new ArrayList<>();
        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel utilizationModelFull = new UtilizationModelFull();
        java.io.File inputFolder = new File(inputFolderName);
        File[] files = inputFolder.listFiles();
        long[] cloudletLengths = {1000, 5000, 10000};
        int[] cloudletPes = {1, 2, 4};
        for (int i = 0; i < files.length; i++) {
            int cloudletType = i % 3;
            Cloudlet cloudlet = new Cloudlet(
                    i,
                    cloudletLengths[cloudletType],
                    cloudletPes[cloudletType],
                    fileSize,
                    outputSize,
                    new UtilizationModelPlanetLabInMemory(
                            files[i].getAbsolutePath(),
                            Constants.SCHEDULING_INTERVAL),
                    utilizationModelFull,
                    utilizationModelFull
            );
            cloudlet.setUserId(brokerId);
            cloudlet.setVmId(i);
            list.add(cloudlet);
        }
        return list;
    }
    public static List<Vm> createVmList(int brokerId, int vmsNumber) {
        List<Vm> vms = new ArrayList();
        int[] vmMips = {500, 1000, 2000};
        int[] vmPes = {1, 2, 2};
        int[] vmRam = {512, 1024, 2048};

        for (int i = 0; i < vmsNumber; ++i) {
            int vmType = i % 3;
            vms.add(new PowerVm(
                    i, brokerId, vmMips[vmType], vmPes[vmType], vmRam[vmType],
                    100000L, 2500L, 1, "Xen", new CloudletSchedulerSpaceShared(), 300.0D
            ));
        }
        return vms;
    }
    public static List<PowerHost> createHostList(int hostsNumber) {
        List<PowerHost> hostList = new ArrayList();
        int[] hostMips = {20000, 40000, 60000};
        int[] hostPes = {2, 4, 8};
        int[] hostRam = {55355, 55355, 55355};
        PowerModel[] powerModels = {
                new PowerModelLinear(250, 0.7),
                new PowerModelLinear(350, 0.8),
                new PowerModelLinear(500, 0.9)
        };

        for (int i = 0; i < hostsNumber; ++i) {
            int hostType = i % 3;
            List<Pe> peList = new ArrayList();
            for (int j = 0; j < hostPes[hostType]; ++j) {
                peList.add(new Pe(j, new PeProvisionerSimple(hostMips[hostType])));
            }
            hostList.add(new PowerHostUtilizationHistory(
                    i, new RamProvisionerSimple(hostRam[hostType]),
                    new BwProvisionerSimple(10000000000L), 1000000L,
                    peList, new VmSchedulerTimeShared(peList), powerModels[hostType]
            ));
        }
        return hostList;
    }
}
