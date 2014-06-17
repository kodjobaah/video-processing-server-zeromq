package com.whatamidoing.actors.red5


import com.whatamidoing.utils.ActorUtils
import com.whatamidoing.utils.ActorUtilsReader
import models.Messages._

import com.typesafe.config.ConfigFactory

import akka.io.Tcp
import akka.actor._

import spray.http.HttpRequest
import spray.can.server.websockets.Sockets
import spray.can.server.websockets.model.Frame
import spray.can.server.websockets.model.OpCode.{ConnectionClose, Text}
import spray.http.HttpRequest
import models.Messages.ProblemsEncoding
import models.Messages.EndTransmission
import models.Messages.EncodeFrame
import akka.actor.SupervisorStrategy.{Stop, Escalate}

class FrameSupervisor(streamId: String) extends Actor with ActorLogging {

  var videoEncoder: ActorRef = _

  val Tag: String = "FrameSupervisor"
  var token: String = _

  var theSender: ActorRef = _

  val ServiceStoppedMessage: String = "SERVICE_STOPPED"

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries =0) {
      case _: Exception =>
        log info "problems with video encoder stopping"
        self ! ProblemsEncoding
        Stop
   }

  override def receive: Receive = {

    case ProblemsEncoding =>
      stopProcessingVideo()
      theSender ! Tcp.Close

    case Sockets.Upgraded => // do nothing

    case req: HttpRequest =>
      theSender = sender
      val tokenIn = req.uri.query.get("token").orElse(Option("did-not-receive-token"))
      if (tokenIn.get != "did-not-receive-token") {
        val res = ActorUtilsReader.getValidToken(tokenIn.get)
        println("toke:" + res)
        if (res.asInstanceOf[List[String]].size > 0) {
          token = tokenIn.get
          var sn = token + "--" + streamId
          ActorUtils.invalidateAllStreams(token)
          val userInformation = ActorUtilsReader.fetchUserInformation(token)
          var config = ConfigFactory.load()
          val xmppDomain = config.getString("xmpp.domain")
          var domId = ""
          if (userInformation.domId == None) {
            domId = java.util.UUID.randomUUID.toString
            ActorUtils.updateUserInformation(token, domId)
            val domain = domId + "." + xmppDomain
            ActorUtils.createXmppDomain(domain)
          } else {
            domId = userInformation.domId.get
          }

          val streamName = sn + ".flv"
          //val streamName = sn + ".ts"
          //val streamName = sn
          // val streamName = sn

          val res = ActorUtils.createStream(token, streamName)

          val roomJid = sn + "@muc." + domId + "." + xmppDomain
          ActorUtils.associateRoomWithStream(token, roomJid)
          ActorUtils.createXmppGroup(roomJid, token)

          log debug ("results from creating stream:" + res)

            videoEncoder = context.actorOf(VideoEncoder.props(streamName), "videoencoder:" + sn)
            // videoEncoder ! EncodeFrame(message);
            sender ! Sockets.UpgradeServer(Sockets.acceptAllFunction(req), self)


        } else {
          sender ! Tcp.Close
        }
      }

    case f@Frame(fin, rsv, Text, maskingKey, data) =>

      if (f.stringData == ServiceStoppedMessage) {
        sender ! Tcp.Close
        stopProcessingVideo()
      } else {
        if (videoEncoder != null)
          videoEncoder ! EncodeFrame(f.stringData)
      }


    case f@Frame(fin, rsv, ConnectionClose, maskingKey, data) =>
      stopProcessingVideo()


    case err: Tcp.ErrorClosed =>
        stopProcessingVideo()

    case x: Tcp.ConnectionClosed =>
      stopProcessingVideo()
      println("received conection closed:" + x)

    case x => log debug ("DEFAULT_MESSAGE:" + x.toString)


  }

  def stopProcessingVideo() = {
    log.debug("ACTOR FOUND STOPPING VIDEOENCODER[:" + token + "]")

    if (videoEncoder != null) {
      videoEncoder ! EndTransmission
      context.stop(videoEncoder)

      var streamName = ActorUtilsReader.findActiveStreamForToken(token)
      log debug ("stream name:" + streamName)
      if (!streamName.isEmpty) {
        val roomJid = ActorUtilsReader.getRoomJid(token)
        log debug ("ROOM JID [:" + roomJid + "]")
        ActorUtils.removeRoom(roomJid)
        ActorUtils.closeStream(streamName)
      }
      log debug ("ACTOR FOUND STOPPING SELF-FRAMESUPERVISOR[:" + token + "]")
      context stop self
      videoEncoder = null
    }
  }

}

object FrameSupervisor {
 def props(streamId: String) = Props(new FrameSupervisor(streamId))

}

