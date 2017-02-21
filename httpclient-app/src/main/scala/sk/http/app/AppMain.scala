package sk.http.app

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{ConsoleReporter, Metric}
import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher
import com.netflix.hystrix.strategy.HystrixPlugins
import nl.grons.metrics.scala.Implicits.functionToMetricFilter
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.collection.parallel.ForkJoinTaskSupport
import scala.util.Try

/**
  * Created by Ľubomír Varga on 7.2.2017.
  */
object AppMain extends nl.grons.metrics.scala.DefaultInstrumented {
  // Define a timer metric
  HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry))

  private val httpRpc = metrics.timer("http-rpc")

  val interestingMetrics = Seq(
    "countEmit",
    //, "countExceptionsThrown", "countFailure", "countFallbackMissing",
    "countShortCircuited",
    "s",
    //"countSuccess", "rollingCountEmit", "rollingCountSuccess", "isCircuitBreakerOpen",
    "isCircuitBreakerOpen",
    "http-rpc"
  )

  val reporter: ConsoleReporter = ConsoleReporter
    .forRegistry(metricRegistry)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .filter((metricName: String, _: Metric) => interestingMetrics.find(metricName.contains(_)).isDefined)
    .build
  reporter.start(5, TimeUnit.SECONDS)

  def main(args: Array[String]): Unit = {
    val s = new SynchronizedSummaryStatistics()
    val p = new Percentile(95.0)
    //val r = new RibbonHttpClient[Rec, Rec]("localhost:8887,localhost:8888,localhost:8889")

    val clients = (1 to 1).map(_ => new ControllingRibbonHttpClient[Record, Record]("http://localhost:8887,http://localhost:8888,http://localhost:8889")).par
    clients.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(50))
    val allDurations = clients.flatMap(r => {
      val a = (1 to 2000).par
      a.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(4))

      val durations = a.map(i => {
        if (i == 223) {
          println("deploy at 223")
          r.deploy(30)
          r.deploy(30)
          r.deploy(30)
        }
        val start = System.nanoTime()
        val o = httpRpc.time {
          Try {
            if (i % 2 == 0) {
              r.sendCommand(ControllingRibbonHttpClient.PORCEDURE_getRecord, new Record("", i, ""), classOf[Record])
            } else {
              r.sendCommand(ControllingRibbonHttpClient.PORCEDURE_makeCall, new Record("CALL", i, "LLAC"), classOf[Record])
            }
          }.toOption
        }
        val end = System.nanoTime()
        val duration = (end - start) / 1000000.0
        //println(s"Result of call: ${o.city}; duration: ${duration}ms.")
        print(o.map(_ => {
          val d = Math.round(Math.log(duration + 1)) + ""
          if (d.length != 1) {
            " " + duration + " "
          } else {
            d
          }
        }).getOrElse("X"))
        Thread.sleep(208)
        s.addValue(duration)
        duration
      })
      durations
    })
    println("Statistics=" + s)
    val allDurationsInArray = allDurations.toArray
    p.setData(allDurationsInArray)
    println("95 percentile: " + p.evaluate())
    p.setData(allDurationsInArray.sorted)
    println("95 percentile (sorted): " + p.evaluate())
    reporter.report()
  }
}
