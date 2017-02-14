import java.util.concurrent.TimeUnit

import com.codahale.metrics.{ConsoleReporter, Metric}
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher
import com.netflix.hystrix.strategy.HystrixPlugins
import nl.grons.metrics.scala.Implicits.functionToMetricFilter

/**
  * Created by Ľubomír Varga on 14.2.2017.
  */
trait TestHelperObject extends nl.grons.metrics.scala.DefaultInstrumented {
  // Define a timer metric
  HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry))

  protected val httpRpc = metrics.timer("http-rpc")

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
}
