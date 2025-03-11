package cloud.ExecuteData;

/**
 * @author ml969$
 * @date 25/02/2025$
 * @description TODO
 */

import cloud.*;
import cloud.brokers.QLearningDatacenterBroker;
import cloud.datacenter.PowerDatacenterRandom;
import cloud.policy.VmAllocationAssignerRandom;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.planetlab.PlanetLabHelper;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerHostUtilizationHistory;
import org.cloudbus.cloudsim.power.PowerVm;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import static cloud.Constants.*;


public class Cross_Datacenter_Test {

    /**
     * The cloudlet list.
     */
    private static List<Cloudlet> cloudletList;
    /**
     * The vmlist.
     */
    public static List<Vm> vmList;
    private static List<PowerHost> hostList;
    private static List<PowerDatacenterRandom> powerDatacenterRandomList;
    private static DatacenterBroker broker;
    public static int brokerId;
    private static VmAllocationAssignerRandom vmAllocationAssignerRandom;
    private static double smallestdata = Double.MAX_VALUE;
    private static double maxGreenPowerConsumption = Double.MIN_VALUE;

    public Double execute() throws Exception {
        for (int i = 0; i < Iteration; i++) {
            vmAllocationAssignerRandom = new VmAllocationAssignerRandom(GenExcel.getInstance());
            CloudSim.init(1, Calendar.getInstance(), false);
            broker = createBroker();
            brokerId = broker.getId();

            cloudletList = createCloudletListPlanetLab(brokerId, inputFolder);
            vmList = createVmList(brokerId, cloudletList.size());
            hostList = createHostList(NUMBER_OF_HOSTS);
            VmAllocationPolicy vmAllocationPolicy = new NewPowerAllocatePolicy(hostList);

            List<Double> initialGreenpower = new ArrayList<>(List.of(2000000000.0, 30000000000.0));
            PowerDatacenterRandom datacenter1 = createDatacenter(
                    "Datacenter1", PowerDatacenterRandom.class, hostList, vmAllocationPolicy, initialGreenpower.get(0));
            PowerDatacenterRandom datacenter2 = createDatacenter(
                    "Datacenter2", PowerDatacenterRandom.class, hostList, vmAllocationPolicy, initialGreenpower.get(1));

            powerDatacenterRandomList = new ArrayList<>();
            datacenter1.setDisableMigrations(false);
            datacenter2.setDisableMigrations(false);
            powerDatacenterRandomList.add(datacenter1);
            powerDatacenterRandomList.add(datacenter2);

            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            CloudSim.terminateSimulation();
            double lastClock = CloudSim.startSimulation();
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            System.out.println(i + "----------------------------------");

            Double greenPowerConsumption = 0.0;
            for (int j = 0; j < powerDatacenterRandomList.size(); j++) {
                greenPowerConsumption += initialGreenpower.get(j) - powerDatacenterRandomList.get(j).getGreenPower();
            }
            if (greenPowerConsumption > maxGreenPowerConsumption) {
                maxGreenPowerConsumption = greenPowerConsumption;
            }
        }
        for (int i = 0; i < PowerDatacenterRandom.allpower.size(); i++) {
            System.out.println(PowerDatacenterRandom.allpower.get(i));
            if (PowerDatacenterRandom.allpower.get(i) < smallestdata) {
                smallestdata = PowerDatacenterRandom.allpower.get(i);
            }
        }


        System.out.println("vm list size " + vmList.size());
        System.out.println("cloudlet list " + cloudletList.size());
        System.out.println("Total successfully submitted Cloudlets: " + broker.getCloudletSubmittedList().size());
        for (PowerHost host : hostList) {
            System.out.println("Host number " + host.getId() + ": VM size of this host " + host.getVmList().size() + ": Available MIPS " + host.getAvailableMips());
        }
        System.out.println("Maxmium Green Power Consumption：" + maxGreenPowerConsumption);
        System.out.println("Minimum Power Consumption：" + smallestdata);
        return maxGreenPowerConsumption;
    }

    public PowerDatacenterRandom createDatacenter(
            String name,
            Class<? extends Datacenter> datacenterClass,
            List<PowerHost> hostList,
            VmAllocationPolicy vmAllocationPolicy, double greenpower) throws Exception {
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch,
                os,
                vmm,
                hostList,
                time_zone,
                cost,
                costPerMem,
                costPerStorage,
                costPerBw);
        PowerDatacenterRandom datacenter = new PowerDatacenterRandom("test", characteristics, vmAllocationPolicy, new LinkedList<Storage>(), 300, vmAllocationAssignerRandom, greenpower);
        return datacenter;
    }

    public DatacenterBroker createBroker() {
        QLearningDatacenterBroker broker = null;

        try {
            broker = new QLearningDatacenterBroker("Broker");
        } catch (Exception var2) {
            var2.printStackTrace();
            System.exit(0);
        }

        return broker;
    }
    public static List<Cloudlet> createCloudletListPlanetLab(int brokerId, String inputFolderName) throws FileNotFoundException {
        List<Cloudlet> list = new ArrayList<>();
        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel utilizationModelFull = new UtilizationModelFull();
        long[] cloudletLengths = {1000, 5000, 10000};
        int[] cloudletPes = {1, 2, 4};
        for (int i = 0; i < 50; i++) {
            int cloudletType = i % 3;
            Cloudlet cloudlet = new Cloudlet(
                    i,
                    cloudletLengths[cloudletType],
                    cloudletPes[cloudletType],
                    fileSize,
                    outputSize,
                    utilizationModelFull,
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
        int[] vmMips = {5000, 10000, 20000};
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
        int[] hostMips = {200000, 400000, 600000};
        int[] hostPes = {2, 4, 8};
        int[] hostRam = {4096, 8192, 16384};
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


