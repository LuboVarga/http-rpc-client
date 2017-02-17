package sk.http.app

import java.util.concurrent.{Future, TimeUnit}

import com.codahale.metrics.Metric
import nl.grons.metrics.scala.Implicits.functionToMetricFilter
import sk.httpclient.app.Record

import scala.collection.JavaConversions._
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

/**
  * https://confluence.nike.sk/display/VVP/RPC+projekt?focusedCommentId=11535760#comment-11535760
  *
  * Test Case, seriovo volane requesty (idempotentne|neidempotentne):
  * Service Timeout test case:
  * <ul>
  * <li>na 1 z N serverov bude sluzba odpovedať až po dlhšom (timeout trigerujúcom) čase. Ine sluzby (endpointy) serveru budu fungovat
  * a aj timeoutujuca sluzba bude na ostatnych serveroch fungovat (ziaden failnuty request | failnuty req s timeout exception)</li>
  * <li>na M z N serverov bude sluzba odpoved error HTTP kodom (napr 500). Circuit breaker sa otvori pre danu sluzbu,
  * ostatne budu fungovat dalej bez problemov (failnute req s Circuit Breaker exception | failnute req s Circuit Breaker
  * exception)</li>
  * <li>na M z N serverov bude sluzba odpoved error HTTP kodom (napr 500). Circuit breaker sa otvori pre danu sluzbu.
  * Sluzba zacne po case fungovat (odpovede 2XX). Circuit breaker sa pre danu sluzbu zatvori (failujuce req zacnu
  * prechadzat | failujuce req zacnu prechadzat)</li>
  * </ul>
  *
  * We always assume that three servers are running at localhost:888[789]
  *
  * Created by Ľubomír Varga on 14.2.2017.
  */
object TimeoutTestCases extends TestHelperObject {
  val printMetrics = false
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"

  /**
    * 20 ms would be in theory 50 request per second (rpc call in 0 latency)
    */
  val sleepTimeMs = 18

  def oneServerTimeoutingLongTime(testName: String, sender: SenderType[Record, Record], client: ControllingRibbonHttpClient[Record, Record]) = {
    (1 to 50).map(_ => client.ok)
    client.overload(45000)
    val results = runTest(20, sender)
    printReport(testName, results)
  }

  def printReport(testName: String, results: Seq[Try[Record]]) = {
    if (printMetrics) reporter.report()
    println("Five sample/random results:")
    scala.util.Random.shuffle(results)
      .take(5)
      .foreach(r => r match {
        case x: Success[Record] => println("VYSLEDOK:" + x)
        case x: Failure[Record] => println("VYSLEDOK ex:" + x.exception.printStackTrace(System.out))
      }
    )

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

  def runTest(requestCount: Int, sender: SenderType[Record, Record]) = (1 to requestCount)
    .map(i => if (i % 2 == 0) {
      (i, ControllingRibbonHttpClient.PORCEDURE_getRecord)
    } else {
      (i, ControllingRibbonHttpClient.PORCEDURE_makeCall)
    })
    .map({
      case (i, procedureName) => {
        TimeUnit.MILLISECONDS.sleep(sleepTimeMs)
        //httpRpc.time(Try(sender(procedureName, new Rec("CALL " + procedureName, i, "LLAC"), classOf[Rec])))
        httpRpc.time(Try(sender(procedureName, new Record("CALL " + procedureName, i, "LLAC"), classOf[Record])).map(x => {println(x);x}))
      }
    })

  def main(args: Array[String]): Unit = {
    val client = new ControllingRibbonHttpClient[Record, Record]("http://localhost:8887,http://localhost:8888,http://localhost:8889")
    // give ipinger time to fill in lbstatistics instance.
    TimeUnit.SECONDS.sleep(1)

    oneServerTimeoutingLongTime("noServerIsRunning - Idempotent", senderIdempotent(client), client)
    //oneServerTimeoutingLongTime("noServerIsRunning - NON-idempotent", senderNormal(client), client)
  }

  //type sender[R, T] = (procedureName: String , request: R, clazz: Class[T]) => T
  type SenderType[R, T] = (String, R, Class[T]) => T

  def senderIdempotent[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.sendIdempotentImmidiate(procedureName, request, clazz)

  def senderNormal[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.sendNonIdempotentImmidiate(procedureName, request, clazz)
}
