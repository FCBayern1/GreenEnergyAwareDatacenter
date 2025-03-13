package cloud;

import cloud.PowerModel.PowerModelDL360G7;
import cloud.PowerModel.PowerModelDL360Gen9;
import cloud.PowerModel.PowerModelML110G5;
import org.cloudbus.cloudsim.power.models.PowerModel;

public class Constants {
    public static final boolean ENABLE_OUTPUT = true;
    public static final boolean OUTPUT_CSV = false;
    public static final double SCHEDULING_INTERVAL = 300.0D;
    public static final double SIMULATION_LIMIT = 86400.0D;
    public static final int CLOUDLET_LENGTH = 216000000;
    public static final int CLOUDLET_PES = 1;
    public static final int VM_TYPES = 4;

    public static final int VM_BW = 100000;
    public static final int VM_SIZE = 2500;

    public static final String[] HOST_TYPES = new String[]{"G5", "G7", "G9"};

    public static final int HOST_BW = 1000000;
    public static final int HOST_STORAGE = 1000000;
    public static final PowerModel[] HOST_POWER = new PowerModel[]{new PowerModelML110G5(), new PowerModelDL360G7(), new PowerModelDL360Gen9()};
    //其余实验用到的设置
//    public static final int[] VM_MIPS = new int[]{2500, 2000, 1000, 500, 250};
//    public static final int[] VM_PES = new int[]{1, 1, 1, 1, 1};
//    public static final int[] VM_RAM = new int[]{2048, 2048, 1024, 1024, 512};
//    public static final int[] HOST_MIPS = new int[]{2660, 3067, 2300};
//    public static final int[] HOST_PES = new int[]{2, 12, 36};
//   public static final int[] HOST_RAM = new int[]{4 * 1024, 16 * 1024, 64 * 1024};

    //CPUUtilizaitonCompare实验用到的设置
    public static final int[] VM_MIPS = new int[]{3500, 3000, 2500, 2000, 1500};
    public static final int[] VM_PES = new int[]{1, 1, 1, 1, 1};
    public static final int[] VM_RAM = new int[]{2048, 2048, 1024, 1024, 512};
    public static final int[] HOST_MIPS = new int[]{6000, 8000, 10000};
    public static final int[] HOST_PES = new int[]{2, 4, 8};
    public static final int[] HOST_RAM = new int[]{65536, 65536, 65536};

    public Constants() {
    }

    public static final int DC_NUM = 10;

    public static final int CREATE_VM = 10;

    public static final int CREATE_VM_ACK = 10;

    public static final int SUBMIT_CLOUDLET = 10;

    public static final int CLOUDSIM_RESTART = 102;


    public static final int Iteration = 100;

    public static final int NUMBER_OF_HOSTS = 50;

    public static final int terminateTime = 700;

    public static final int outputTime = 600;


    public static String inputFolder = "/home/ml969/Downloads/joshua_cloudsim/src/main/resources/datas/50";

    //pso
    public static final int POPULATION_SIZE = 300; // Number of Particles.
    public static final double W = 0.9;
    public static final double C = 2.0;
}
