package cloud.Test;

import cloud.ExecuteData.RandomScheduleTest;

import java.util.List;

import static cloud.Constants.Iteration;


/**
 * @author ml969$
 * @date 20/02/2025$
 * @description TODO
 */
public class test_random {
    public static void main(String[] args) throws Exception {
        double total = 0;
        RandomScheduleTest randomScheduleTest = new RandomScheduleTest();
        List<Double> randomPowerList = randomScheduleTest.execute();
        for (int i = 0; i < randomPowerList.size(); i++) {
           total += randomPowerList.get(i);
        }
        System.out.println("Avg VM power consumption is " + total / Iteration);

    }

}
