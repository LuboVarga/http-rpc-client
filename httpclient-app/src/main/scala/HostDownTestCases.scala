import java.util.concurrent.{Future, TimeUnit}

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
  val printMetrics = false
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"

  /**
    * 20 ms would be in theory 50 request per second (rpc call in 0 latency)
    */
  val sleepTimeMs = 18

  def onlyOneServerRunningTest(testName: String, sender: SenderType[Rec, Rec]) = {
    val results: Seq[Try[Rec]] = runTest(250, sender)
    printReport(testName, results)
  }

  def serversStartingUpTest(testName: String, sender: SenderType[Rec, Rec], client: ControllingRibbonHttpClient[Rec, Rec]) = {
    // turn off servers for beginning...
    client.deploy(10)
    client.deploy(15)
    client.deploy(20)
    ??? // TODO implement
  }

  def serversTurningOff(testName: String, sender: SenderType[Rec, Rec]) = {
    val results: Seq[Try[Rec]] = runTest(250, sender)
    printReport(testName, results)
    ??? // TODO implement parallel shutting of servers down
  }

  def noServerIsRunning(testName: String, sender: SenderType[Rec, Rec]) = {
    val results: Seq[Try[Rec]] = runTest(250, sender)
    printReport(testName, results)
  }

  def printReport(testName: String, results: Seq[Try[Rec]]) = {
    if(printMetrics) reporter.report()
    println("Five sample/random results:")
    scala.util.Random.shuffle(results).take(5).foreach(r => println("VYSLEDOK:" + r))

    val successRequest = results.count(t => t.isInstanceOf[Success[Rec]])
    val failedRequest = results.count(t => t.isInstanceOf[Success[Rec]] == false)
    println(s"${testName} finished. $successRequest|$failedRequest\n")
    println("\t\t\t\t\tcall\trecord")
    printTableLine("countEmit\t\t\t")
    printTableLine("countExceptionsThrown")
    printTableLine("countFailure\t\t")
    printTableLine("countFallbackMissing")
    printTableLine("countSemaphoreRejected")
    printTableLine("countShortCircuited\t")
    printTableLine("countSuccess\t\t")
    printTableLine("countTimeout\t\t")
    printTableLine("errorPercentage\t\t")
    printTableLine("latencyTotal_mean\t")
    printTableLine("latencyExecute_mean\t")
  }

  def printTableLine(metricsType: String) = {
    val trimmed = metricsType.trim
    println(s"${metricsType}\t${getValueForMetricsNameContaining("." + trimmed, "call").head._2.getValue}\t\t${getValueForMetricsNameContaining("." + trimmed, "record").head._2.getValue}")
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

  def runTest(requestCount: Int, sender: SenderType[Rec, Rec]) = (1 to requestCount)
    .map(i => if (i % 2 == 0) {
      (i, ControllingRibbonHttpClient.PORCEDURE_getRecord)
    } else {
      (i, ControllingRibbonHttpClient.PORCEDURE_makeCall)
    })
    .map({
      case (i, procedureName) => {
        TimeUnit.MILLISECONDS.sleep(sleepTimeMs)
        httpRpc.time(Try(sender(procedureName, new Rec("CALL " + procedureName, i, "LLAC"), classOf[Rec]).get))
      }
    })

  def main(args: Array[String]): Unit = {
    val client = new ControllingRibbonHttpClient[Rec, Rec]("http://localhost:8887,http://localhost:8888,http://localhost:8889")
    val clientAllOff = new ControllingRibbonHttpClient[Rec, Rec]("http://localhost:554,http://localhost:555,http://localhost:556")
    val clientOneRunningServer = new ControllingRibbonHttpClient[Rec, Rec]("http://localhost:8888,http://localhost:555,http://localhost:556")
    // give ipinger time to fill in lbstatistics instance.
    TimeUnit.SECONDS.sleep(1)

    noServerIsRunning("noServerIsRunning - Idempotent", senderIdempotent(clientAllOff))
    noServerIsRunning("noServerIsRunning - NON-idempotent", senderNormal(clientAllOff))

    onlyOneServerRunningTest("onlyOneServerRunningTest - Idempotent", senderIdempotent(clientOneRunningServer))
    onlyOneServerRunningTest("onlyOneServerRunningTest - NON-idempotent", senderNormal(clientOneRunningServer))

    serversStartingUpTest("serversStartingUpTest - Idempotent", senderIdempotent(client), client)
    serversStartingUpTest("serversStartingUpTest - NON-idempotent", senderNormal(client), client)

    serversTurningOff("serversTurningOff - Idempotent", senderIdempotent(client))
    serversTurningOff("serversTurningOff - NON-idempotent", senderNormal(client))
  }

  //type sender[R, T] = (procedureName: String , request: R, clazz: Class[T]) => T
  type SenderType[R, T] = (String , R, Class[T]) => Future[T]
  def senderIdempotent[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.sendIdempotent(procedureName, request, clazz)
  def senderNormal[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.send(procedureName, request, clazz)
}
