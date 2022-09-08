import uk.ac.manchester.tornado.api.TaskSchedule
import uk.ac.manchester.tornado.api.annotations.Parallel
import uk.ac.manchester.tornado.api.common.Access
import uk.ac.manchester.tornado.api.common.TornadoDevice
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime
import uk.ac.manchester.tornado.runtime.common.TornadoOptions
import uk.ac.manchester.tornado.api.common.SchedulableTask

import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat

import java.io.File
import java.util.Random

import java.io.BufferedReader
import java.io.FileReader

object ScalaClass {

  val DO_CPU = true
  val DO_VALIDATE = false

  val PRINT_EVERY_ITERATION = false
  val GEN_DATA_EVERY_ITERATION = true
  val ENABLE_PROFILER = false
  val NANOSCALE = true

  val REUSE_OPENCL = true

  val MEASURE_COMPILE = false

  def matmul(a: Matrix2DFloat, b: Matrix2DFloat, c: Matrix2DFloat): Unit = {
    @Parallel var row: Int = 0
    while (row < c.M)
    {
      @Parallel var col: Int = 0
      while (col < c.N)
      {
        var sum: Float = 0
        var k: Int = 0
        while (k < b.M)
        {
          sum += a.get(row, k) * b.get(k, col)
          k += 1
        }
        c.set(row, col, sum)
        col += 1
      }
      row += 1
    }
  }

  var stTaskSchedule = new TaskSchedule("s0")
  var formerA: Matrix2DFloat = new Matrix2DFloat(0,0)
  var formerB: Matrix2DFloat = new Matrix2DFloat(0,0)
  var formerC: Matrix2DFloat = new Matrix2DFloat(0,0)

  val dirPath = System.getProperty("user.dir") + "/OpenCLSource"
  val filePathBase = dirPath + "/matmul_"


  def GetTimeStamp(): Long = {
    if (NANOSCALE) {
      System.nanoTime
    }
    else {
      System.currentTimeMillis
    }
  }

  def compileAndCreateOpenCLSource(matSize: Int): String = {
    val dir = new File(dirPath)
    if (dir.exists == false) {
      dir.mkdir
    }
    val filePath = filePathBase + matSize.toString + ".cl"
    val file = new File(filePath)
    if (REUSE_OPENCL == false || file.exists == false) {
      var GraalTime: Long = 0
      var OpenCLTime: Long = 0
      var DeviceTime: Long = 0
      var stTempTaskSchedule = new TaskSchedule("s1")

      for (i <- 0 until 1) {
        var taskName: String = ""

        stTempTaskSchedule = new TaskSchedule("s1")
        
        val tempA = new Matrix2DFloat(matSize, matSize)
        val tempB = new Matrix2DFloat(matSize, matSize)
        val tempC = new Matrix2DFloat(matSize, matSize)
        stTempTaskSchedule.task("t0", ScalaClass.matmul, tempA, tempB, tempC)
        stTempTaskSchedule.streamIn(tempA, tempB);
        stTempTaskSchedule.streamOut(tempC);
        
        TornadoOptions.PRINT_SOURCE = REUSE_OPENCL
        TornadoOptions.PRINT_SOURCE_DIRECTORY = filePath
        stTempTaskSchedule.execute
        TornadoOptions.PRINT_SOURCE = false

        if (stTempTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 128)
        {
          stTempTaskSchedule.getDevice().reset
          stTempTaskSchedule.warmup
        }

        /*
        GraalTime += stTempTaskSchedule.getTornadoCompilerTime
        OpenCLTime += stTempTaskSchedule.getDriverInstallTime
        DeviceTime += stTempTaskSchedule.getByteCodeTime
        */
      }

      //printf("%d %d %d\n", GraalTime/500, OpenCLTime/500, DeviceTime/500) 
      //printf("%d %d %d %d\n", stTempTaskSchedule.getCompileTime, stTempTaskSchedule.getTotalTime, stTempTaskSchedule.getDataTransfersTime, stTempTaskSchedule.getDeviceKernelTime) 

      stTempTaskSchedule.getTask("t0").getTaskName
    }
    else {
      var taskName: String = ""
      val fileReader = new BufferedReader(new FileReader(filePath))
      while (taskName.isEmpty == true) {
        val str = fileReader.readLine
        if (str.contains("__kernel") == true) {
          taskName = str.substring("__kernel void ".length, str.indexOf("("))
        }
      }
      taskName
    }
  }

