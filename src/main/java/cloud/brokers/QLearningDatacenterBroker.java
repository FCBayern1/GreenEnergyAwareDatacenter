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
import java.util.List;
import java.util.Random;

import static cloud.Constants.CREATE_VM_ACK;

/**
 * @author ml969
 * @date 06/03/2025
 * @description A Q-Learning based DatacenterBroker to maximize green power consumption.
 */
public class QLearningDatacenterBroker extends PowerDatacenterBroker {

    private double[][] qTable; // Q 表: [Cloudlet ID][Datacenter ID]
    private Random random;
    private double alpha = 0.1; // 学习率
    private double gamma = 0.9; // 折扣因子
    private double epsilon = 0.1; // ε-贪婪策略的探索概率
    private int requestedVms = 0;

    public QLearningDatacenterBroker(String name) throws Exception {
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
            // 如果仍有 VM 未创建，尝试其他数据中心
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
        while (requestedVms < getVmList().size()) {
            Vm vm = getVmList().get(requestedVms);
            if (!getVmsToDatacentersMap().containsKey(vm.getId())) {
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + vm.getId()
                        + " in Datacenter #" + datacenterId);
                sendNow(datacenterId, CREATE_VM_ACK, vm);
                requestedVms++;
            }
            getDatacenterRequestedIdsList().add(datacenterId);
        }
        setVmsRequested(requestedVms);
        setVmsAcks(0);
    }
    @Override
    protected void submitCloudlets() {
        if (qTable == null) {
            int cloudletCount = getCloudletList().size();
            int datacenterCount = getDatacenterIdsList().size();
            if (cloudletCount > 0 && datacenterCount > 0) {
                qTable = new double[cloudletCount][datacenterCount];
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Initialized Q-Table with "
                        + cloudletCount + " Cloudlets and " + datacenterCount + " Datacenters.");
            } else {
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Error - Cloudlet or Datacenter list is empty.");
                return;
            }
        }

        List<Cloudlet> successfullySubmitted = new ArrayList<>();
        int vmIndex = 0;

        for (Cloudlet cloudlet : getCloudletList()) {
            Vm vm = getVmsCreatedList().get(vmIndex);
            int state = cloudlet.getCloudletId() % qTable.length; // 简单状态：Cloudlet ID
            int actionIndex = selectAction(state); // 数据中心索引
            int datacenterId = getDatacenterIdsList().get(actionIndex);

            // 绑定 Cloudlet 到 VM
            cloudlet.setVmId(vm.getId());
            getVmsToDatacentersMap().put(vm.getId(), datacenterId);

            // 提交 Cloudlet
            sendNow(datacenterId, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Sending cloudlet #",
                    cloudlet.getCloudletId(), " to VM #", vm.getId(), " in Datacenter #", datacenterId);

            // 获取奖励并更新 Q 表
            double reward = getGreenPowerConsumption(datacenterId, cloudlet);
            updateQTable(state, actionIndex, reward, datacenterId);

            cloudletsSubmitted++;
            vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
            getCloudletSubmittedList().add(cloudlet);
            successfullySubmitted.add(cloudlet);
        }
        getCloudletList().removeAll(successfullySubmitted);
    }

    // ε-贪婪策略选择动作
    private int selectAction(int state) {
        if (random.nextDouble() < epsilon) {
            return random.nextInt(getDatacenterIdsList().size());
        } else {
            int bestAction = 0;
            double maxQ = qTable[state][0];
            for (int i = 1; i < getDatacenterIdsList().size(); i++) {
                if (qTable[state][i] > maxQ) {
                    maxQ = qTable[state][i];
                    bestAction = i;
                }
            }
            return bestAction;
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

        double mips = vm.getMips() * vm.getNumberOfPes();           // VM 的总计算能力
        double cloudletLength = cloudlet.getCloudletLength();       // Cloudlet 的计算需求 (MI)
        double executionTime = cloudletLength / mips;               // 执行时间（秒）
        double hostTotalMips = host.getTotalMips();                 // Host 的总 MIPS

        // 计算利用率，确保不超过 Host 容量，限制在 [0, 1]
        double requestedMips = Math.min(mips, cloudlet.getNumberOfPes() * vm.getMips()); // Cloudlet 实际请求的 MIPS
        double utilization = Math.min(1.0, Math.max(0.0, requestedMips / hostTotalMips));

        // 获取功耗
        double power;
        try {
            power = host.getPowerModel().getPower(utilization);     // 每秒功耗 (W)
        } catch (IllegalArgumentException e) {
            // 异常保护：如果利用率仍非法，强制调整为边界值
            power = utilization < 0 ? host.getPowerModel().getPower(0.0) : host.getPowerModel().getPower(1.0);
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Warning - Adjusted utilization for Cloudlet #" +
                    cloudlet.getCloudletId() + " from " + utilization + " to " + (utilization < 0 ? 0.0 : 1.0));
        }

        double energyConsumed = power * executionTime;              // 总能源需求 (W·s 或 J)
        double availableGreenPower = datacenter.getGreenPower();    // 可用绿色能源 (W)
        double greenPowerConsumed = Math.min(energyConsumed, availableGreenPower); // 绿色能源消耗

        Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Cloudlet #", cloudlet.getCloudletId(),
                " estimated green power consumption in Datacenter #", datacenterId, ": ", greenPowerConsumed, " W",
                " (Utilization: ", utilization, ", Execution Time: ", executionTime, " s)");

        return greenPowerConsumed;
    }

    private void updateQTable(int state, int action, double reward, int datacenterId) {
        int nextState = state;

        double maxNextQ = 0;
        for (int i = 0; i < getDatacenterIdsList().size(); i++) {
            maxNextQ = Math.max(maxNextQ, qTable[nextState][i]);
        }

        qTable[state][action] += alpha * (reward + gamma * maxNextQ - qTable[state][action]);

        // 可选：记录更新日志
        Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Updated Q(s=", state, ", a=", action,
                ") = ", qTable[state][action], " with reward=", reward);
    }
}