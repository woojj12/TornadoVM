import subprocess
import os

fullDebug="False"
printKernel="False"
printByteCodes="False"
debug="False"
profiler="True"
#profilerSave="False"
threadInfo="False"
autoParallel="False"
sketcherThreads="10"
heapAllocation="20GB"
callStackLimit="10240"

matSizes = [65536,131072,262144,524288]##[524288,2097152,2097152*2, 2097152*4]#[4096,8192,8192*2,8192*4,8192*8,8192*16,8192*32,8192*64,8192*128,8192*256,8192*512,67108864, 134217728, 134217728*2]#33554432

cmd = "/home/jjwoo/openjdk1.8.0_302-jvmci-21.2-b08/bin/java -server -Xbootclasspath/a:/home/jjwoo/scala_jj/build/pack/lib/jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler-doc.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-library.jar:/home/jjwoo/scala_jj/build/pack/lib/scalap.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-reflect.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline-embedded.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-swing_2.12-2.0.3.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-xml_2.12-1.0.6.jar -classpath . -Dscala.boot.class.path=/home/jjwoo/scala_jj/build/pack/lib/jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler-doc.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-library.jar:/home/jjwoo/scala_jj/build/pack/lib/scalap.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-reflect.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline-embedded.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-swing_2.12-2.0.3.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-xml_2.12-1.0.6.jar -Dscala.home=/home/jjwoo/scala_jj/build/pack -Dscala.usejavacp=true -server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djava.library.path=/home/jjwoo/TornadoVM/bin/sdk/lib -Djava.ext.dirs=/home/jjwoo/TornadoVM/bin/sdk/share/java/tornado:/home/jjwoo/scala_jj/build/pack/lib  -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado -Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory -Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor -Ds0.t0.device=0:0 -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel -XX:-UseJVMCIClassLoader -Dtornado.fullDebug=" + fullDebug + " -Dtornado.print.kernel=" + printKernel + " -Dtornado.print.bytecodes=" + printByteCodes + " -Dtornado.debug=" + debug + " -Dtornado.threadInfo=" + threadInfo + " -Dtornado.opencl.callstack.limit=" + callStackLimit + " -Dtornado.parallelise.auto=" + autoParallel + " -Dtornado.heap.allocation=" + heapAllocation + " -Dtornado.sketcher.threads=" + sketcherThreads + " ScalaClass "

cmdWithProfile0 = "/home/jjwoo/openjdk1.8.0_302-jvmci-21.2-b08/bin/java -server -Xbootclasspath/a:/home/jjwoo/scala_jj/build/pack/lib/jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler-doc.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-library.jar:/home/jjwoo/scala_jj/build/pack/lib/scalap.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-reflect.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline-embedded.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-swing_2.12-2.0.3.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-xml_2.12-1.0.6.jar -classpath . -Dscala.boot.class.path=/home/jjwoo/scala_jj/build/pack/lib/jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler-doc.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-compiler.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-library.jar:/home/jjwoo/scala_jj/build/pack/lib/scalap.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-reflect.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline-embedded.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-repl-jline.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-swing_2.12-2.0.3.jar:/home/jjwoo/scala_jj/build/pack/lib/scala-xml_2.12-1.0.6.jar -Dscala.home=/home/jjwoo/scala_jj/build/pack -Dscala.usejavacp=true -server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djava.library.path=/home/jjwoo/TornadoVM/bin/sdk/lib -Djava.ext.dirs=/home/jjwoo/TornadoVM/bin/sdk/share/java/tornado:/home/jjwoo/scala_jj/build/pack/lib  -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado -Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory -Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor -Ds0.t0.device=0:0 -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel -XX:-UseJVMCIClassLoader -Dtornado.fullDebug=" + fullDebug + " -Dtornado.print.kernel=" + printKernel + " -Dtornado.print.bytecodes=" + printByteCodes + " -Dtornado.debug=" + debug + " -Dtornado.profiler=True -Dtornado.log.profiler=True -Dtornado.profiler.dump.dir="
cmdWithProfile1 = " -Dtornado.threadInfo=" + threadInfo + " -Dtornado.opencl.callstack.limit=" + callStackLimit + " -Dtornado.parallelise.auto=" + autoParallel + " -Dtornado.heap.allocation=" + heapAllocation + " -Dtornado.sketcher.threads=" + sketcherThreads + " ScalaClass "

errFile = open('ErrLog.log', 'w')

runCnt = 5

totalRun = len(matSizes) * runCnt

iter = 1

logFile = open('Log.log', 'w')
logFile.write('Size, WarmUpTime(with JIT), WarmUpTime(without JIT), WarmUpTime(CPU), CpuTime, TornadoVmTime(External), Gain, TornadoVmTime(Internal), CompileTime, DataTransferTime, KernelExecutionTime, DispatchTime\n')
logFile.flush()


if profiler!="True":
    for matSize in matSizes:
        runCmd = cmd + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            subprocess.call(runCmd.split(), stdout=logFile, stderr=logFile)
            iter += 1
    '''
    matSize = 65536
    while matSize < 134217728:
        runCmd = cmd + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            subprocess.call(runCmd.split(), stdout=logFile, stderr=logFile)
            iter += 1
        matSize *= 2
    '''
else:
    os.system('rm *.json')
    '''
    for matSize in matSizes:
        profileLogFile = 'profileLog_' + str(matSize) + '.json'
        runCmd = cmdWithProfile0 + profileLogFile + cmdWithProfile1 + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            subprocess.run(runCmd.split(), stdout=logFile, stderr=logFile)
            iter += 1
    '''
    matSize = 65536
    while matSize < 134217728:
        profileLogFile = 'profileLog_' + str(matSize) + '.json'
        runCmd = cmdWithProfile0 + profileLogFile + cmdWithProfile1 + str(matSize)
        for run in range (runCnt):
            print(str(iter) + "/" + str(totalRun) + " " + str(matSize))
            #print(runCmd)
            subprocess.call(runCmd.split(), stdout=logFile, stderr=logFile)
            iter += 1
        matSize *= 2

logFile.close()
errFile.close()