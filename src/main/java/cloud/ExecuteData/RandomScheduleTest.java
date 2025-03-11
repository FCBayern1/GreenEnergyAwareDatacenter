package cloud.ExecuteData;

import cloud.*;
import cloud.brokers.RandomDatacenterBroker;
import cloud.datacenter.PowerDatacenterRandom;
import cloud.policy.VmAllocationAssignerRandom;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.planetlab.PlanetLabHelper;

import org.cloudbus.cloudsim.power.PowerHost;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import static cloud.Constants.*;


public class RandomScheduleTest {

    /**
     * The cloudlet list.
     */
    private static List<Cloudlet> cloudletList;
    /**
     * The vmlist.
     */
    public static List<Vm> vmList;
    private static List<PowerHost> hostList;
    private static DatacenterBroker broker;
    public static int brokerId;
    private static VmAllocationAssignerRandom vmAllocationAssignerRandom;
    private static double smallestdata = Double.MAX_VALUE;

    public List<Double> execute() throws Exception {

        for (int i = 0; i < 1; i++) {
            vmAllocationAssignerRandom = new VmAllocationAssignerRandom(GenExcel.getInstance());

            CloudSim.init(1, Calendar.getInstance(), false);
            broker = createBroker();
            brokerId = broker.getId();
            cloudletList = PlanetLabHelper.createCloudletListPlanetLab(brokerId, inputFolder);
            vmList = newHelper.createVmList(brokerId, cloudletList.size());
            hostList = newHelper.createHostList(Constants.NUMBER_OF_HOSTS);
            VmAllocationPolicy vmAllocationPolicy = new NewPowerAllocatePolicy(hostList);

            PowerDatacenterRandom datacenter1 = createDatacenter(
                    "Datacenter1",
                    PowerDatacenterRandom.class,
                    hostList,
                    vmAllocationPolicy, 20000);

            PowerDatacenterRandom datacenter2 = createDatacenter(
                    "Datacenter2",
                    PowerDatacenterRandom.class,
                    hostList,
                    vmAllocationPolicy, 10000);

            datacenter1.setDisableMigrations(false);
            datacenter2.setDisableMigrations(false);

            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            CloudSim.terminateSimulation(terminateTime);
            double lastClock = CloudSim.startSimulation();
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            System.out.println(i + "----------------------------------");
        }

        System.out.println(vmList.size());
        for (PowerHost host : hostList) {
            System.out.println("Datacenter "+ host.getDatacenter().getId() + " Host number "+host.getId() + ":" + "VM size of this host "+host.getVmList().size() + ":" + " Available MIPS" + host.getAvailableMips());
        }
        for (int i = 0; i < PowerDatacenterRandom.allpower.size(); i++) {
            System.out.println(PowerDatacenterRandom.allpower.get(i));
            if (PowerDatacenterRandom.allpower.get(i) < smallestdata) {
                smallestdata = PowerDatacenterRandom.allpower.get(i);
            }
        }
        System.out.println("Minimum Power Consumption：" + smallestdata);
        return PowerDatacenterRandom.allpower;
    }

    public PowerDatacenterRandom createDatacenter(
            String name,
            Class<? extends Datacenter> datacenterClass,
            List<PowerHost> hostList,
            VmAllocationPolicy vmAllocationPolicy, double greenpower) throws Exception {
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource locatedgb
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
        RandomDatacenterBroker broker = null;

        try {
            broker = new RandomDatacenterBroker("Broker");
        } catch (Exception var2) {
            var2.printStackTrace();
            System.exit(0);
        }

        return broker;
    }

}


