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
 * @description A Q-Learning based DatacenterBroker to maximise green power consumption.
 */

/*
vmList：存储待创建的虚拟机列表。
vmsCreatedList：存储已创建的虚拟机列表。
cloudletList：存储待提交的任务列表。
cloudletSubmittedList：存储已提交的任务列表。
cloudletReceivedList：存储已完成并返回的任务列表。
datacenterIdsList：存储数据中心 ID 列表。
datacenterRequestedIdsList：存储已请求过的资源的数据中心 ID。
vmsToDatacentersMap：映射虚拟机 ID 到数据中心 ID。
datacenterCharacteristicsList：存储数据中心的特性。
 */
public class QLearningDatacenterBroker extends PowerDatacenterBroker {

    private HashMap<String, double[]> qTable; // Q 表: [Cloudlet ID][Datacenter ID]
    private Random random;
    private double alpha = 0.1; // 学习率
    private double gamma = 0.9; // 折扣因子
    private double epsilon = 0.1; // ε-贪婪策略的探索概率
    private int requestedVms = 0;

    public QLearningDatacenterBroker(String name) throws Exception {
        super(name);
        this.random = new Random();
        this.qTable = new HashMap<>();
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

//    处理虚拟机创建的结果，从事件中提取dc id，vm id 和创建结果
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
            // 尝试其他数据中心
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

//    在指定的数据中心里创建虚拟机
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

    // 把一组cloudlets提交到cloudletList里面, 方便后续把他们分配给虚拟机
    @Override
    protected void submitCloudlets() {
        if (qTable == null) {
            qTable = new HashMap<>();
        }

        List<Cloudlet> cloudletList = getCloudletList();
        if (cloudletList.isEmpty()) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": No Cloudlets to submit.");
            return;
        }

        for (Cloudlet cloudlet : cloudletList) {
            // **Q-learning 选择最优 Datacenter**
            String state = getStateString();
            int actionIndex = selectAction(state);
            int datacenterId = getDatacenterIdsList().get(actionIndex);

            // **找到该 Datacenter 里的 VM**
            Vm selectedVm = null;
            for (Vm vm : getVmsCreatedList()) {
                if (getVmsToDatacentersMap().get(vm.getId()) == datacenterId) {
                    selectedVm = vm;
                    break;
                }
            }

            // **如果该 Datacenter 里没有 VM，选择默认 VM**
            if (selectedVm == null) {
                selectedVm = getVmsCreatedList().get(0); // 选第一个 VM，防止出错
                Log.printLine("⚠️ No VM found in Datacenter #" + datacenterId + ", using VM #" + selectedVm.getId());
            }

            // **绑定 Cloudlet 到这个 VM**
            cloudlet.setVmId(selectedVm.getId());
            sendNow(datacenterId, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Sending Cloudlet #"
                    + cloudlet.getCloudletId() + " to VM #" + selectedVm.getId() + " in Datacenter #" + datacenterId);

            // **更新 Q-learning**
            double reward = getGreenPowerConsumption(datacenterId, cloudlet);
            String newState = getStateString();
            updateQTable(state, actionIndex, reward, newState);

            decayEpsilon();
            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
        }

        getCloudletList().clear();
    }



    // ε-greedy action selection
    private int selectAction(String state) {
        if (!qTable.containsKey(state)) {
            qTable.put(state, new double[getDatacenterIdsList().size()]);
        }

        double[] qValues = qTable.get(state);

        if (random.nextDouble() < epsilon) {
            return random.nextInt(qValues.length);
        } else {
            int bestIndex = argMax(qValues);
            if (qValues[bestIndex] == 0) {
                return random.nextInt(qValues.length);
            }
            return bestIndex;
        }
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


    private void updateQTable(String state, int action, double reward, String newState) {
        if (!qTable.containsKey(newState)) {
            qTable.put(newState, new double[getDatacenterIdsList().size()]);
        }

        double[] qValues = qTable.get(state);
        double[] newQValues = qTable.get(newState);

        double maxNextQ = max(newQValues);
        qValues[action] += alpha * (reward + gamma * maxNextQ - qValues[action]);

        Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Updated Q(s=", state, ", a=", action,
                ") = ", qValues[action], " with reward=", reward);
    }

    private String getStateString() {
        StringBuilder state = new StringBuilder();
        for (Integer dcId : getDatacenterIdsList()) {
            PowerDatacenterRandom dc = (PowerDatacenterRandom) CloudSim.getEntity(dcId);
            state.append(dc.getGreenPower()).append("_");
        }
        return state.toString();
    }


    private void decayEpsilon() {
        if (epsilon > 0.01) {
            epsilon *= 0.995; // 逐渐降低探索率
        }
    }

    private int argMax(double[] values) {
        int bestIndex = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[bestIndex]) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double max(double[] values) {
        double maxValue = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        return maxValue;
    }
}