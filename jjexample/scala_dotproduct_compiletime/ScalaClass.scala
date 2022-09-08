import uk.ac.manchester.tornado.api.TaskSchedule
import uk.ac.manchester.tornado.api.annotations.Parallel
import uk.ac.manchester.tornado.api.annotations.Reduce
import uk.ac.manchester.tornado.api.common.Access
import uk.ac.manchester.tornado.api.common.TornadoDevice
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime
import uk.ac.manchester.tornado.runtime.common.TornadoOptions
import uk.ac.manchester.tornado.api.common.SchedulableTask
import uk.ac.manchester.tornado.runtime.tasks.ReduceTaskSchedule

import uk.ac.manchester.tornado.api.collections.types.Matrix2DDouble

import java.io.File
import java.util.Random

import java.io.BufferedReader
import java.io.FileReader

object ScalaClass {
  var len: Int =  134217728
  val runCnt = 0
  val DO_VALIDATION = true

  val REUSE_OPENCL = true

  val MEASURE_COMPILE = false

  var _stTaskSchedule = new TaskSchedule("s0")
  var oldA = new Array[Float](0)
  var oldB = new Array[Float](0)
  var output = new Array[Float](1)

  def targetFunc(a: Array[Float], b: Array[Float], @Reduce c: Array[Float]): Unit = {
    c(0) = 0
    @Parallel var i = 0
    while (i < len) {
      val value = a(i) * b(i)
      c(0) += value
      i += 1
    }
  }

  val dirPath = System.getProperty("user.dir") + "/OpenCLSource"
  val filePathBase = dirPath + "/dotproduct_"

  def createFileName(vectorSize: Int): String = {
      return Integer.toString(vectorSize) + ".cl";
  }

  def  compileAndCreateOpenCLSource(vectorSize: Int): String = {
      val dir = new File(dirPath)
      dir.mkdir()
      val filePath = filePathBase + createFileName(vectorSize)
      val file = new File(filePath);
      if ((REUSE_OPENCL == false) || (file.exists() == false))
      {          
          val tempA: Array[Float] = new Array[Float](vectorSize)
          val tempB: Array[Float] = new Array[Float](vectorSize)

          val stTempTaskSchedule = new TaskSchedule("s0")
          stTempTaskSchedule.task("t0", ScalaClass.targetFunc, tempA, tempB, output)
          stTempTaskSchedule.streamIn(tempA, tempB);
          stTempTaskSchedule.streamOut(output);

          TornadoOptions.PRINT_SOURCE = true
          TornadoOptions.PRINT_SOURCE_DIRECTORY = filePath
          stTempTaskSchedule.execute()
          TornadoOptions.PRINT_SOURCE = false

          println(stTempTaskSchedule.getDevice().toString())

          if (stTempTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 128)
          {
              stTempTaskSchedule.getDevice().reset()
              stTempTaskSchedule.warmup()
          }
          if (MEASURE_COMPILE == true) {
            val graalTime = _stTaskSchedule.getTornadoCompilerTime()
            val openCLTime = _stTaskSchedule.getDriverInstallTime()
            val byteCodeTime = _stTaskSchedule.getByteCodeTime()

            printf("%d %d %d\n", graalTime, openCLTime, byteCodeTime)
          }

          stTempTaskSchedule.getTask("t0").getTaskName()
      }
      else {
          var taskName = ""
            val fileReader = new BufferedReader(new FileReader(filePath))
            while (taskName.isEmpty() == true) {
                val str = fileReader.readLine();
                if (str.contains("__kernel") == true) {
                    taskName = str.substring("__kernel void ".length(), str.indexOf("("));
                }
            }
          taskName;
      }
  }

  var minHeapSize: Long = 0

  def createTornadoVMTask(vectorSize: Int): Unit = {
      val taskName = compileAndCreateOpenCLSource(vectorSize).replace("$", "_")
      val filePath = filePathBase + createFileName(vectorSize)

      _stTaskSchedule = new TaskSchedule("s0")
      val params = Array(oldA, oldB, output)
      val access = Array(Access.READ, Access.READ, Access.READ_WRITE)
      val dim = Array(vectorSize)
      val tempArray = new Array[Float](vectorSize)
      for (i <- 0 until vectorSize) {
          tempArray(i) = i
      }
      oldA = tempArray.clone()
      oldB = tempArray.clone()

      ReduceTaskSchedule.USE_PREBUILT = true
      ReduceTaskSchedule.PREBUILT_FILE = filePath
      ReduceTaskSchedule.ENTRY_POINT = taskName
      ReduceTaskSchedule.ACCESS = access
      ReduceTaskSchedule.DIM = dim
      _stTaskSchedule.streamIn(oldA, oldB)
      _stTaskSchedule.streamOut(output)
      _stTaskSchedule.task("t0", ScalaClass.targetFunc, oldA, oldB, output)

      _stTaskSchedule.execute()

      if (MEASURE_COMPILE == true) {
        val graalTime = _stTaskSchedule.getTornadoCompilerTime()
        val openCLTime = _stTaskSchedule.getDriverInstallTime()
        val byteCodeTime = _stTaskSchedule.getByteCodeTime()

        printf("%d %d %d\n", graalTime, openCLTime, byteCodeTime);
      }
  
      minHeapSize = 4 * (vectorSize * 2) + 1024
  }

  def getTaskSchedule(a: Array[Float], b: Array[Float]): TaskSchedule = {
      if (isInit == false) {
          createTornadoVMTask(a.length)
          isInit = true
      }

      _stTaskSchedule.updateReference(oldA, a)
      _stTaskSchedule.updateReference(oldB, b)
      if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getHeapRemaining() < minHeapSize)
      {
          _stTaskSchedule.getDevice().reset()
      }
      else if (_stTaskSchedule.getDevice().getDeviceContext().getMemoryManager().getCallStackRemaining() < 50) {
          _stTaskSchedule.getDevice().reset()
      }

      oldA = a
      oldB = b

      return _stTaskSchedule
    }

  var isInit = false

  var cpuTime: Long = 0
  var gpuTime: Long = 0

  var warmUp = true

  def doTornado(a: Array[Float], b: Array[Float]): Unit = {
    if (MEASURE_COMPILE == true) {
      if (REUSE_OPENCL == true)
        getTaskSchedule(a, b)
      else
        compileAndCreateOpenCLSource(a.length)
    }
    else {
      val startTime = System.nanoTime
      getTaskSchedule(a, b).execute
      val endTime = System.nanoTime
      if (warmUp == true) {
        warmUp = false
      } else {
        gpuTime += (endTime - startTime)/1000
        if (DO_VALIDATION == true) {
          val c = new Array[Float](1)
          val startTimeCpu = System.nanoTime
          targetFunc(a, b, c)
          val endTimeCpu = System.nanoTime
          printf("%d %d %d\n", a.length, (endTime -startTime), (endTimeCpu - startTimeCpu))
          cpuTime += (endTimeCpu - startTimeCpu)/1000
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    len = Integer.parseInt(args(0))
    val tempArray = new Array[Float](len)
    for (i <- 0 until len) {
      tempArray(i) = i
    }
    //val a = tempArray.clone
    //val b = tempArray.clone
    val runCnt = 200 + 1
    for (i <- 0 until runCnt) {
      doTornado(tempArray.clone, tempArray.clone)
    }
    printf("%d %d %d\n", len, gpuTime/(runCnt-1), cpuTime/(runCnt-1))
  }
}
