package sk.http.app

import java.util.concurrent.TimeUnit

import com.netflix.hystrix.Hystrix
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher
import com.netflix.hystrix.strategy.HystrixPlugins
import io.netty.util.ResourceLeakDetector

/**
  * https://confluence.nike.sk/display/VVP/RPC+projekt?focusedCommentId=11535760#comment-11535760
  *
  * Test Case, seriovo volane requesty (idempotentne|neidempotentne):
  * Service Timeout test case:
  * <ul>
  * <li>na 1 z N serverov bude sluzba odpovedať až po dlhšom (timeout trigerujúcom) čase. Ine sluzby (endpointy) serveru budu fungovat
  * a aj timeoutujuca sluzba bude na ostatnych serveroch fungovat (ziaden failnuty request | failnuty req s timeout exception)</li>
  * <li>na M z N serverov bude sluzba odpovedať až po dlhšom (timeout trigerujúcom) čase. Circuit breaker sa otvori pre danu sluzbu,
  * ostatne budu fungovat dalej bez problemov (failnute req s Circuit Breaker exception | failnute req s Circuit Breaker
  * exception)</li>
  * <li>na všetkých serverch bude sluzba odpovedať až po dlhšom (timeout trigerujúcom) čase. Circuit breaker sa otvori pre danu sluzbu.
  * Sluzba zacne po case fungovat. Circuit breaker sa pre danu sluzbu zatvori (failujuce req zacnu prechadzat | failujuce req zacnu
  * prechadzat)</li>
  * </ul>
  *
  * We always assume that three servers are running at localhost:888[789]
  *
  * Created by Ľubomír Varga on 14.2.2017.
  */
object TimeoutTestCases extends TestHelperObject[Record, Record] {
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"

  def someOfServersTimeouting(testName: String, numberOfTestCalls: Int, sender: SenderType[Record, Record], client: ControllingRibbonHttpClient[Record, Record], numberOfOkServers: Int) = {
    //setup:
    {
      // make all servers ok (clear exceptions, etc).
      (1 to 20).map(_ => client.ok)
      // make all servers timeout/overload
      (1 to 20).map(_ => client.overload(45000))
      // make desired number of servers respond after about 2 ms.
      (1 to numberOfOkServers).map(_ => client.overload(2))
    }
    val start = System.currentTimeMillis()
    val results = runTest(numberOfTestCalls, sender)
    val end = System.currentTimeMillis();

    TimeUnit.SECONDS.sleep(1)
    printReport(testName, results)
    println(s"Total runtime (wall clock) was ${(end - start)} ms.")
  }

  def allOfServersTimeoutingStartWorkingLater(testName: String, sender: SenderType[Record, Record], client: ControllingRibbonHttpClient[Record, Record]) = {
    //setup:
    {
      // make all servers ok (clear exceptions, etc).
      (1 to 20).map(_ => client.ok)
      // make all servers timeout/overload
      (1 to 20).map(_ => client.overload(45000))
    }
    val start = System.currentTimeMillis()
    val resultsStart = runTest(10, sender)
    // make one more servers respond after about 2 ms.
    client.overload(2)
    val resultsRun2 = resultsStart ++ runTest(50, sender)
    client.overload(2)
    val resultsRun3 = resultsRun2 ++ runTest(50, sender)
    client.overload(2)
    val resultsRun4 = resultsRun3 ++ runTest(50, sender)
    (1 to 15).map(_ => client.overload(2))
    val results = resultsRun4 ++ runTest(50, sender)
    val end = System.currentTimeMillis();
    TimeUnit.SECONDS.sleep(1)
    printReport(testName, results)
    println(s"Total runtime (wall clock) was ${(end - start)} ms.")
  }

  def main(args: Array[String]): Unit = {
    val client = new ControllingRibbonHttpClient[Record, Record](servers)
    // give ipinger time to fill in lbstatistics instance.
    TimeUnit.SECONDS.sleep(1)
    (1 to 50).map(_ => client.ok)

    val a = runTest(5, senderIdempotent(client))
    printReport("DUMMY TEST, WARMUP", a)

    someOfServersTimeouting("one server time-outing - NON-idempotent", 210, senderNormal(client), client, 2)
    reset()
    someOfServersTimeouting("one server time-outing - Idempotent", 180, senderIdempotent(client), client, 2)
    reset()

    someOfServersTimeouting("two server time-outing - NON-idempotent", 120, senderNormal(client), client, 1)
    reset()
    someOfServersTimeouting("two server time-outing - Idempotent", 170, senderIdempotent(client), client, 1)
    reset()

    allOfServersTimeoutingStartWorkingLater("servers slowly stops time-outing - Idempotent", senderIdempotent(client), client)
    reset()
    allOfServersTimeoutingStartWorkingLater("servers slowly stops time-outing - NON-idempotent", senderNormal(client), client)
  }

  def reset() = {
    TimeUnit.MILLISECONDS.sleep(500)
    import scala.collection.JavaConversions._
    Hystrix.reset(7, TimeUnit.SECONDS)
    TimeUnit.MILLISECONDS.sleep(500)
    metricRegistry.getNames.map(metricRegistry.remove(_))
    TimeUnit.MILLISECONDS.sleep(500)
    HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry))
    TimeUnit.MILLISECONDS.sleep(500)
  }
}