  var minHeapSize: Long = 4

  def createTornadoVMTask(matSize: Int): Unit = {
    val taskName = compileAndCreateOpenCLSource(matSize).replace("$", "_")
    val filePath = filePathBase + matSize.toString + ".cl"

    stTaskSchedule = new TaskSchedule("s0")

    stTaskSchedule.prebuiltTask("t0", taskName, filePath,
      Array(formerA, formerB, formerC),
      Array(Access.READ, Access.READ, Access.READ_WRITE),
      TornadoRuntime.getTornadoRuntime().getDefaultDevice(),
      Array(matSize, matSize)
      )
    stTaskSchedule.streamIn(formerA, formerB)
    stTaskSchedule.streamOut(formerC)
    stTaskSchedule.execute
    stTaskSchedule = stTaskSchedule

    var tempMinHeapSize: Long = 4
    tempMinHeapSize *= matSize * matSize * 3

    minHeapSize = tempMinHeapSize + 1024
  }

  def doTornado(a: Matrix2DFloat, b: Matrix2DFloat, c: Matrix2DFloat): Unit = {

    stTaskSchedule.updateReference(formerA, a)
    stTaskSchedule.updateReference(formerB, b)
    stTaskSchedule.updateReference(formerC, c)

    if (stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 128)
    {
     stTaskSchedule.getDevice().reset
    }
    if (stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
    {
     stTaskSchedule.getDevice().reset
    }

    stTaskSchedule.execute()


    if (stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
    {
     stTaskSchedule.getDevice().reset
    }
    if (stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < 128)
    {
     stTaskSchedule.getDevice().reset
    }

    formerA = a
    formerB = b
    formerC = c
  }

  def checkResult(a: Array[Float], b: Array[Float]): Boolean = {
    var result = true;
    var diffCnt = 0
    for ( i <- 0 until a.length) 
    {
      val diff = a(i) - b(i)
      if ((diff > 0.1) || (diff < -0.1))
      {
        printf("diff!! %d %f %f\n", i, a(i), b(i))
        result = false
        diffCnt += 1
        if (diffCnt > 10)
          return result
      }
    }
    result
  }

  class tornadoProfile {
    var totalTime: Long = 0
    var compileTime: Long = 0
    var dataTransfersTime: Long = 0
    var totalKernelTime: Long = 0
    var totalDispatchTime: Long = 0
  }
  def doMatMul(matSize: Int): Unit = {
    val random = new Random();

    var cpuTotal: Long = 0
    var tornadoTotal: Long = 0

    val profiler = new tornadoProfile

    val warmUpStart = GetTimeStamp
    createTornadoVMTask(matSize)
    val warmUpTime = GetTimeStamp - warmUpStart
    val warmUpStart2 = GetTimeStamp
    createTornadoVMTask(matSize)
    val warmUpTime2 = GetTimeStamp - warmUpStart2

    /*
    val tempArray = new Array[Array[Float]](matSize)
    for (i <- 0 until matSize)
    {
      tempArray(i) = new Array[Float](matSize)
      for (j <- 0 until matSize)
      {
        tempArray(i)(j) = random.nextFloat()
      }
    }
    */
    val tempArray = new Array[Float](matSize * matSize)
    for (i <- 0 until matSize * matSize) {
      tempArray(i) = random.nextFloat()
    }

    var a = new Matrix2DFloat(matSize, matSize, tempArray.clone)
    var b = new Matrix2DFloat(matSize, matSize, tempArray.clone)
    var cCpu = new Matrix2DFloat(matSize, matSize, tempArray.clone)
    val warmupCpu = GetTimeStamp
    if (DO_CPU)
      matmul(a, b, cCpu)
    val warmupCpuTime = GetTimeStamp - warmupCpu

    var cTornado = new Matrix2DFloat(matSize, matSize)

    val loopSize = 11
    
    var isWarmUp = true
    for (run <- 0 until loopSize)
    {
      var cputime: Long = 0
      if (GEN_DATA_EVERY_ITERATION)
      {
        a = new Matrix2DFloat(matSize, matSize, tempArray.clone)
        b = new Matrix2DFloat(matSize, matSize, tempArray.clone)
        cCpu = new Matrix2DFloat(matSize, matSize)
        cTornado = new Matrix2DFloat(matSize, matSize)

        val cpustart = GetTimeStamp
        if (DO_CPU)
          matmul(a, b, cCpu)
        cputime = GetTimeStamp - cpustart
      }

      val tornadostart0 = GetTimeStamp
      doTornado(a, b, cTornado)
      val tornadotime0 = GetTimeStamp - tornadostart0

      if (isWarmUp == true) {
        isWarmUp = false
      }
      else {
        cpuTotal = cpuTotal + cputime
        tornadoTotal = tornadoTotal + tornadotime0

        if (ENABLE_PROFILER == true) {
          profiler.compileTime += stTaskSchedule.getCompileTime
          profiler.totalTime += stTaskSchedule.getTotalTime
          profiler.dataTransfersTime += stTaskSchedule.getDataTransfersTime
          profiler.totalKernelTime += stTaskSchedule.getDeviceKernelTime
          profiler.totalDispatchTime += stTaskSchedule.getKernelDispatchTime + stTaskSchedule.getDataTransferDispatchTime
        }
        printf("%d %d %d\n", matSize, cputime, tornadotime0)

        if (DO_VALIDATE && DO_CPU)
          checkResult(cCpu.getFlattenedArray, cTornado.getFlattenedArray)
        if (PRINT_EVERY_ITERATION)
        {
          if (ENABLE_PROFILER == true)
          {
            //printf("%d, %d, %d, %d, %d, %d, %.2f, %d, %d, %d, %d, %d\n", matSize, warmUpTime, warmUpTime2, warmupCpuTime, cputime, tornadotime0, cpuTotal.toFloat/tornadoTotal.toFloat, stTaskSchedule.getTotalTime, stTaskSchedule.getCompileTime, stTaskSchedule.getDataTransfersTime, stTaskSchedule.getDeviceKernelTime, stTaskSchedule.getKernelDispatchTime + stTaskSchedule.getDataTransferDispatchTime)
            printf("%d %d\n", matSize, tornadotime0)
          }
          else
          {
            //printf("%d, %d, %d, %d, %d, %d, %.2f\n", matSize, warmUpTime, warmUpTime2, warmupCpuTime, cpuTotal, tornadotime0, cpuTotal.toFloat/tornadoTotal.toFloat)
            printf("%d %d\n", matSize, tornadotime0)
          }
        }
      }
    }
    //printf("%d %d\n", matSize, tornadoTotal/loopSize)
    if (PRINT_EVERY_ITERATION == false) {
      printf("%d %d %d\n", matSize, cpuTotal/(loopSize - 1), tornadoTotal/(loopSize - 1))
      //printf("%d, %d, %d, %d, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n", matSize, warmUpTime, warmUpTime2, warmupCpuTime, cpuTotal.toFloat/loopSize.toFloat, tornadoTotal.toFloat/loopSize.toFloat, cpuTotal.toFloat/tornadoTotal.toFloat, profiler.totalTime.toFloat/loopSize.toFloat, profiler.compileTime.toFloat/loopSize.toFloat, profiler.dataTransfersTime.toFloat/loopSize.toFloat, profiler.totalKernelTime.toFloat/loopSize.toFloat, profiler.totalDispatchTime.toFloat/loopSize.toFloat)
    }
  }

  def main(args: Array[String]): Unit = {
    val matSize = Integer.parseInt(args(0))
    
    if (MEASURE_COMPILE == true) {
      for (i <- 0 until 50) {
        if (REUSE_OPENCL == true)
          createTornadoVMTask(matSize)
        else
          compileAndCreateOpenCLSource(matSize)
      }
    }
    else {
      doMatMul(matSize)
    }
  }
}
