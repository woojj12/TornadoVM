import uk.ac.manchester.tornado.api.TaskSchedule
import uk.ac.manchester.tornado.api.annotations.Parallel
import uk.ac.manchester.tornado.api.collections.types.ImageFloat
import uk.ac.manchester.tornado.api.common.Access
import uk.ac.manchester.tornado.api.common.TornadoDevice
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime
import uk.ac.manchester.tornado.runtime.common.TornadoOptions
import uk.ac.manchester.tornado.api.common.SchedulableTask
import uk.ac.manchester.tornado.api.mm.TaskMetaDataInterface

import uk.ac.manchester.tornado.benchmarks.BenchmarkUtils._

import java.io.File
import java.util.Random

import java.io.BufferedReader
import java.io.FileReader

object ScalaClass {
  val DO_VALIDATION = true

  val REUSE_OPENCL = true

  val MEASURE_COMPILE = false
  
  def convolveImage(input: ImageFloat, filter: ImageFloat, output: ImageFloat): Unit = {
    val filter2 = filter.X / 2

    @Parallel var y: Int = 0
    while (y < output.Y) {
      @Parallel var x: Int = 0
      while (x < output.X) {
        var sum: Float = 0.0f
        var v: Int = 0
        while (v < filter.Y) {
          var u: Int = 0
          while (u < filter.X) {
            if ((((y - filter2) + v) >= 0) && ((y + v) < output.Y())) {
              if ((((x - filter2) + u) >= 0) && ((x + u) < output.X())) {
                sum += filter.get(u, v) * input.get(x - filter2 + u, y - filter2 + v)
              }
            }
            u += 1
          }
          v += 1
        }
        output.set(x, y, sum);
        x += 1
      }
      y += 1
    }
  }

  var _stTaskSchedule = new TaskSchedule("s0")
  var oldInput: ImageFloat = new ImageFloat(0,0)
  var oldFilter: ImageFloat = new ImageFloat(0,0)
  var oldOutput: ImageFloat = new ImageFloat(0,0)

  val dirPath = System.getProperty("user.dir") + "/OpenCLSource"
  val filePathBase = dirPath + "/convol_"

