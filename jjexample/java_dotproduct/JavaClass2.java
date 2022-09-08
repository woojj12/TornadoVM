import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import uk.ac.manchester.tornado.runtime.tasks.ReduceTaskSchedule;

import uk.ac.manchester.tornado.api.common.TaskPackage;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;

class JavaClass2 {
    static TaskSchedule _stTaskSchedule = new TaskSchedule("s0");
    static float[] oldA = new float[0];
    static float[] oldB = new float[0];
    static float[] output = new float[1];

    static void targetFunc(float[] a, float[] b, @Reduce float[] c) {
        c[0] = 0;
        for (@Parallel int i = 0; i < a.length; i++) {
            c[0] += a[i] * b[i];
        }
    }

    static final String dirPath = System.getProperty("user.dir") + "/OpenCLSource";
    static final String filePathBase = dirPath + "/dotproduct_";

    static String createFileName(int matSize) {
        return Integer.toString(matSize) + ".cl";
    }

    static String compileAndCreateOpenCLSource(int vectorSize) {
        File dir = new File(dirPath);
        dir.mkdir();
        String filePath = filePathBase + createFileName(vectorSize);
        File file = new File(filePath);
        if (file.exists() == false) {
            long GraalTime = 0;
            long OpenCLTime = 0;
            long DeviceTime = 0;
            
            float[] tempA = new float[vectorSize];
            float[] tempB = new float[vectorSize];

            TaskSchedule stTempTaskSchedule = new TaskSchedule("s0");
            stTempTaskSchedule.task("t0", JavaClass2::targetFunc, tempA, tempB, output);
            stTempTaskSchedule.streamIn(tempA, tempB);
            stTempTaskSchedule.streamOut(output);

            TornadoOptions.PRINT_SOURCE = true;
            TornadoOptions.PRINT_SOURCE_DIRECTORY = filePath;
            stTempTaskSchedule.execute();
            TornadoOptions.PRINT_SOURCE = false;

            System.out.println(stTempTaskSchedule.getDevice().toString());

            /*
            System.out.printf("global Work: ");
            long[] globalWorkGrid = stTempTaskSchedule.getTask("t0").meta().getGlobalWork();
            for (long globalWork : globalWorkGrid) {
                System.out.printf("%d ", globalWork);
            }
            System.out.printf("\n");

            System.out.printf("local Work: ");
            long[] localWorkGrid = stTempTaskSchedule.getTask("t0").meta().getLocalWork();
            for (long localWork : localWorkGrid) {
                System.out.printf("%d ", localWork);
            }
            System.out.printf("\n");

            System.out.println(((TaskMetaData)stTempTaskSchedule.getTask("t0").meta()).getDomain().toString());

            Access[] accessArr = ((TaskMetaData)stTempTaskSchedule.getTask("t0").meta()).getArgumentsAccess();

            for (int i = 0; i < accessArr.length; i++) {
                System.out.println(accessArr[i].position);
            }

            System.out.println(((TaskMetaData)stTempTaskSchedule.getTask("t0").meta()).getArgumentsAccess().toString());

             */
            if (stTempTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 128)
            {
                stTempTaskSchedule.getDevice().reset();
                stTempTaskSchedule.warmup();
            }
            GraalTime += stTempTaskSchedule.getTornadoCompilerTime();
            OpenCLTime += stTempTaskSchedule.getDriverInstallTime();
            DeviceTime += stTempTaskSchedule.getByteCodeTime();

            System.out.printf("%d %d %d\n", GraalTime, OpenCLTime, DeviceTime);

            return stTempTaskSchedule.getTask("t0").getTaskName();
        }
        else {
            String taskName = "";
            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(filePath));
                while (taskName.isEmpty() == true) {
                    String str = fileReader.readLine();
                    if (str.contains("__kernel") == true) {
                        taskName = str.substring("__kernel void ".length(), str.indexOf("("));
                    }
                }
            }
            catch (Exception e) {
                System.out.println(e.toString());
            }
            return taskName;
        }
    }

    static long minHeapSize = 0;

    static void createTornadoVMTask(int vectorSize) {
        String taskName = compileAndCreateOpenCLSource(vectorSize).replace("$", "_");
        String filePath = filePathBase + createFileName(vectorSize);
        //System.out.println(filePath);

        _stTaskSchedule = new TaskSchedule("s0");
        Object[] params = {oldA, oldB, output};
        Access[] access = {Access.READ, Access.READ, Access.READ_WRITE};
        int[] dim = {vectorSize};
        //TaskPackage stTaskPackage = new TaskPackage("t0", JavaClass2::targetFunc, oldA, oldB, output);


        float[] tempArray = new float[vectorSize];
        for (int i = 0; i < vectorSize; i++) {
            tempArray[i] = (float)i;
        }
        oldA = tempArray.clone();
        oldB = tempArray.clone();

        ReduceTaskSchedule.USE_PREBUILT = true;
        ReduceTaskSchedule.PREBUILT_FILE = filePath;
        ReduceTaskSchedule.ENTRY_POINT = taskName;
        ReduceTaskSchedule.ACCESS = access;
        ReduceTaskSchedule.DIM = dim;
        _stTaskSchedule.streamIn(oldA, oldB);
        _stTaskSchedule.streamOut(output);
        _stTaskSchedule.task("t0", JavaClass2::targetFunc, oldA, oldB, output);
        /*
        _stTaskSchedule.prebuiltTask("t0", taskName, filePath,
                params,
                access,
                TornadoRuntime.getTornadoRuntime().getDefaultDevice(),
                dim
        );
         */
        //_stTaskSchedule.someFunction("t0", JavaClass2::targetFunc, oldA, oldB, output);


        _stTaskSchedule.execute();

        long tempMinHeapSize = 4;
        tempMinHeapSize *= vectorSize * 2;
    
        minHeapSize = tempMinHeapSize + 1024;
        //System.out.printf("minHeapSize %d\n", minHeapSize);
    }

    static TaskSchedule getTaskSchedule(float[] a, float[] b) {
        if (isInit == false) {
            createTornadoVMTask(a.length);
            //System.out.println("create done");
            isInit = true;
        }

        _stTaskSchedule.updateReference(oldA, a);
        _stTaskSchedule.updateReference(oldB, b);
        if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
        {
            _stTaskSchedule.getDevice().reset();
        }
        else if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 50) {
            _stTaskSchedule.getDevice().reset();
        }

        oldA = a;
        oldB = b;

        return _stTaskSchedule;
    }

    static boolean isInit = false;

    public static void doTornado(float[] a, float[] b) {
        TaskSchedule stTaskSchedule = getTaskSchedule(a, b);

        stTaskSchedule.execute();
    }
    public static void main(String[] args) {
        final int vectorSize = Integer.parseInt(args[0]);

        float[] tempArray = new float[vectorSize];
        for (int i = 0; i < vectorSize; i++) {
            tempArray[i] = (float)i;
        }

        int runCnt = 1 + 200;
        long accum = 0;
        boolean initRun = true;
        float[] c = new float[1];
        for (int i = 0; i < runCnt; i++) {
            float[] a = tempArray.clone();
            float[] b = tempArray.clone();

            long startTime = System.nanoTime();
            doTornado(a, b);
            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            if (initRun == true) {
                initRun = false;
            }
            else {
                accum += elapsed/1000;
                //targetFunc(a, b, c);
                //System.out.printf("%d %d %f %f\n", vectorSize, elapsed, output[0], c[0]);
            }
        }
        System.out.printf("%d %d\n", vectorSize, accum/(runCnt-1));
    }
}
