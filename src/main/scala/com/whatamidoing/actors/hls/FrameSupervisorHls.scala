package com.whatamidoing.actors.hls

import com.whatamidoing.utils.ActorUtils
import com.whatamidoing.utils.ActorUtilsReader

import com.typesafe.config.ConfigFactory

import akka.io.Tcp
import akka.actor._

import spray.can.server.websockets.Sockets
import spray.can.server.websockets.model.Frame
import spray.can.server.websockets.model.OpCode.{ConnectionClose, Text}
import spray.http.HttpRequest
import models.Messages.ProblemsEncoding
import models.Messages.EndTransmission
import models.Messages.EncodeFrame
import akka.actor.SupervisorStrategy.Stop
import com.whatamidoing.actors.hls.model.Value.FrameData
import com.whatamidoing.actors.red5.VideoEncoder

class FrameSupervisorHls(streamId: String) extends Actor with ActorLogging {

  var videoEncoder: ActorRef = _

  val Tag: String = "FrameSupervisor"
  var token: String = _

  var theSender: ActorRef = _

  val ServiceStoppedMessage: String = "SERVICE_STOPPED"


  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 0) {
      case _: Exception =>
        log info "problems with video encoder stopping"
        self ! ProblemsEncoding
        Stop
    }

  override def receive: Receive = {

    case ProblemsEncoding =>
        println("---------- something got wrong---")

    case e:EndTransmission =>
      videoEncoder ! e

    case frame: FrameData =>
      if (videoEncoder == null) {
        val token = frame.authToken
        val res = ActorUtilsReader.getValidToken(token)
        println("toke:" + res)
        if (res.asInstanceOf[List[String]].size > 0) {
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
          val streamName = sn
          val res = ActorUtils.createStream(token, streamName)

          val roomJid = sn + "@muc." + domId + "." + xmppDomain
          ActorUtils.associateRoomWithStream(token, roomJid)
          ActorUtils.createXmppGroup(roomJid, token)
          log debug ("results from creating stream:" + res)
          videoEncoder = context.actorOf(VideoEncoderHls.props(streamName), "videoencoder:" + sn)
        }

      }
      videoEncoder ! EncodeFrame(frame.data,frame.time,frame.sequence)
      case x => log debug ("DEFAULT_MESSAGE:" + x.toString)

  }

}

object FrameSupervisorHls {
  def props(streamId: String) = Props(new FrameSupervisorHls(streamId))

}

