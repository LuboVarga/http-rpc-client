import java.util.concurrent.TimeUnit

import com.codahale.metrics.ConsoleReporter
import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher
import com.netflix.hystrix.strategy.HystrixPlugins
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import sk.httpclient.app.RibbonHttpClient

import scala.collection.parallel.ForkJoinTaskSupport

/**
  * Created by Ľubomír Varga on 7.2.2017.
  */
object AppMain extends nl.grons.metrics.scala.DefaultInstrumented {
  // Define a timer metric
  HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry))

  private val httpRpc = metrics.timer("http-rpc")

  val reporter: ConsoleReporter = ConsoleReporter
    .forRegistry(metricRegistry)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build
  reporter.start(10, TimeUnit.SECONDS)

  case class Rec @JsonCreator()(
                                 @JsonProperty("name")
                                 name: String,
                                 @JsonProperty("age")
                                 age: Integer,
                                 @JsonProperty("city")
                                 city: String
                               )

  def main(args: Array[String]): Unit = {
    val s = new SynchronizedSummaryStatistics()
    val p = new Percentile(95.0)
    //val r = new RibbonHttpClient[Rec, Rec]("localhost:8887,localhost:8888,localhost:8889")

    val clients = (1 to 4).map(_ => new RibbonHttpClient[Rec, Rec]("localhost:8887,localhost:8888,localhost:8889")).par
    clients.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(50))
    val allDurations = clients.flatMap(r => {
      println("Going to do warm-up.")
      (1 to 200).foreach(i => r.send("aaa", new Rec("", i, ""), classOf[Rec]).get)

      val a = (1 to 100) //.par
      //a.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(10))

      val durations = a.map(i => {
        val start = System.nanoTime()
        val o = httpRpc.time {
          r.send("aaa", new Rec("", i, ""), classOf[Rec]).get
        }
        val end = System.nanoTime()
        val duration = (end - start) / 1000000.0
        println(s"Result of call: ${o.city}; duration: ${duration}ms.")
        //Thread.sleep(20)
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
