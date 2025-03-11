package cloud.Hosts;

/**
 * @author ml969$
 * @date 28/02/2025$
 * @description TODO
 */
import java.util.List;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

public class MyPowerHost extends PowerHost {
    private PowerModel powerModel;
    private double greenPower;

    public MyPowerHost(int id, RamProvisioner ramProvisioner, BwProvisioner bwProvisioner,
                       long storage, List<? extends Pe> peList, VmScheduler vmScheduler,
                       PowerModel powerModel, double initialGreenPower) {
        super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler,powerModel);
        this.greenPower = initialGreenPower;
    }

    public double getGreenPower() {
        return this.greenPower;
    }

    public void setGreenPower(double greenPower) {
        this.greenPower = greenPower;
    }

    /**
     * @param powerNeeded The needed amount of power consumption
     * @return 返回实际消耗的能源
     */
    private double consumePower(double powerNeeded) {
        if (this.greenPower >= powerNeeded) {
            this.greenPower -= powerNeeded;
            return powerNeeded;
        } else {
            double greenUsed = this.greenPower;
            double brownNeeded = powerNeeded - greenUsed;
            this.greenPower = 0;
            return powerNeeded;  // 总消耗不变，但绿色部分用尽
        }
    }

    /**
     * **计算当前主机的功耗**
     */

    public double getPower() {
        double utilization = this.getUtilizationOfCpu();  // 获取 CPU 利用率
        double powerNeeded = this.getPower(utilization);  // 根据功率模型计算总功耗
        return consumePower(powerNeeded);  // **优先消耗绿色能源**
    }

    /**
     * **根据 CPU 利用率计算功耗**
     * @param utilization 当前 CPU 负载
     * @return 计算出的功耗
     */
    protected double getPower(double utilization) {
        double power = 0.0;

        try {
            power = this.getPowerModel().getPower(utilization);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        return power;
    }

    /**
     * **计算线性插值的能耗**
     * @param fromUtilization 之前的 CPU 利用率
     * @param toUtilization 当前的 CPU 利用率
     * @param time 时间段
     * @return 计算出的能耗
     */
    public double getEnergyLinearInterpolation(double fromUtilization, double toUtilization, double time) {
        if (fromUtilization == 0.0) {
            return 0.0;
        } else {
            double fromPower = this.getPower(fromUtilization);
            double toPower = this.getPower(toUtilization);
            double totalEnergy = (fromPower + (toPower - fromPower) / 2.0) * time;
            return consumePower(totalEnergy);  // **优先使用绿色能源**
        }
    }

    /**
     * **获取最大功耗**
     */
    public double getMaxPower() {
        double power = 0.0;

        try {
            power = this.getPowerModel().getPower(1.0);  // 100% CPU 负载时的功耗
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        return power;
    }

    /**
     * **设置功率模型**
     */
    protected void setPowerModel(PowerModel powerModel) {
        this.powerModel = powerModel;
    }

    /**
     * **获取功率模型**
     */
    public PowerModel getPowerModel() {
        return this.powerModel;
    }
}