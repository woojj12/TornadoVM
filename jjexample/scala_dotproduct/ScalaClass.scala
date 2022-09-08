import uk.ac.manchester.tornado.api.TaskSchedule
import uk.ac.manchester.tornado.api.annotations.Parallel
import uk.ac.manchester.tornado.api.annotations.Reduce
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
  var len: Int =  134217728
  val runCnt = 0
  val DO_VALIDATION = false

  def dotProduct(a: Array[Float], b: Array[Float], @Reduce c: Array[Float]): Unit = {
    //c(0) = 0
    @Parallel var i = 0
    while (i < len) {
      val value = a(i) * b(i)
      c(0) += value
      i += 1
    }
  }

  def doTornado(): Unit = {
    val tempArray = new Array[Float](len)
    for (i <- 0 until len) {
      tempArray(i) = i
    }
    var a = tempArray.clone
    var b = tempArray.clone
    var c = new Array[Float](1)
    c(0) = 0
    val cCpu = new Array[Float](1)
    val task = new TaskSchedule("s0")
    task.task("t0", ScalaClass.dotProduct, a,b,c)
    task.streamIn(a,b)
    task.streamOut(c)
    
    task.execute()
    //println(c(0))

    for (i <- 0 until runCnt)
    {
      val oldA = a
      val oldB = b
      //val oldC = c

      a = tempArray.clone
      b = tempArray.clone
      //c = new Array[Float](1)

      task.updateReference(oldA, a)
      task.updateReference(oldB, b)
      //task.updateReference(oldC, c)

      c(0) = 0
      cCpu(0) = 0

      val tornadoStartTime = System.nanoTime
      task.execute()
      val tornadoEndTime = System.nanoTime
      if (DO_VALIDATION == true) {
        println(c(0))
        val cpuStartTime = System.nanoTime
        dotProduct(a,b,cCpu)
        val cpuEndTime = System.nanoTime
        println(cCpu(0))
        printf("%d %d %d\n", len, (tornadoEndTime - tornadoStartTime), (cpuEndTime - cpuStartTime))
      }
      else {
        printf("%d %d\n", len, (tornadoEndTime - tornadoStartTime))
      }
    }
  }

  def main(args: Array[String]): Unit = {
    len = Integer.parseInt(args(0))
    doTornado
  }
}
