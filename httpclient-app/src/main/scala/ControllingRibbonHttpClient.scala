import sk.httpclient.app.RibbonHttpClient

/**
  * Created by Ľubomír Varga on 10.2.2017.
  */
class ControllingRibbonHttpClient[R, T](servers: String) extends RibbonHttpClient[R, T](servers) {

  /**
    * This is also instance of RibbonHttpClient. This attribute holds another instance (with different types) to same servers.
    */
  val controlClient = new RibbonHttpClient[String, String](servers)

  val PORCEDURE_getRecord = "/test/record"
  val PORCEDURE_makeCall = "/test/call"
  val PORCEDURE_control = "/test/control"
  /**
    * Simulate deploy. Reboot server. Its starter (run[789].sh script) will restart it after 30 seconds.
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

  def deploy = this.controlClient.send(PORCEDURE_control, CONTROL_DEPLOY, classOf[String])

  def dbDown = this.controlClient.send(PORCEDURE_control, CONTROL_DB_DOWN, classOf[String])

  def overload = this.controlClient.send(PORCEDURE_control, CONTROL_OVERLOAD, classOf[String])

  def ok = this.controlClient.send(PORCEDURE_control, CONTROL_OK, classOf[String])
}
