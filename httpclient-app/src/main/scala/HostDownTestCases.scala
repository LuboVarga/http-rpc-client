import java.util.concurrent.TimeUnit

import com.codahale.metrics.Metric
import nl.grons.metrics.scala.Implicits.functionToMetricFilter

import scala.collection.JavaConversions._
import scala.collection.immutable.Seq
import scala.util.{Success, Try}

/**
  * https://confluence.nike.sk/display/VVP/RPC+projekt?focusedCommentId=11535760#comment-11535760
  *
  * Test Case, seriovo volane requesty (idempotentne|neidempotentne):
  * Host Down test case:
  * <ul>
  * <li>1 server z N serverov je zapnuty a http klient bude spravne posielat requesty na tento zapnuty server (ziaden
  * failnuty request|ziaden failnuty request)</li>
  * <li>1 server z N serverov je zapnuty, postupne sa budu zapisant dalsie serveri. Http klient bude postupne posielat
  * requesty na zapnute serveri (ziaden failnuty request|ziaden failnuty request)</li>
  * <li>N serverov je zapnutych a budeme postupne vypinat serveri a http klient bude spravne posielat requesty iba
  * na zapnute serveri (ziaden failnuty request| N - 1 failnutych)</li>
  * <li>ziaden server nie je zapnuty (vsetko failne s nejakou nasou exception|vsetko failne s nejakou nasou exception)</li>
  * </ul>
  *
  * We always assume that three servers are running at localhost:888[789]
  *
  * Created by Ľubomír Varga on 14.2.2017.
  */
object HostDownTestCases extends TestHelperObject {
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"

  /**
    * 20 ms would be in theory 50 request per second (rpc call in 0 latency)
    */
  val sleepTimeMs = 18

  def onlyOneServerRunningTest = {}

  def serversStartingUpTest = {}

  def serversTurningOff = {}

  def noServerIsRunning = {
    val client = new ControllingRibbonHttpClient[Rec, Rec]("http://localhost:554,http://localhost:555,http://localhost:556")
    TimeUnit.SECONDS.sleep(2)
    // give ipinger time to fill in lbstatistics instance.
    val results: Seq[Try[Rec]] = (1 to 500)
      .map(i => if (i % 2 == 0) {
        (i, client.PORCEDURE_getRecord)
      } else {
        (i, client.PORCEDURE_makeCall)
      })
      .map({
        case (i, procedureName) => {
          TimeUnit.MILLISECONDS.sleep(sleepTimeMs)
          httpRpc.time(Try(client.send(procedureName, new Rec("CALL " + procedureName, i, "LLAC"), classOf[Rec]).get))
        }
      })

    printReport(results)
  }

  def printReport(results: Seq[Try[Rec]]) = {
    val successRequest = results.count(t => t.isInstanceOf[Success[Rec]])
    val failedRequest = results.count(t => t.isInstanceOf[Success[Rec]] == false)
    reporter.report()
    println(s"noServerIsRunning finished. $successRequest|$failedRequest\n")
    println("\t\tcall\trecord")
    println(s"countEmit\t${getValueForMetricsNameContaining(".countEmit", "call").head._2.getValue}\t${getValueForMetricsNameContaining(".countEmit", "record").head._2.getValue}")
    results.take(5).foreach(r => println("VYSLEDOK:" + r))
  }

  def getValueForMetricsNameContaining(stringToContain: String*) = {
    metricRegistry
      .getGauges((metricName: String, _: Metric) => {
        val numberOfContainedStrings = stringToContain.foldLeft(0) { (z, i) => {
          if (metricName.contains(i) == true) {
            1 + z
          } else {
            0 + z
          }
        }
        }
        numberOfContainedStrings == stringToContain.length
      })
      //.map(x => println(s"On ${x._1} there was ${x._2.getValue}"))
  }

  def main(args: Array[String]): Unit = {
    onlyOneServerRunningTest
    serversStartingUpTest
    serversTurningOff
    noServerIsRunning
  }
}
