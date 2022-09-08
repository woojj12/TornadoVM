import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.convolveImage;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;

class JavaClass {
    static TaskSchedule _stTaskSchedule = new TaskSchedule("s0");
    static ImageFloat oldInput = new ImageFloat(0,0);
    static ImageFloat oldFilter = new ImageFloat(0,0);
    static ImageFloat oldOutput = new ImageFloat(0,0);

    static void targetFunc(ImageFloat input, ImageFloat filter, ImageFloat output) {
        convolveImage(input, filter, output);
    }

    static final String dirPath = System.getProperty("user.dir") + "/OpenCLSource";
    static final String filePathBase = dirPath + "/conv_";

    static String createFileName(int matSize) {
        return Integer.toString(matSize) + ".cl";
    }

    static String compileAndCreateOpenCLSource(int imageSize) {
        File dir = new File(dirPath);
        dir.mkdir();
        String filePath = filePathBase + createFileName(imageSize);
        File file = new File(filePath);
        if (file.exists() == false) {
            long GraalTime = 0;
            long OpenCLTime = 0;
            long DeviceTime = 0;
            
            ImageFloat tempInput = new ImageFloat(imageSize, imageSize);
            ImageFloat tempFilter = new ImageFloat(5, 5);
            ImageFloat tempOutput = new ImageFloat(imageSize, imageSize);

            TaskSchedule stTempTaskSchedule = new TaskSchedule("s0");
            stTempTaskSchedule.task("t0", JavaClass::targetFunc, tempInput, tempFilter, tempOutput);
            stTempTaskSchedule.streamIn(tempInput, tempFilter);
            stTempTaskSchedule.streamOut(tempOutput);

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

    static void createTornadoVMTask(int imageSize) {
        String taskName = compileAndCreateOpenCLSource(imageSize).replace("$", "_");
        String filePath = filePathBase + createFileName(imageSize);
        //System.out.println(filePath);

        _stTaskSchedule = new TaskSchedule("s0");
        Object[] params = {oldInput, oldFilter, oldOutput};
        Access[] access = {Access.READ, Access.READ, Access.READ_WRITE};
        int[] dim = {imageSize, imageSize};
        _stTaskSchedule.prebuiltTask("t0", taskName, filePath,
                params,
                access,
                TornadoRuntime.getTornadoRuntime().getDefaultDevice(),
                dim
        );
        _stTaskSchedule.streamIn(oldInput, oldFilter);
        _stTaskSchedule.streamOut(oldOutput);
        _stTaskSchedule.execute();

        long tempMinHeapSize = 4;
        tempMinHeapSize *= imageSize * imageSize * 2 + 5 * 5;
    
        minHeapSize = tempMinHeapSize + 1024;
        //System.out.printf("minHeapSize %d\n", minHeapSize);
    }

    static TaskSchedule getTaskSchedule(ImageFloat input, ImageFloat filter, ImageFloat output) {
        if (isInit == false) {
            createTornadoVMTask(input.X());
            //System.out.println("create done");
            isInit = true;
        }

        _stTaskSchedule.updateReference(oldInput, input);
        _stTaskSchedule.updateReference(oldFilter, filter);
        _stTaskSchedule.updateReference(oldOutput, output);
        if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
        {
            _stTaskSchedule.getDevice().reset();
        }
        else if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 50) {
            _stTaskSchedule.getDevice().reset();
        }

        oldInput = input;
        oldFilter = filter;
        oldOutput = output;

        return _stTaskSchedule;
    }

    static boolean isInit = false;

    public static void doTornado(ImageFloat input, ImageFloat filter, ImageFloat output) {
        TaskSchedule stTaskSchedule = getTaskSchedule(input, filter, output);

        stTaskSchedule.execute();
    }
    public static void main(String[] args) {
        final int imageSize = Integer.parseInt(args[0]);

        int runCnt = 1 + 50;
        long accum = 0;
        boolean initRun = true;
        for (int i = 0; i < runCnt; i++) {
            ImageFloat input = new ImageFloat(imageSize, imageSize);
            ImageFloat filter = new ImageFloat(5, 5);
            ImageFloat output = new ImageFloat(imageSize, imageSize);
    
            createImage(input);
            createFilter(filter);

            long startTime = System.nanoTime();
            doTornado(input, filter, output);
            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            if (initRun == true) {
                initRun = false;
            }
            else {
                accum += elapsed;
                System.out.printf("%d %d\n", imageSize, elapsed);
            }
        }

        //System.out.printf("%d %d\n", imageSize, accum/(runCnt - 1));

        //targetFunc(A, B, C);
    }
}
