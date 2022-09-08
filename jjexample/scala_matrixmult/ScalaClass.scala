import uk.ac.manchester.tornado.api.TaskSchedule
import uk.ac.manchester.tornado.api.annotations.Parallel
import uk.ac.manchester.tornado.api.common.Access
import uk.ac.manchester.tornado.api.common.TornadoDevice
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime
import uk.ac.manchester.tornado.runtime.common.TornadoOptions
import uk.ac.manchester.tornado.api.common.SchedulableTask

import uk.ac.manchester.tornado.api.collections.types.Matrix2DDouble

import java.io.File
import java.util.Random

import java.io.BufferedReader
import java.io.FileReader

object ScalaClass {
  def matmul(a: Matrix2DDouble, b: Matrix2DDouble, c: Matrix2DDouble): Unit = {
    @Parallel var row: Int = 0
    while (row < c.M)
    {
      @Parallel var col: Int = 0
      while (col < c.N)
      {
        var sum: Double = 0
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
  var formerA: Matrix2DDouble = new Matrix2DDouble(0,0)
  var formerB: Matrix2DDouble = new Matrix2DDouble(0,0)
  var formerC: Matrix2DDouble = new Matrix2DDouble(0,0)

  val dirPath = System.getProperty("user.dir") + "/OpenCLSource"
  val filePathBase = dirPath + "/matmul_"

  def compileAndCreateOpenCLSource(matSize: Int): String = {
    val dir = new File(dirPath)
    if (dir.exists == false) {
      dir.mkdir
    }
    val filePath = filePathBase + matSize.toString + ".cl"
    val file = new File(filePath)
    if (file.exists == false) {
      var GraalTime: Long = 0
      var OpenCLTime: Long = 0
      var DeviceTime: Long = 0
      var stTempTaskSchedule = new TaskSchedule("s1")

      for (i <- 0 until 1) {
        var taskName: String = ""

        stTempTaskSchedule = new TaskSchedule("s1")
        
        val tempA = new Matrix2DDouble(matSize, matSize)
        val tempB = new Matrix2DDouble(matSize, matSize)
        val tempC = new Matrix2DDouble(matSize, matSize)
        stTempTaskSchedule.task("t0", ScalaClass.matmul, tempA, tempB, tempC)
        stTempTaskSchedule.streamIn(tempA, tempB);
        stTempTaskSchedule.streamOut(tempC);
        
        TornadoOptions.PRINT_SOURCE = true
        TornadoOptions.PRINT_SOURCE_DIRECTORY = filePath
        stTempTaskSchedule.warmup
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
    stTaskSchedule.warmup
  }

  def doTornado(a: Matrix2DDouble, b: Matrix2DDouble, c: Matrix2DDouble): Unit = {
    stTaskSchedule.updateReference(formerA, a)
    stTaskSchedule.updateReference(formerB, b)
    stTaskSchedule.updateReference(formerC, c)

    println(formerA.getFlattenedArray)
    println(formerB.getFlattenedArray)
    println(formerC.getFlattenedArray)

    stTaskSchedule.execute()

    if (stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 128)
     stTaskSchedule.getDevice().reset

    formerA = a
    formerB = b
    formerC = c
  }

  def checkResult(a: Array[Double], b: Array[Double]): Boolean = {
    var result = true;
    for ( i <- 0 until a.length) 
    {
      val diff = a(i) - b(i)
      if ((diff > 0.1) || (diff < -0.1))
      {
        printf("diff!! %d %f %f\n", i, a(i), b(i))
        result = false
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

    val filterSize = 5

    var cpuTotal: Long = 0
    var tornadoTotal: Long = 0

    val outputCpu = new Matrix2DDouble(matSize, matSize)
    val outputTornadoVM = new Matrix2DDouble(matSize, matSize)

    val profiler = new tornadoProfile

    val tempArray = new Array[Array[Double]](matSize)
    for (i <- 0 until matSize)
    {
      tempArray(i) = random.doubles(matSize).toArray()
    }
    var a = new Matrix2DDouble(tempArray)
    var b = new Matrix2DDouble(tempArray)
    var cCpu = new Matrix2DDouble(tempArray)
    val warmupCpu = System.nanoTime
    matmul(a, b, cCpu)
    val warmupCpuTime = System.nanoTime - warmupCpu

    val warmUpStart = System.nanoTime()
    createTornadoVMTask(matSize)
    val warmUpTime = System.nanoTime() - warmUpStart
    val warmUpStart2 = System.nanoTime()
    createTornadoVMTask(matSize)
    val warmUpTime2 = System.nanoTime() - warmUpStart2

    var cTornado = new Matrix2DDouble(tempArray)

    val netloopSize = 11
    val loopSize = netloopSize - 1
    
    var isWarmUp = true
    for (run <- 0 until loopSize)
    {
      a = new Matrix2DDouble(tempArray)
      b = new Matrix2DDouble(tempArray)
      cCpu = new Matrix2DDouble(tempArray)
      cTornado = new Matrix2DDouble(tempArray)

      val cpustart = System.nanoTime()
      matmul(a, b, cCpu)
      val cputime = System.nanoTime() - cpustart

      val tornadostart0 = System.nanoTime()
      doTornado(a, b, cTornado)
      val tornadotime0 = System.nanoTime() - tornadostart0

      if (isWarmUp == true) {
        isWarmUp = false
      }
      else {
        cpuTotal = cpuTotal + cputime
        tornadoTotal = tornadoTotal + tornadotime0

        /*
        profiler.compileTime += stTaskSchedule.getCompileTime
        profiler.totalTime += stTaskSchedule.getTotalTime
        profiler.dataTransfersTime += stTaskSchedule.getDataTransfersTime
        profiler.totalKernelTime += stTaskSchedule.getDeviceKernelTime
        profiler.totalDispatchTime += stTaskSchedule.getKernelDispatchTime + stTaskSchedule.getDataTransferDispatchTime
        */
      }

      checkResult(cCpu.getFlattenedArray, cTornado.getFlattenedArray)
    }
    printf("%d, %d, %d, %d, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n", matSize, warmUpTime, warmUpTime2, warmupCpuTime, cpuTotal.toFloat/loopSize.toFloat, tornadoTotal.toFloat/loopSize.toFloat, cpuTotal.toFloat/tornadoTotal.toFloat, profiler.totalTime.toFloat/loopSize.toFloat, profiler.compileTime.toFloat/loopSize.toFloat, profiler.dataTransfersTime.toFloat/loopSize.toFloat, profiler.totalKernelTime.toFloat/loopSize.toFloat, profiler.totalDispatchTime.toFloat/loopSize.toFloat)
  }

  def main(args: Array[String]): Unit = {
    val matSize = Integer.parseInt(args(0))
    doMatMul(matSize)
  }
}
