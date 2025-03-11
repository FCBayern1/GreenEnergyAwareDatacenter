package cloud.brokers;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.power.PowerDatacenterBroker;
import java.util.Random;

import java.util.ArrayList;
import java.util.List;

import static cloud.Constants.CLOUDSIM_RESTART;
import static cloud.Constants.CREATE_VM_ACK;


/**
 * @author ml969$
 * @date 04/03/2025$
 * @description TODO
 */
public class RandomDatacenterBroker extends PowerDatacenterBroker {

    int requestedVms = 0;
    private Random random;

    /**
     * Instantiates a new PowerDatacenterBroker.
     *
     * @param name the name of the broker
     * @throws Exception the exception
     */
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
            case CLOUDSIM_RESTART:
                startEntity();
                break;
            default:
                if (ev == null) {
                    Log.printConcatLine(getName(), ".processOtherEvent(): Error - an event is null in DatacenterBroker.");
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
        int randomDatacenterId = selectRandomDatacenter();
        if (result == CloudSimTags.TRUE) {
            getVmsToDatacentersMap().put(vmId, randomDatacenterId);
            getVmsCreatedList().add(VmList.getById(getVmList(), vmId));
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": VM #", vmId,
                    " has been randomly created in Datacenter #", randomDatacenterId, ", Host #",
                    VmList.getById(getVmsCreatedList(), vmId).getHost().getId());
        } else {
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Creation of VM #", vmId,
                    " failed in Datacenter #", randomDatacenterId);
        }

        incrementVmsAcks();
        int createsize = getVmsCreatedList().size();
        int vmsize = getVmList().size();
        int destroysize = getVmsDestroyed();
        // all the requested VMs have been created
        if (getVmsCreatedList().size() > 0 && getVmsCreatedList().size() <= getVmList().size()) {
            submitCloudlets();
        } else {
            // all the acks received, but some VMs were not created
            if (getVmsRequested() == getVmsAcks()) {
                // find id of the next datacenter that has not been tried
                for (int nextDatacenterId : getDatacenterIdsList()) {
                    if (!getDatacenterRequestedIdsList().contains(nextDatacenterId)) {
                        createVmsInDatacenter(nextDatacenterId);
                        return;
                    }
                }

                // all datacenters already queried
                if (getVmsCreatedList().size() > 0) { // if some vm were created
                    submitCloudlets();
                } else { // no vms created. abort
                    Log.printLine(CloudSim.clock() + ": " + getName()
                            + ": none of the required VMs could be created. Aborting");
                    finishExecution();
                }
            }
        }
    }

    @Override
    protected void createVmsInDatacenter(int datacenterId) {
        int randomDatacenterId = selectRandomDatacenter();
        String datacenterName = CloudSim.getEntityName(randomDatacenterId);

        Vm vm = getVmList().get(requestedVms);
        if (!getVmsToDatacentersMap().containsKey(vm.getId()) && requestedVms < getVmList().size() - 1) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + vm.getId()
                    + " randomly in " + datacenterName);
            sendNow(randomDatacenterId, CREATE_VM_ACK, vm);
            requestedVms++;
        }
        getDatacenterRequestedIdsList().add(randomDatacenterId);

        setVmsRequested(requestedVms);
        setVmsAcks(0);
    }


    @Override
    protected void submitCloudlets() {
        int vmIndex = 0;
        List<Cloudlet> successfullySubmitted = new ArrayList<Cloudlet>();
        List<Vm> vms = getVmsCreatedList();
        Cloudlet cloudlet = getCloudletList().get(0);
        Vm vm;
        // if user didn't bind this cloudlet and it has not been executed yet
        if (cloudlet.getVmId() == -1) {
            vm = getVmsCreatedList().get(vmIndex);
        } else { // submit to the specific vm
            vm = VmList.getById(getVmsCreatedList(), cloudlet.getVmId());
            if (vm == null) { // vm was not created
                if (!Log.isDisabled()) {
                    Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Postponing execution of cloudlet ",
                            cloudlet.getCloudletId(), ": Found VM not available");
                }
            }
        }

        if (!Log.isDisabled()) {
            Log.printConcatLine(CloudSim.clock(), ": ", getName(), ": Sending cloudlet ",
                    cloudlet.getCloudletId(), " to VM #", vm.getId());
        }

        cloudlet.setVmId(vm.getId());
        sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
        cloudletsSubmitted++;
        vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
        getCloudletSubmittedList().add(cloudlet);
        successfullySubmitted.add(cloudlet);


        // remove submitted cloudlets from waiting list
        getCloudletList().removeAll(successfullySubmitted);
    }

    private int selectRandomDatacenter(){
        int size = getDatacenterIdsList().size();
        if(getDatacenterIdsList().isEmpty()){
            return -1;
        }
        int index = random.nextInt(size);
        return getDatacenterIdsList().get(index);
    }

}

