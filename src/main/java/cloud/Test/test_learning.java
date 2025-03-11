package cloud.Test;

import cloud.ExecuteData.LearningScheduleTest;

import java.util.List;

import static cloud.Constants.Iteration;

/**
 * @author ml969$
 * @date 18/02/2025$
 * @description TODO
 */
public class test_learning {
    public static void main(String[] args) throws Exception {
        double total = 0;
        LearningScheduleTest learningScheduleTest = new LearningScheduleTest();
//        learningScheduleTest.setLEARNING_GAMMA(0);
//        learningScheduleTest.setLEARNING_EPSILON(0);
        learningScheduleTest.setLEARNING_GAMMA(0.1);
        List<Double> learningPowerList = learningScheduleTest.execute();
        for (int i = 0; i < learningPowerList.size(); i++) {
            total += learningPowerList.get(i);
        }
        System.out.println(total / Iteration +"£££");

    }
}
