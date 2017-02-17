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
object TimeoutTestCases extends TestHelperObject[Record, Record] {
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"

  def oneServerTimeoutingLongTime(testName: String, sender: SenderType[Record, Record], client: ControllingRibbonHttpClient[Record, Record]) = {
    (1 to 50).map(_ => client.ok)
    client.overload(45000)
    val results = runTest(20, sender)
    printReport(testName, results)
  }

  def main(args: Array[String]): Unit = {
    val client = new ControllingRibbonHttpClient[Record, Record]("http://localhost:8887,http://localhost:8888,http://localhost:8889")
    // give ipinger time to fill in lbstatistics instance.
    TimeUnit.SECONDS.sleep(1)

    oneServerTimeoutingLongTime("noServerIsRunning - Idempotent", senderIdempotent(client), client)
    //oneServerTimeoutingLongTime("noServerIsRunning - NON-idempotent", senderNormal(client), client)
  }
}