  def compileAndCreateOpenCLSource(imageSize: Int): String = {
    val dir = new File(dirPath)
    if (dir.exists == false) {
      dir.mkdir
    }
    val filePath = filePathBase + imageSize.toString + ".cl"
    val file = new File(filePath)
    if (REUSE_OPENCL == false || file.exists == false) {
      var GraalTime: Long = 0
      var OpenCLTime: Long = 0
      var DeviceTime: Long = 0

      var taskName: String = ""
      var stTempTaskSchedule = new TaskSchedule("s1")
      val tempInput = new ImageFloat(imageSize, imageSize)
      val tempFilter = new ImageFloat(5, 5)
      val tempOutput = new ImageFloat(imageSize, imageSize)
      
      for (i <- 0 until 1) {
        stTempTaskSchedule = new TaskSchedule("s1")

        stTempTaskSchedule.task("t0", ScalaClass.convolveImage, tempInput, tempFilter, tempOutput)
        stTempTaskSchedule.streamIn(tempInput, tempFilter);
        stTempTaskSchedule.streamOut(tempOutput);
        
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

  def createTornadoVMTask(imageSize: Int): Unit = {
    val taskName = compileAndCreateOpenCLSource(imageSize).replace("$", "_")
    val filePath = filePathBase + imageSize.toString + ".cl"

    _stTaskSchedule = new TaskSchedule("s0")
    _stTaskSchedule.prebuiltTask("t0", taskName, filePath,
      Array(oldInput, oldFilter, oldOutput),
      Array(Access.READ, Access.READ, Access.READ_WRITE),
      TornadoRuntime.getTornadoRuntime().getDefaultDevice(),
      Array(imageSize, imageSize)
      )
    _stTaskSchedule.streamIn(oldInput, oldFilter)
    _stTaskSchedule.streamOut(oldOutput)

    _stTaskSchedule.execute

    minHeapSize *= imageSize * imageSize * 2 + 5 * 5
    minHeapSize += 1024;
  }

  def doTornado(input: ImageFloat, filter: ImageFloat, output: ImageFloat): Unit = {
    val stTaskSchedule = getTaskSchedule(input, filter, output)
    stTaskSchedule.execute()
  }

  def checkResult(a: Array[Float], b: Array[Float]): Boolean = {
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
  var bInit = false
  def getTaskSchedule(input: ImageFloat, filter: ImageFloat, output: ImageFloat): TaskSchedule = {
    if (bInit == false) {
      createTornadoVMTask(input.X)
      bInit = true
    }

    _stTaskSchedule.updateReference(oldInput, input)
    _stTaskSchedule.updateReference(oldFilter, filter)
    _stTaskSchedule.updateReference(oldOutput, output)
    if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
    {
        _stTaskSchedule.getDevice().reset
    }
    else if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 50) {
        _stTaskSchedule.getDevice().reset
    }

    oldInput = input
    oldFilter = filter
    oldOutput = output
    
    _stTaskSchedule
  }
  def doConvol(imageSize: Int): Unit = {
    val filterSize = 5

    var cpuTotal: Long = 0
    var tornadoTotal: Long = 0

    var outputCpu = new ImageFloat(imageSize, imageSize)
    var outputTornadoVM = new ImageFloat(imageSize, imageSize)

    val profiler = new tornadoProfile

    val loopSize = 1 + 200
    var initRun = true
    
    for (run <- 0 until loopSize)
    {
      val image = new ImageFloat(imageSize, imageSize)
      val filter = new ImageFloat(filterSize, filterSize)
      val outputTornadoVM = new ImageFloat(imageSize, imageSize)

      createImage(image)
      createFilter(filter)
      
      val outputCpu = if (DO_VALIDATION ==true) {
        new ImageFloat(imageSize, imageSize)
      }
      else {
        outputTornadoVM
      }

      var cputime: Long = 0
      if (DO_VALIDATION ==true) {
        val cpustart = System.nanoTime()
        convolveImage(image, filter, outputCpu)
        cputime = System.nanoTime() - cpustart
      }

      val tornadostart0 = System.nanoTime()
      doTornado(image, filter, outputTornadoVM)
      val tornadotime0 = System.nanoTime() - tornadostart0

      if (initRun == true) {
        initRun = false
      }
      else {
        if (DO_VALIDATION ==true) {
          cpuTotal = cpuTotal + cputime
        }
        tornadoTotal = tornadoTotal + tornadotime0

        /*
        profiler.compileTime += stTaskSchedule.getCompileTime
        profiler.totalTime += stTaskSchedule.getTotalTime
        profiler.dataTransfersTime += stTaskSchedule.getDataTransfersTime
        profiler.totalKernelTime += stTaskSchedule.getDeviceKernelTime
        profiler.totalDispatchTime += stTaskSchedule.getKernelDispatchTime + stTaskSchedule.getDataTransferDispatchTime
        */
        //printf("%d, %d, %d, %d, %d, %d, %.2f\n", imageSize, warmUpTime, warmUpTime2, warmupCpuTime, cpuTotal, tornadotime0, cpuTotal.toFloat/tornadoTotal.toFloat)
        //printf("%d %d\n", imageSize, cputime, tornadotime0)

        //if (DO_VALIDATION ==true) {
          //checkResult(outputCpu.getArray, outputTornadoVM.getArray)
        //}
      }
    }
      printf("%d %d %d\n", imageSize, cpuTotal/(loopSize-1), tornadoTotal/(loopSize-1))
    //printf("%d, %d, %d, %d, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n", imageSize, warmUpTime, warmUpTime2, warmupCpuTime, cpuTotal.toFloat/loopSize.toFloat, tornadoTotal.toFloat/loopSize.toFloat, cpuTotal.toFloat/tornadoTotal.toFloat, profiler.totalTime.toFloat/loopSize.toFloat, profiler.compileTime.toFloat/loopSize.toFloat, profiler.dataTransfersTime.toFloat/loopSize.toFloat, profiler.totalKernelTime.toFloat/loopSize.toFloat, profiler.totalDispatchTime.toFloat/loopSize.toFloat)
  }

  def main(args: Array[String]): Unit = {
    val imageSize = Integer.parseInt(args(0))
    
    if (MEASURE_COMPILE == true) {
      for (i <- 0 until 50) {
        if (REUSE_OPENCL == true)
          createTornadoVMTask(imageSize)
        else
          compileAndCreateOpenCLSource(imageSize)
      }
    }
    else {
      doConvol(imageSize)
    }
  }
}
