package cloud.Test;
import java.util.List;
import static cloud.Constants.Iteration;
    import cloud.ExecuteData.Cross_Datacenter_Test;


/**
 * @author ml969$
 * @date 06/03/2025$
 * @description TODO
 */
public class Test_CrossDC {
    public static void main(String[] args) throws Exception {
        Cross_Datacenter_Test test = new Cross_Datacenter_Test();
        Double maxGreenEnergyConsumption = test.execute();
        System.out.println("Max Green Energy Consumption: "+ maxGreenEnergyConsumption);


    }


}
