import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import com.whatamidoing.actors.hls.model.Value.FrameData
import com.whatamidoing.actors.hls.{FrameSupervisorHls, MonitorConnection}
import com.whatamidoing.utils.ActorUtils
import java.nio.charset.Charset
import models.Messages.EndTransmission
import org.zeromq.ZMQ

/**
 * Created by kodjobaah on 13/06/2014.
 */
class VideoReceiver(context: ZMQ.Context) extends Thread {

  val cl = ActorUtils.getClass.getClassLoader
  val priority = ActorSystem("priority", ConfigFactory.load(), cl)
  val config = ConfigFactory.load()
  val system = ActorSystem("videoprocessing")
  val serverName = config.getString("server.name")
  val serverPort = config.getInt("server.port")
  var framesupervisors: Map[String, ActorRef] = Map()

  override def run() {
    //  Socket to talk to clients
    val socket: ZMQ.Socket = context.socket(ZMQ.REP)
    socket.bind("tcp://*:12345")

    socket.monitor("inproc://monitor.req", ZMQ.EVENT_ALL)
    val monitor = new MonitorConnection(context)
    monitor.start()
    var message: List[String] = List()

    while (!Thread.currentThread.isInterrupted) {
      message = retrieveRequest(socket)
      println("my scala receive:head" + message.head)
      performOperation(message, socket)
    }
    socket.close()
  }

  def performOperation(message: List[String], socket: ZMQ.Socket) {
    var streamId: String = message.head
    val requestHeader = RequestHeader(message.head, message.length)

    requestHeader match {
      case RequestHeader("CONNECT", 1) =>
        socket.send("ALIVE")

      case RequestHeader("END_STREAM", 2) =>
        streamId = message.tail.head
        val framesupervisor = framesupervisors get streamId
        if (framesupervisor != None) {
          println("size before remvoe:" + framesupervisors.size)
          framesupervisor.get ! EndTransmission()
          framesupervisors -= streamId
          println("size afer remvoe:" + framesupervisors.size)
        }
        socket.send("STREAM_ENDED")

      case _ =>
        var msg = message.tail
        if (streamId.length < 1) {
          streamId = java.util.UUID.randomUUID.toString
          val fs = priority.actorOf(FrameSupervisorHls.props(streamId).withMailbox("priority-dispatch"), "frameSupervisor-" + streamId)
          framesupervisors += streamId -> fs
        }

        val framesupervisor = framesupervisors get streamId
        if (framesupervisor == None) {
          socket.send("terminate")
          //socket.close()
        } else {
          val authToken = msg.head
          msg = msg.tail
          val sequence = msg.head.toInt
          msg = msg.tail
          val time = msg.head.toInt
          msg = msg.tail
          val data = msg.head

          val f = FrameData(streamId, authToken, sequence, time, data)
          framesupervisor.get ! f
          socket.send(streamId)

        }

    }
  }

  def retrieveRequest(socket: ZMQ.Socket): List[String] = {
    var message: List[String] = List()
    var more: Boolean = false

    do {
      var reply: Array[Byte] = socket.recv()
      println("idenitti:" + new String(socket.getIdentity))
      val mess = new String(reply, Charset.forName("UTF-8"))
      message = message ::: List(mess)
      more = socket.hasReceiveMore
    } while (more)
    message
  }

  case class RequestHeader(message: String, length: Int)


}
