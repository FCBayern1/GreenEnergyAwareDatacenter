package cloud.brokers;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.power.PowerDatacenterBroker;
import cloud.datacenter.PowerDatacenterRandom;
import org.cloudbus.cloudsim.power.PowerHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static cloud.Constants.CREATE_VM_ACK;

/**
 * @author ml969
 * @date 06/03/2025
 * @description A Random-strategy based DatacenterBroker to assign cloudlets randomly to datacenters.
 */
public class RandomDatacenterBroker extends PowerDatacenterBroker {

    private Random random;
    private int requestedVms = 0;

    public RandomDatacenterBroker(String name) throws Exception {
        super(name);
        this.random = new Random();
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CREATE_VM_ACK:
                processVmCreate(ev);
                break;
            default:
                if (ev == null) {
                    Log.printConcatLine(getName(), ".processOtherEvent(): Error - an event is null.");
                }
                break;
        }
    }

    @Override
    protected void processVmCreate(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int datacenterId = data[0];
        int vmId = data[1];
        int result = data[2];
        if (result == CloudSimTags.TRUE) {
            getVmsToDatacentersMap().put(vmId, datacenterId);
            getVmsCreatedList().add(VmList.getById(getVmList(), vmId));
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": VM #", vmId,
                    " has been created in Datacenter #", datacenterId);
        } else {
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Creation of VM #", vmId,
                    " failed in Datacenter #", datacenterId);
            for (int nextDatacenterId : getDatacenterIdsList()) {
                if (nextDatacenterId != datacenterId && !getDatacenterRequestedIdsList().contains(nextDatacenterId)) {
                    createVmsInDatacenter(nextDatacenterId);
                    break;
                }
            }
        }

        incrementVmsAcks();
        if (getVmsCreatedList().size() == getVmList().size() && getVmsRequested() == getVmsAcks()) {
            submitCloudlets();
        } else if (getVmsRequested() == getVmsAcks() && getVmsCreatedList().size() < getVmList().size()) {
            for (int nextDatacenterId : getDatacenterIdsList()) {
                if (!getDatacenterRequestedIdsList().contains(nextDatacenterId)) {
                    createVmsInDatacenter(nextDatacenterId);
                    break;
                }
            }
        }
    }

    @Override
    protected void createVmsInDatacenter(int datacenterId) {
        List<Integer> datacenterIds = getDatacenterIdsList();
        while (requestedVms < getVmList().size()) {
            Vm vm = getVmList().get(requestedVms);
            if (!getVmsToDatacentersMap().containsKey(vm.getId())) {
                int selectedDatacenter = datacenterIds.get(requestedVms % datacenterIds.size());

                Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + vm.getId()
                        + " in Datacenter #" + selectedDatacenter);
                sendNow(selectedDatacenter, CREATE_VM_ACK, vm);
                requestedVms++;
            }
            getDatacenterRequestedIdsList().add(datacenterId);
        }
        setVmsRequested(requestedVms);
        setVmsAcks(0);
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> cloudletList = getCloudletList();
        if (cloudletList.isEmpty()) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": No Cloudlets to submit.");
            return;
        }

        for (Cloudlet cloudlet : cloudletList) {
            // 随机选择数据中心
            int datacenterId = getDatacenterIdsList().get(random.nextInt(getDatacenterIdsList().size()));

            // 找到该数据中心里的 VM
            Vm selectedVm = null;
            for (Vm vm : getVmsCreatedList()) {
                if (getVmsToDatacentersMap().get(vm.getId()) == datacenterId) {
                    selectedVm = vm;
                    break;
                }
            }

            // 如果该数据中心里没有 VM，选择默认 VM
            if (selectedVm == null) {
                selectedVm = getVmsCreatedList().get(0); // 选第一个 VM，防止出错
                Log.printLine("⚠️ No VM found in Datacenter #" + datacenterId + ", using VM #" + selectedVm.getId());
            }

            // 绑定 Cloudlet 到这个 VM
            cloudlet.setVmId(selectedVm.getId());
            sendNow(datacenterId, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Sending Cloudlet #"
                    + cloudlet.getCloudletId() + " to VM #" + selectedVm.getId() + " in Datacenter #" + datacenterId);

            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
        }

        getCloudletList().clear();
    }

    private double getGreenPowerConsumption(int datacenterId, Cloudlet cloudlet) {
        PowerDatacenterRandom datacenter = (PowerDatacenterRandom) CloudSim.getEntity(datacenterId);
        if (datacenter == null) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Error - Datacenter #" + datacenterId + " not found.");
            return 0.0;
        }

        Vm vm = VmList.getById(getVmsCreatedList(), cloudlet.getVmId());
        if (vm == null) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Error - VM not found for Cloudlet #" + cloudlet.getCloudletId());
            return 0.0;
        }
        PowerHost host = (PowerHost) vm.getHost();
        if (host == null) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Error - Host not found for VM #" + vm.getId());
            return 0.0;
        }

        double mips = vm.getMips() * vm.getNumberOfPes();
        double cloudletLength = cloudlet.getCloudletLength();
        double executionTime = cloudletLength / mips;
        double utilization = Math.min(1.0, Math.max(0.0, mips / host.getTotalMips()));

        double power = host.getPowerModel().getPower(utilization);
        double energyConsumed = power * executionTime;
        double availableGreenPower = datacenter.getGreenPower();
        double greenPowerConsumed = Math.min(energyConsumed, availableGreenPower);

        return greenPowerConsumed;
    }
}