import subprocess
import os
import time

fullDebug="False"
printKernel="False"
printByteCodes="False"
debug="False"
profiler="False"
profilerSave="False"
threadInfo="False"
autoParallel="False"
sketcherThreads="10"
heapAllocation="20GB"
callStackLimit="10240"
dumpCodeCache="False"

#matSizes = [10000,15000,30000,32000]
matSizes = [512,1024,2048,4096,8192]#[5000,10000,15000]

cmd = "/home/jjwoo/openjdk1.8.0_302-jvmci-21.2-b08/bin/java -Xmx64G -server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djava.library.path=/home/jjwoo/TornadoVM/bin/sdk/lib -Djava.ext.dirs=/home/jjwoo/TornadoVM/bin/sdk/share/java/tornado -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado -Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory -Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel -XX:-UseJVMCIClassLoader -Dtornado.fullDebug=" + fullDebug + " -Dtornado.print.kernel=" + printKernel + " -Dtornado.print.bytecodes=" + printByteCodes + " -Dtornado.debug=" + debug + " -Dtornado.threadInfo=" + threadInfo + " -Dtornado.opencl.codecache.dump=" + dumpCodeCache + " -Dtornado.opencl.callstack.limit=" + callStackLimit + " -Dtornado.parallelise.auto=" + autoParallel + " -Dtornado.heap.allocation=" + heapAllocation + " JavaClass "

cmdWithProfile0 = "/home/jjwoo/openjdk1.8.0_302-jvmci-21.2-b08/bin/java -Xmx32G  -server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djava.library.path=/home/jjwoo/TornadoVM/bin/sdk/lib -Djava.ext.dirs=/home/jjwoo/TornadoVM/bin/sdk/share/java/tornado -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado -Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory -Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel -XX:-UseJVMCIClassLoader -Dtornado.fullDebug=" + fullDebug + " -Dtornado.print.kernel=" + printKernel + " -Dtornado.print.bytecodes=" + printByteCodes + " -Dtornado.debug=" + debug + " -Dtornado.profiler=True -Dtornado.log.profiler=True -Dtornado.profiler.dump.dir="
cmdWithProfile1 = " -Dtornado.threadInfo=" + threadInfo + " -Dtornado.opencl.codecache.dump=" + dumpCodeCache + " -Dtornado.opencl.callstack.limit=" + callStackLimit + " -Dtornado.parallelise.auto=" + autoParallel + " -Dtornado.heap.allocation=" + heapAllocation + " -Dtornado.sketcher.threads=" + sketcherThreads + " JavaClass "

errFile = open('ErrLog.log', 'w')

runCnt = 5

totalRun = len(matSizes) * runCnt

iter = 1

logFile = open('Log2.log', 'w')
logFile.write('Size, WarmUpTime(with JIT), WarmUpTime(without JIT), WarmUpTime(CPU), CpuTime, TornadoVmTime(External), Gain, TornadoVmTime(Internal), CompileTime, DataTransferTime, KernelExecutionTime, DispatchTime\n')
logFile.flush()

if profiler!="True":
    for matSize in matSizes:
        runCmd = cmd + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            subprocess.run(runCmd.split(), stdout=logFile, stderr=logFile)
            #os.system(runCmd)
            #subprocess.call(runCmd.split())
            time.sleep(1)
            iter += 1
else:
    os.system('rm *.json')
    for matSize in matSizes:
        profileLogFile = 'profileLog_' + str(matSize) + '.json'
        runCmd = cmdWithProfile0 + profileLogFile + cmdWithProfile1 + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            #os.system(runCmd)
            subprocess.run(runCmd.split(), stdout=logFile, stderr=logFile)
            time.sleep(1)
            iter += 1

logFile.close()
errFile.close()
