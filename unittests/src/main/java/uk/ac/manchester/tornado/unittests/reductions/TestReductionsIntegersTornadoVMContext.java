/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsIntegersTornadoVMContext extends TornadoTestBase {

    public static int computeSequential(int[] input) {
        int acc = 0;
        for (int v : input) {
            acc += v;
        }
        return acc;
    }

    public static void intReductionGlobalMemory(TornadoVMContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] += a[id + stride];
            }
        }
        context.globalBarrier();
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testIntReductionsGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(input, localSize).task("t0", TestReductionsIntegersTornadoVMContext::intReductionGlobalMemory, context, input, reduce).streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        int finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void intReductionLocalMemory(TornadoVMContext context, int[] a, int[] b) {
        int globalIdx = context.threadIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(1024);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testIntReductionsLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        int sequential = computeSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(input, localSize).task("t0", TestReductionsIntegersTornadoVMContext::intReductionLocalMemory, context, input, reduce).streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }
}