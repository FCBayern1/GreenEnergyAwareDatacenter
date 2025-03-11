package cloud.Test;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

public class Custom_Data_Broker_Test {
    public static void main(String[] args) {
        try {
            int numUsers = 1;  // 模拟 1 个用户
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;  // 关闭日志跟踪

            // 初始化 CloudSim
            CloudSim.init(numUsers, calendar, traceFlag);

            // 创建多个数据中心
            Datacenter datacenter1 = createDatacenter("Datacenter_1");
            Datacenter datacenter2 = createDatacenter("Datacenter_2");

            // 创建 Broker（任务调度代理）
            DatacenterBroker broker = new DatacenterBroker("Broker");

            // 创建虚拟机（VM），分别放到不同数据中心
            List<Vm> vmList = new ArrayList<>();
            int vmId = 0;
            int numVms = 4;
            for (int i = 0; i < numVms; i++) {
                int datacenterId = (i % 2 == 0) ? datacenter1.getId() : datacenter2.getId();
                Vm vm = new Vm(vmId++, broker.getId(), 1000, 1, 2048, 1000, 10000, "Xen",
                        new CloudletSchedulerTimeShared());
                vmList.add(vm);
            }
            broker.submitVmList(vmList);

            // 创建任务（Cloudlet），并分配到 Broker 进行调度
            List<Cloudlet> cloudletList = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Cloudlet cloudlet = new Cloudlet(i, 4000, 1, 300, 300,
                        new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                cloudlet.setUserId(broker.getId());
                cloudletList.add(cloudlet);
            }
            broker.submitCloudletList(cloudletList);

            // **自定义调度逻辑：在数据中心之间调度任务**
            for (Cloudlet cloudlet : cloudletList) {
                Vm selectedVm = selectBestVm(cloudlet, vmList);
                if (selectedVm != null) {
                    broker.bindCloudletToVm(cloudlet.getCloudletId(), selectedVm.getId());
                }
            }

            // 启动 CloudSim 仿真
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // 输出仿真结果
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
            printResults(finishedCloudlets);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // **创建数据中心**
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        int mips = 10000;
        int ram = 16384;
        int storage = 1000000;
        int bw = 100000;

        for (int i = 0; i < 2; i++) {  // 每个数据中心创建 2 台服务器
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));

            hostList.add(new Host(i, new RamProvisionerSimple(ram),
                    new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList)));
        }

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, 10.0, 3.0, 0.05, 0.1, 0.1);

        try {
            return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // **自定义任务调度策略：在数据中心之间选择最佳 VM**
    private static Vm selectBestVm(Cloudlet cloudlet, List<Vm> vmList) {
        Vm bestVm = null;
        double minLoad = Double.MAX_VALUE;

        for (Vm vm : vmList) {
            double load = vm.getCurrentRequestedTotalMips();  // 获取当前 VM 负载
            if (load < minLoad) {
                minLoad = load;
                bestVm = vm;
            }
        }
        return bestVm;
    }

    // **输出仿真结果**
    private static void printResults(List<Cloudlet> cloudletList) {
        System.out.println("Cloudlet Execution Results:");
        System.out.println("==========================================");
        for (Cloudlet cloudlet : cloudletList) {
            System.out.printf("Cloudlet ID: %d | VM ID: %d | Datacenter ID: %d | Execution Time: %.2f%n",
                    cloudlet.getCloudletId(),
                    cloudlet.getVmId(),
                    cloudlet.getResourceId(),
                    cloudlet.getActualCPUTime());
        }
    }
}
