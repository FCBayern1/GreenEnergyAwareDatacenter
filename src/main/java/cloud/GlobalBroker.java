package cloud;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * @author ml969$
 * @date 25/02/2025$
 * @description TODO
 */
public class GlobalBroker extends SimEntity {
    private static final int CREATE_BROKER = 0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private DatacenterBroker broker;

    public GlobalBroker(String name) {
        super(name);
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CREATE_BROKER:
                setBroker(createBroker(super.getName()+"_"));

                //Create VMs and Cloudlets and send them to broker
                setVmList(createVM(getBroker().getId(), 5, 100)); //creating 5 vms
                setCloudletList(createCloudlet(getBroker().getId(), 10, 100)); // creating 10 cloudlets

                broker.submitVmList(getVmList());
                broker.submitCloudletList(getCloudletList());

                CloudSim.resumeSimulation();

                break;

            default:
                Log.printLine(getName() + ": unknown event type");
                break;
        }
    }

    @Override
    public void startEntity() {

    }

    @Override
    public void shutdownEntity() {
    }

    public List<Vm> getVmList() {
        return vmList;
    }

    protected void setVmList(List<Vm> vmList) {
        this.vmList = vmList;
    }

    public List<Cloudlet> getCloudletList() {
        return cloudletList;
    }

    protected void setCloudletList(List<Cloudlet> cloudletList) {
        this.cloudletList = cloudletList;
    }

    public DatacenterBroker getBroker() {
        return broker;
    }

    private static DatacenterBroker createBroker(String name){

        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker(name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    protected void setBroker(DatacenterBroker broker) {
        this.broker = broker;
    }

    private static List<Vm> createVM(int userId, int vms, int idShift) {
        //Creates a container to store VMs. This list is passed to the broker later
        LinkedList<Vm> list = new LinkedList<Vm>();

        //VM Parameters
        long size = 10000; //image size (MB)
        int ram = 512; //vm memory (MB)
        int mips = 250;
        long bw = 1000;
        int pesNumber = 1; //number of cpus
        String vmm = "Xen"; //VMM name

        //create VMs
        Vm[] vm = new Vm[vms];

        for(int i=0;i<vms;i++){
            vm[i] = new Vm(idShift + i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
            list.add(vm[i]);
        }

        return list;
    }

    private static List<Cloudlet> createCloudlet(int userId, int cloudlets, int idShift){
        // Creates a container to store Cloudlets
        LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

        //cloudlet parameters
        long length = 40000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        Cloudlet[] cloudlet = new Cloudlet[cloudlets];

        for(int i=0;i<cloudlets;i++){
            cloudlet[i] = new Cloudlet(idShift + i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            // setting the owner of these Cloudlets
            cloudlet[i].setUserId(userId);
            list.add(cloudlet[i]);
        }

        return list;
    }
}