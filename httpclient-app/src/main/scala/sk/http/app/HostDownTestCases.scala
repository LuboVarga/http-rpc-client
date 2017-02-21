package sk.http.app

import java.util.concurrent.TimeUnit

import sk.httpclient.app.Record

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
object HostDownTestCases extends TestHelperObject[Record, Record] {
  val servers = "http://localhost:8887,http://localhost:8888,http://localhost:8889"
  type R = Record
  type T = Record

  def onlyOneServerRunningTest(testName: String, sender: SenderType[R, T]) = {
    val results = runTest(250, sender)
    printReport(testName, results)
  }

  def serversStartingUpTest(testName: String, sender: SenderType[R, T], client: ControllingRibbonHttpClient[R, T]) = {
    // turn off servers for beginning...
    client.deploy(10)
    client.deploy(15)
    client.deploy(20)
    ??? // TODO implement
  }

  def serversTurningOff(testName: String, sender: SenderType[R, T], client: ControllingRibbonHttpClient[R, T]) = {
    val resultBefore = runTest(250, sender)
    client.deploy(45)
    val start = System.currentTimeMillis()
    val resultOneDown = runTest(250, sender)
    //val resultBefore: Seq[Try[Rec]] = runTest(250, sender)
    // TODO printReport(testName, results)
    ??? // TODO implement parallel shutting of servers down
  }

  def noServerIsRunning(testName: String, sender: SenderType[R, T]) = {
    val results = runTest(250, sender)
    printReport(testName, results)
  }

  def main(args: Array[String]): Unit = {
    val client = new ControllingRibbonHttpClient[R, T]("http://localhost:8887,http://localhost:8888,http://localhost:8889")
    val clientAllOff = new ControllingRibbonHttpClient[R, T]("http://localhost:554,http://localhost:555,http://localhost:556")
    val clientOneRunningServer = new ControllingRibbonHttpClient[R, T]("http://localhost:555,http://localhost:8888,http://localhost:556")
    // give ipinger time to fill in lbstatistics instance.
    TimeUnit.SECONDS.sleep(1)

    noServerIsRunning("noServerIsRunning - Idempotent", senderIdempotent(clientAllOff))
    noServerIsRunning("noServerIsRunning - NON-idempotent", senderNormal(clientAllOff))

    onlyOneServerRunningTest("onlyOneServerRunningTest - Idempotent", senderIdempotent(clientOneRunningServer))
    onlyOneServerRunningTest("onlyOneServerRunningTest - NON-idempotent", senderNormal(clientOneRunningServer))

    serversStartingUpTest("serversStartingUpTest - Idempotent", senderIdempotent(client), client)
    serversStartingUpTest("serversStartingUpTest - NON-idempotent", senderNormal(client), client)

    serversTurningOff("serversTurningOff - Idempotent", senderIdempotent(client), client)
    serversTurningOff("serversTurningOff - NON-idempotent", senderNormal(client), client)
  }
}
