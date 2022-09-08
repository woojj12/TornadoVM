import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;

class JavaClass {
    static TaskSchedule _stTaskSchedule = new TaskSchedule("s0");
    static Matrix2DFloat oldA;
    static Matrix2DFloat oldB;
    static Matrix2DFloat oldC;

    static void targetFunc(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C) {
        for (@Parallel int row = 0; row < C.M(); row++) {
            for (@Parallel int col = 0; col < C.N(); col++) {
                float sum = 0.0f;
                for (int k = 0; k < B.M(); k++) {
                    sum += A.get(row, k) * B.get(k, col);
                }
                C.set(row, col, sum);
            }
        }
    }

    static final String dirPath = System.getProperty("user.dir") + "/OpenCLSource";
    static final String filePathBase = dirPath + "/matmul_";

    static String createFileName(int matSize) {
        return Integer.toString(matSize) + ".cl";
    }

    static String compileAndCreateOpenCLSource(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C) {
        File dir = new File(dirPath);
        dir.mkdir();
        String filePath = filePathBase + createFileName(A.M());
        File file = new File(filePath);
        if (file.exists() == false) {
            long GraalTime = 0;
            long OpenCLTime = 0;
            long DeviceTime = 0;

            TaskSchedule stTempTaskSchedule = new TaskSchedule("s0");
            stTempTaskSchedule.task("t0", JavaClass::targetFunc, A, B, C);
            stTempTaskSchedule.streamIn(A, B);
            stTempTaskSchedule.streamOut(C);

            TornadoOptions.PRINT_SOURCE = true;
            TornadoOptions.PRINT_SOURCE_DIRECTORY = filePath;
            stTempTaskSchedule.execute();
            TornadoOptions.PRINT_SOURCE = false;

            System.out.println(stTempTaskSchedule.getDevice().toString());

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

    static void createTornadoVMTask(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C) {
        String taskName = compileAndCreateOpenCLSource(A, B, C).replace("$", "_");
        final int matSize = A.M();
        String filePath = filePathBase + createFileName(matSize);
        //System.out.println(filePath);

        _stTaskSchedule = new TaskSchedule("s0");
        Object[] params = {A, B, C};
        Access[] access = {Access.NONE, Access.READ, Access.READ_WRITE};
        int[] dim = {matSize, matSize};
        _stTaskSchedule.prebuiltTask("t0", taskName, filePath,
                params,
                access,
                TornadoRuntime.getTornadoRuntime().getDefaultDevice(),
                dim
        );
        _stTaskSchedule.streamIn(A, B);
        _stTaskSchedule.streamOut(C);
        _stTaskSchedule.execute();

        long tempMinHeapSize = 4;
        tempMinHeapSize *= matSize * matSize * 3;
    
        minHeapSize = tempMinHeapSize + 4096;
        //System.out.printf("minHeapSize %d\n", minHeapSize);
    }

    static TaskSchedule getTaskSchedule(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C) {
        if (isInit == false) {
            createTornadoVMTask(A, B, C);
            //System.out.println("create done");
            isInit = true;
        }
        else {
            _stTaskSchedule.updateReference(oldA, A);
            _stTaskSchedule.updateReference(oldB, B);
            _stTaskSchedule.updateReference(oldC, C);
        }
        if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
        {
            _stTaskSchedule.getDevice().reset();
        }
        else if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 50) {
            _stTaskSchedule.getDevice().reset();
        }

        oldA = A;
        oldB = B;
        oldC = C;

        return _stTaskSchedule;
    }

    static void resetTornadoVMHeap() {
        _stTaskSchedule.getDevice().reset();
    }

    static boolean isInit = false;

    public static void doTornado(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C) {
        TaskSchedule stTaskSchedule = getTaskSchedule(A, B, C);

        stTaskSchedule.execute();
    }
    static void fillArray(float[] arr) {
        Random rd = new Random();
        for (int i = 0; i < arr.length; i++) {
            arr[i] = rd.nextFloat();
        }
    }
    public static void main(String[] args) {
        final int matSize = Integer.parseInt(args[0]);

        float[] tempArray = new float[matSize * matSize];
        fillArray(tempArray);

        Matrix2DFloat A = new Matrix2DFloat(matSize, matSize, tempArray.clone());
        Matrix2DFloat B = new Matrix2DFloat(matSize, matSize, tempArray.clone());
        Matrix2DFloat C = new Matrix2DFloat(matSize, matSize);

        doTornado(A, B, C);

        int runCnt = 50;
        long accum = 0;
        try {
            for (int i = 0; i < runCnt; i++) {
                A = new Matrix2DFloat(matSize, matSize, tempArray.clone());
                B = new Matrix2DFloat(matSize, matSize, tempArray.clone());
                C = new Matrix2DFloat(matSize, matSize);

                long startTime = 0;
                long endTime = 0;
                try {
                    startTime = System.nanoTime();
                    doTornado(A, B, C);
                    endTime = System.nanoTime();
                }
                catch(Exception e) {
                    System.out.println(e);
                    resetTornadoVMHeap();
                    startTime = System.nanoTime();
                    doTornado(A, B, C);
                    endTime = System.nanoTime();
                }
                long elapsed = endTime - startTime;
                accum += elapsed;
                System.out.printf("%d %d\n", matSize, elapsed);
                System.out.flush();
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }


        //System.out.printf("%d %d\n", matSize, accum/(runCnt - 1));

        //targetFunc(A, B, C);
    }
}
