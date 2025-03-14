package cloud.datacenter;

import cloud.NewPowerAllocatePolicy;
import cloud.policy.VmAllocationAssignerRandom;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.power.PowerDatacenter;

import org.cloudbus.cloudsim.power.PowerHost;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cloud.Constants.*;

public class PowerDatacenterRandom extends PowerDatacenter {

    private double power; // Power Consumption of datacenter
    private boolean disableMigrations; // VM migration disable or not?
    private double cloudletSubmitted; // The last time submitted cloudlets were processed.
    private int migrationCount; // The VM migration count.
    private double currentTime; // Current time
    private VmAllocationAssignerRandom vmAllocationAssignerRandom; // Random allocation policy
    private Host targetHost; // Current VM target host
    private String currentcpu;
    private String historycpu;
    private double greenPower;
    public double getGreenPower() {
        return greenPower;
    }
    public void setGreenPower(double greenPower) {
        this.greenPower = greenPower;
    }

    public static List<Double> allpower = new ArrayList<>();

    public PowerDatacenterRandom(
            String name,
            DatacenterCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval, VmAllocationAssignerRandom vmAllocationAssignerRandom) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

        setPower(0.0);
        setDisableMigrations(false);
        setCloudletSubmitted(-1);
        setMigrationCount(0);
        this.vmAllocationAssignerRandom = vmAllocationAssignerRandom;
    }

    public PowerDatacenterRandom(
            String name,
            DatacenterCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval, VmAllocationAssignerRandom vmAllocationAssignerRandom, double greenPower) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

        setPower(0.0);
        this.greenPower = greenPower;
        setDisableMigrations(false);
        setCloudletSubmitted(-1);
        setMigrationCount(0);
        this.vmAllocationAssignerRandom = vmAllocationAssignerRandom;
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CREATE_VM_ACK:
                processVmCreate(ev, true);
                break;
            default:
                if (ev == null) {
                    Log.printConcatLine(getName(), ".processOtherEvent(): Error - an event is null in Datacenter.");
                }
                break;
        }
    }

    @Override
    protected void processVmCreate(SimEvent ev, boolean ack) {
        Vm vm = (Vm) ev.getData();
        targetHost = vmAllocationAssignerRandom.getVmAllocationHost(getHostList(), vm);
        boolean result = getVmAllocationPolicy().allocateHostForVm(vm, targetHost);
        if (!result) {
            NewPowerAllocatePolicy newPowerAllocatePolicy = (NewPowerAllocatePolicy) getVmAllocationPolicy();
            targetHost = newPowerAllocatePolicy.findHostForVm(vm);
            result = newPowerAllocatePolicy.allocateHostForVm(vm);
        }
        if (ack) {
            int[] data = new int[3];
            data[0] = getId();
            data[1] = vm.getId();

            if (result) {
                data[2] = CloudSimTags.TRUE;
            } else {
                data[2] = CloudSimTags.FALSE;
            }
            send(vm.getUserId(), CloudSim.getMinTimeBetweenEvents(), CREATE_VM_ACK, data);
        }

        if (result) {
            getVmList().add(vm);

            if (vm.isBeingInstantiated()) {
                vm.setBeingInstantiated(false);
            }

            vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
                    .getAllocatedMipsForVm(vm));
        }

    }
    @Override
    protected void updateCloudletProcessing() {
        currentTime = CloudSim.clock();

        // if some time passed since last processing
        if (currentTime > getLastProcessTime()) {
            System.out.print(currentTime + " ");

            double minTime = updateCloudetProcessingWithoutSchedulingFutureEventsForce();

            if (!isDisableMigrations()) {
                List<Map<String, Object>> migrationMap = getVmAllocationPolicy().optimizeAllocation(
                        getVmList());

                if (migrationMap != null) {
                    for (Map<String, Object> migrate : migrationMap) {
                        Vm vm = (Vm) migrate.get("vm");
                        PowerHost targetHost = (PowerHost) migrate.get("host");
                        PowerHost oldHost = (PowerHost) vm.getHost();

                        if (oldHost == null) {
                            Log.formatLine(
                                    "%.2f: Migration of VM #%d to Host #%d is started",
                                    currentTime,
                                    vm.getId(),
                                    targetHost.getId());
                        } else {
                            Log.formatLine(
                                    "%.2f: Migration of VM #%d from Host #%d to Host #%d is started",
                                    currentTime,
                                    vm.getId(),
                                    oldHost.getId(),
                                    targetHost.getId());
                        }

                        targetHost.addMigratingInVm(vm);
                        incrementMigrationCount();

                        send(
                                getId(),
                                vm.getRam() / ((double) targetHost.getBw() / (2 * 8000)),
                                CloudSimTags.VM_MIGRATE,
                                migrate);
                    }
                }
            }

            // schedules an event to the next time
            if (minTime != Double.MAX_VALUE) {
                CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_DATACENTER_EVENT));
                send(getId(), getSchedulingInterval(), CloudSimTags.VM_DATACENTER_EVENT);
            }

            setLastProcessTime(currentTime);
        }
    }

    protected double updateCloudetProcessingWithoutSchedulingFutureEvents() {
        if (CloudSim.clock() > getLastProcessTime()) {
            return updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
        return 0;
    }

    protected double updateCloudetProcessingWithoutSchedulingFutureEventsForce() {
        double currentTime = CloudSim.clock();
        double minTime = Double.MAX_VALUE;
        double timeDiff = currentTime - getLastProcessTime();
        double timeFrameDatacenterEnergy = 0.0;

        Log.formatLine("\n%.2f: Updating Cloudlet Processing...", currentTime);

        for (PowerHost host : this.<PowerHost>getHostList()) {
            double time = host.updateVmsProcessing(currentTime);
            if (time < minTime) {
                minTime = time;
            }
            Log.printLine("Host #" + host.getId() + " CPU Utilization: " + host.getUtilizationOfCpu());
        }

        if (timeDiff > 0) {
            Log.formatLine("\nEnergy consumption from %.2f to %.2f:", getLastProcessTime(), currentTime);
            for (PowerHost powerHost : this.<PowerHost>getHostList()) {
                double prevUtilization = powerHost.getPreviousUtilizationOfCpu();
                double currentUtilization = powerHost.getUtilizationOfCpu();
                double energyConsumed = powerHost.getEnergyLinearInterpolation(prevUtilization, currentUtilization, timeDiff);
                Log.formatLine("[Host #%d]: Energy requirement: %.2f W.sec (Utilization: %.2f)",
                        powerHost.getId(), energyConsumed, currentUtilization);
                if (this.getGreenPower() >= energyConsumed) {
                    Log.formatLine("%.2f: [Datacenter #%d] [Host #%d] Used GREEN energy: %.2f W (Remaining Green Energy: %.2f W.sec)",
                            currentTime, powerHost.getDatacenter().getId(), powerHost.getId(), energyConsumed, this.getGreenPower());
                    this.setGreenPower(this.getGreenPower() - energyConsumed);
                } else {
                    double brownEnergyUsed = energyConsumed - this.getGreenPower();
                    this.setGreenPower(0);
                    Log.formatLine("%.2f: [Datacenter id #%d] GREEN depleted! Using BROWN energy: %.2f W",
                            currentTime, this.getId(), brownEnergyUsed);
                }
                timeFrameDatacenterEnergy += energyConsumed;
            }
        }

        setPower(getPower() + timeFrameDatacenterEnergy);
        checkCloudletCompletion();
        if (currentTime > outputTime) {
            allpower.add(getPower());
        }
        setLastProcessTime(currentTime);
        return minTime;
    }
    @Override
    protected void processVmMigrate(SimEvent ev, boolean ack) {
        updateCloudetProcessingWithoutSchedulingFutureEvents();
        super.processVmMigrate(ev, ack);
        SimEvent event = CloudSim.findFirstDeferred(getId(), new PredicateType(CloudSimTags.VM_MIGRATE));
        if (event == null || event.eventTime() > CloudSim.clock()) {
            updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
    }

    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        updateCloudletProcessing();
        try {
            Cloudlet cl = (Cloudlet) ev.getData();
            if (cl.isFinished()) {
                String name = CloudSim.getEntityName(cl.getUserId());
                Log.printConcatLine(getName(), ": Warning - Cloudlet #", cl.getCloudletId(), " owned by ", name,
                        " is already completed/finished.");
                if (ack) {
                    int[] data = new int[3];
                    data[0] = getId();
                    data[1] = cl.getCloudletId();
                    data[2] = CloudSimTags.FALSE;
                    sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_SUBMIT_ACK, data);
                }
                sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
                return;
            }

            cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics().getCostPerBw());
            int userId = cl.getUserId();
            int vmId = cl.getVmId();
            double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

            Host host = getVmAllocationPolicy().getHost(vmId, userId);
            Vm vm = host.getVm(vmId, userId);
            CloudletScheduler scheduler = vm.getCloudletScheduler();
            double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime);

            if (estimatedFinishTime > 0.0 && !Double.isInfinite(estimatedFinishTime)) {
                estimatedFinishTime += fileTransferTime;
                Log.printLine("estimatedFinishTime for Cloudlet #" + cl.getCloudletId() + ": " + estimatedFinishTime);
                send(getId(), estimatedFinishTime, CloudSimTags.VM_DATACENTER_EVENT);
            } else {
                Log.printLine("Cloudlet #" + cl.getCloudletId() + " failed to schedule, estimatedFinishTime: " + estimatedFinishTime);
            }

            if (ack) {
                int[] data = new int[3];
                data[0] = getId();
                data[1] = cl.getCloudletId();
                data[2] = CloudSimTags.TRUE;
                sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_SUBMIT_ACK, data);
            }
        } catch (Exception e) {
            Log.printLine(getName() + ".processCloudletSubmit(): Exception error.");
            e.printStackTrace();
        }

        checkCloudletCompletion();
        setCloudletSubmitted(CloudSim.clock());
    }
    public double getPower() {
        return power;
    }

    protected void setPower(double power) {
        this.power = power;
    }
    protected boolean isInMigration() {
        boolean result = false;
        for (Vm vm : getVmList()) {
            if (vm.isInMigration()) {
                result = true;
                break;
            }
        }
        return result;
    }
    public boolean isDisableMigrations() {
        return disableMigrations;
    }
    public void setDisableMigrations(boolean disableMigrations) {
        this.disableMigrations = disableMigrations;
    }
    protected double getCloudletSubmitted() {
        return cloudletSubmitted;
    }
    protected void setCloudletSubmitted(double cloudletSubmitted) {
        this.cloudletSubmitted = cloudletSubmitted;
    }
    public int getMigrationCount() {
        return migrationCount;
    }
    protected void setMigrationCount(int migrationCount) {
        this.migrationCount = migrationCount;
    }
    protected void incrementMigrationCount() {
        setMigrationCount(getMigrationCount() + 1);
    }

}
