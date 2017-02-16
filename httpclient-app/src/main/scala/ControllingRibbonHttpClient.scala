import sk.httpclient.app.RibbonHttpClient

import scala.util.Try

/**
  * Created by Ľubomír Varga on 10.2.2017.
  */
class ControllingRibbonHttpClient[R, T](servers: String) extends RibbonHttpClient[R, T](servers) {
  /**
    * This is also instance of RibbonHttpClient. This attribute holds another instance (with different types) to same servers.
    */
  val controlClient = new RibbonHttpClient[String, String](servers)

  def deploy(deployTimeInSeconds: Int) = Try {
    this.controlClient.send(ControllingRibbonHttpClient.PORCEDURE_control, s"${ControllingRibbonHttpClient.CONTROL_DEPLOY},$deployTimeInSeconds", classOf[String]).get()
  }.recover { case t: Throwable => t.printStackTrace(); t.getMessage }.get

  def dbDown = Try {
    this.controlClient.send(ControllingRibbonHttpClient.PORCEDURE_control, ControllingRibbonHttpClient.CONTROL_DB_DOWN, classOf[String]).get()
  }.recover { case t: Throwable => t.getMessage }.get

  def overload = Try {
    this.controlClient.send(ControllingRibbonHttpClient.PORCEDURE_control, ControllingRibbonHttpClient.CONTROL_OVERLOAD, classOf[String]).get()
  }.recover { case t: Throwable => t.getMessage }.get

  def ok = Try {
    this.controlClient.send(ControllingRibbonHttpClient.PORCEDURE_control, ControllingRibbonHttpClient.CONTROL_OK, classOf[String]).get()
  }.recover { case t: Throwable => t.getMessage }.get
}

object ControllingRibbonHttpClient {
  val PORCEDURE_getRecord = "/test/record"
  val PORCEDURE_makeCall = "/test/call"
  val PORCEDURE_control = "/test/control"
  /**
    * Simulate deploy. Reboot server. Its starter (run[789].sh script) will restart it after 30 seconds.
    *
    * Server expects number 1 (this constant), than comma and than number of seconds (exit code is used to propagate
    * sleep time, so max byte value is possible!) to sleep before restart.
    */
  val CONTROL_DEPLOY = "1"
  /**
    * Server start responding by exception to all "/test/record" requests.
    */
  val CONTROL_DB_DOWN = "2"
  /**
    * Responses (also exceptional one) are given after 18 seconds of sleep.
    */
  val CONTROL_OVERLOAD = "3"
  /**
    * Make server responding OK. Without delay and with proper content/status code.
    */
  val CONTROL_OK = "4"
}