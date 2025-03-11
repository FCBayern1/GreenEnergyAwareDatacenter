package cloud.Test;

import cloud.ExecuteData.LearningAndInitScheduleTest;
import java.util.List;

import static cloud.Constants.Iteration;

/**
 * @author ml969$
 * @date 18/02/2025$
 * @description TODO
 */
public class test_learningandinit {
    public static void main(String[] args) throws Exception {
        double total = 0;
        LearningAndInitScheduleTest learningAndInitScheduleTest = new LearningAndInitScheduleTest();
        List<Double> initPowerList = learningAndInitScheduleTest.execute();
        for (int i = 0; i < initPowerList.size(); i++) {
            total += initPowerList.get(i);
        }
        System.out.println("Avg VM power consumption is " + total / Iteration);

    }
}
