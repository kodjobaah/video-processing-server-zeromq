import org.javasimon.jmx.JmxReporter
import org.slf4j.LoggerFactory
import com.whatamidoing.utils.ActorUtils
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.ActorRef
import org.zeromq.ZMQ




import org.javasimon.jmx.JmxReporter
import org.zeromq.ZMQ
import java.nio.charset.Charset
import com.whatamidoing.actors.hls.{MonitorConnection, FrameSupervisorHls}
import com.whatamidoing.actors.hls.model.Value.FrameData
import models.Messages.EndTransmission



object VideoProcessingServer {


  val log = LoggerFactory.getLogger("SocketServer")

  def main(args: Array[String]): Unit = {

    val context: ZMQ.Context = ZMQ.context(1)

    val zmqThread = new VideoReceiver(context)
      sys.addShutdownHook({
      println("ShutdownHook called")
      context.term()
      zmqThread.interrupt()
      zmqThread.join
    })


    val reporter: JmxReporter = JmxReporter.forDefaultManager()
      .registerSimons() // add MBean for every Simon
      .registerExistingSimons() // register also already existing ones (ExistingStopwatch in this case)
      .start(); // this performs actual MXBean registration + JmxRegisterCallback is added to manager

    zmqThread.start

  }


}