package sk.http.app

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{ConsoleReporter, Metric}
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher
import com.netflix.hystrix.strategy.HystrixPlugins
import nl.grons.metrics.scala.Implicits.functionToMetricFilter
import sk.httpclient.app.Record

import scala.collection.JavaConversions._
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

/**
  * Created by Ľubomír Varga on 14.2.2017.
  */
trait TestHelperObject[R, T] extends nl.grons.metrics.scala.DefaultInstrumented {
  //type sender[R, T] = (procedureName: String , request: R, clazz: Class[T]) => T
  type SenderType[R, T] = (String, R, Class[T]) => T

  def senderIdempotent[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.sendIdempotentImmidiate(procedureName, request, clazz)

  def senderNormal[R, T](client: ControllingRibbonHttpClient[R, T]): SenderType[R, T] = (procedureName: String, request: R, clazz: Class[T]) => client.sendNonIdempotentImmidiate(procedureName, request, clazz)

  val interestingMetrics = Seq(
    ".errorPercentage", // real percentage of failed requests in given time window.
    ".countTimeout",
    ".countShortCircuited",
    ".countExceptionsThrown",
    ".countEmit",
    ".isCircuitBreakerOpen",
    ".latencyTotal_mean",
    //".latencyTotal_percentile_90",
    //".latencyExecute_percentile_90",
    ".latencyExecute_mean",
    ".countFallbackMissing",
    "http-rpc"
  )

  val reporter: ConsoleReporter = ConsoleReporter
    .forRegistry(metricRegistry)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .filter((metricName: String, _: Metric) => interestingMetrics.find(metricName.contains(_)).isDefined)
    .build

  // Define a timer metric
  HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry))

  protected val printMetrics = false

  protected val httpRpc = metrics.timer("http-rpc")

  /**
    * 20 ms would be in theory 50 request per second (rpc call in 0 latency)
    */
  protected val sleepTimeMs = 18

  def runTest(requestCount: Int, sender: SenderType[Record, Record]) = (1 to requestCount)
    .map(i => if (i % 2 == 0) {
      (i, ControllingRibbonHttpClient.PORCEDURE_getRecord)
    } else {
      (i, ControllingRibbonHttpClient.PORCEDURE_makeCall)
    })
    .map({
      case (i, procedureName) => {
        TimeUnit.MILLISECONDS.sleep(sleepTimeMs)
        //httpRpc.time(Try(sender(procedureName, new Rec("CALL " + procedureName, i, "LLAC"), classOf[T])))
        (procedureName, httpRpc.time(Try(sender(procedureName, new Record("CALL " + procedureName, i, "LLAC"), classOf[Record])).map(x => {
          println(x)
          x
        })))
      }
    })

  def printReport(testName: String, results: Seq[(String, Try[T])]) = {
    if (printMetrics) reporter.report()
    println("Five sample/random results:")
    scala.util.Random.shuffle(results)
      .take(5)
      .foreach(r => r match {
        case x: (String, Success[Record]) => println("VYSLEDOK:" + x)
        case x: (String, Failure[Record]) => println("VYSLEDOK ex:" + x._1 + "\t"+ x._2.exception.printStackTrace(System.out))
      })

    val resultsGrouped = results.groupBy(res => res match {
      case s: (String, Success[T]) =>
        (s._1, true)
      case s: (String, Failure[T]) =>
        (s._1, false)
    })
    val aggregated = resultsGrouped.map(g => g._1 -> g._2.aggregate(0)({(sum, r) => sum + r._2.map(_ => 1).getOrElse(0)}, { (p1, p2) => p1 + p2 }))

    aggregated.foreach(x => println(s"${x._1._1}\t${x._1._2}\t: ${x._2}"))

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
}
