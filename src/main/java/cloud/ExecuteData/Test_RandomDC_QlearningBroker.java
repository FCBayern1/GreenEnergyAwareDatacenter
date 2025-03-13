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
import cloud.utils.helper;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import static cloud.Constants.*;


public class Test_RandomDC_QlearningBroker {
    private static List<Cloudlet> cloudletList;
    public static List<Vm> vmList;
    private static List<PowerHost> hostList;
    private static List<PowerDatacenterRandom> powerDatacenterRandomList;
    private static DatacenterBroker broker;
    public static int brokerId;
    private static VmAllocationAssignerRandom vmAllocationAssignerRandom;
    private static List<Double> powerconsumption;
    private static double maxGreenPowerConsumption = Double.MIN_VALUE;

    public Double execute() throws Exception {
        powerconsumption = new ArrayList<>();
        List<Double> initialGreenpower = new ArrayList<>();
        double greensum = 0.0;
        for (int i = 0; i < DC_NUM; i++){
            double greenpower = 50000 + Math.random() * 50000;
            initialGreenpower.add(greenpower);
            greensum += greenpower;
        }
        for (int i = 0; i < Iteration; i++) {
            vmAllocationAssignerRandom = new VmAllocationAssignerRandom(GenExcel.getInstance());
            CloudSim.init(1, Calendar.getInstance(), false);
            broker = createBroker();
            brokerId = broker.getId();

            cloudletList = helper.createCloudletListPlanetLab(brokerId, inputFolder);
            vmList = helper.createVmList(brokerId, cloudletList.size());
            hostList = helper.createHostList(NUMBER_OF_HOSTS);
            VmAllocationPolicy vmAllocationPolicy = new NewPowerAllocatePolicy(hostList);

            powerDatacenterRandomList = new ArrayList<>();
            for (int j = 0; j < initialGreenpower.size(); j++) {
                PowerDatacenterRandom datacenter = createDatacenter("Datacenter" + j, PowerDatacenterRandom.class, hostList, vmAllocationPolicy, initialGreenpower.get(j));
                datacenter.setDisableMigrations(false);
                powerDatacenterRandomList.add(datacenter);
            }

            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            CloudSim.terminateSimulation();
            double lastClock = CloudSim.startSimulation();
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            System.out.println(i + "th iteration----------------------------------");

            Double greenPowerConsumption = 0.0;
            for (int j = 0; j < powerDatacenterRandomList.size(); j++) {
                greenPowerConsumption += initialGreenpower.get(j) - powerDatacenterRandomList.get(j).getGreenPower();
            }
            if (greenPowerConsumption > maxGreenPowerConsumption) {
                maxGreenPowerConsumption = greenPowerConsumption;
            }
            double power_consumption = 0.0;
            for (PowerDatacenterRandom datacenter : powerDatacenterRandomList) {
                power_consumption += datacenter.getPower();
            }

            powerconsumption.add(power_consumption);
        }

        System.out.println("vm list size " + vmList.size());
        System.out.println("cloudlet list " + cloudletList.size());
        System.out.println("Total successfully submitted Cloudlets: " + broker.getCloudletSubmittedList().size());
        for (PowerHost host : hostList) {
            System.out.println("Host number " + host.getId() + ": VM size of this host is " + host.getVmList().size() + ": Available MIPS " + host.getAvailableMips());
        }
        double minPowerConsumption = Double.MAX_VALUE;
        for (int i = 0; i < powerconsumption.size(); i++) {
            if(powerconsumption.get(i) < minPowerConsumption) {
                minPowerConsumption = powerconsumption.get(i);
            }
        }
        Log.formatLine("Min Power Consumption is " + minPowerConsumption);
        Log.formatLine("Max Green Power Consumption is " + maxGreenPowerConsumption);
        Log.formatLine("Green power Capacity is " + greensum);
        Log.formatLine("max green power consumption ratio is %.5f%%", (maxGreenPowerConsumption/greensum) * 100);
        return maxGreenPowerConsumption;
    }

    public PowerDatacenterRandom createDatacenter(
            String name,
            Class<? extends Datacenter> datacenterClass,
            List<PowerHost> hostList,
            VmAllocationPolicy vmAllocationPolicy, double greenpower) throws Exception {
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
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

}


