import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

import java.util.Random;

import java.util.stream.IntStream;

import java.io.RandomAccessFile;

class JavaClass
{
    static int len = 131072;
    static final boolean DO_VALIDATION = false;
    static final int runCnt = 50;

    static void dotProduct(float[] a, float[] b, @Reduce float[] c) {
        c[0] = 0;
        for (@Parallel int i = 0; i < a.length; i++) {
            c[0] += a[i] * b[i];
        }
    }
    static void doTornado() {
        float[] tempArray = new float[len];
        for (int i = 0; i < len; i++) {
            tempArray[i] = i;
        }

        float[] a = tempArray.clone();
        float[] b = tempArray.clone();
        float[] c = new float[1];
        c[0] = 0;

        TaskSchedule task = new TaskSchedule("s0");
        task.task("t0", JavaClass::dotProduct, a, b, c);
        task.streamIn(a,b);
        task.streamOut(c);
        task.execute();
        
        for (int i = 0; i < runCnt; i++) {
            float[] oldA = a;
            float[] oldB = b;
            //float[] oldC = c;

            a = tempArray.clone();
            b = tempArray.clone();
            //c = new float[1];
            c[0] = 0;

            task.updateReference(oldA, a);
            task.updateReference(oldB, b);
            //task.updateReference(oldC, c);

            long tornadoStartTime = System.nanoTime();
            task.execute();
            long tornadoEndTime = System.nanoTime();
            if (DO_VALIDATION == true) {
                System.out.println(c[0]);

                long cpuStartTime = System.nanoTime();
                dotProduct(a, b, c);
                long cpuEndTime = System.nanoTime();
                System.out.println(c[0]);
                System.out.printf("%d %d\n", (tornadoEndTime - tornadoStartTime), (cpuEndTime - cpuStartTime));
            }
            else {
                System.out.printf("%d %d\n", len, (tornadoEndTime - tornadoStartTime));
            }
        }
    }
    public static void main(String[] args)
    {
        len = Integer.parseInt(args[0]);
        doTornado();
    }
}
