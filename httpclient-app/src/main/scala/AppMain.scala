import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import sk.httpclient.app.RibbonHttpClient

import scala.collection.parallel.ForkJoinTaskSupport

/**
  * Created by Ľubomír Varga on 7.2.2017.
  */
object AppMain {

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

      val a = (1 to 100)//.par
      //a.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(10))

      val durations = a.map(i => {
        val start = System.currentTimeMillis()
        val aaa = r.send("aaa", new Rec("", i, ""), classOf[Rec])
        val o = aaa.get
        val end = System.currentTimeMillis()
        val duration = end - start
        println(s"Result of call: ${o.city}; duration: ${duration}ms.")
        //Thread.sleep(20)
        s.addValue(duration)
        duration
      })
      durations
    })
    p.setData(allDurations.map(_.toDouble).toArray)
    println("Statistics=" + s)
    println("95 percentile: " + p.evaluate(0.95))
  }
}
