package com.whatamidoing.actors.hls

import akka.actor.{Props, ActorRef, ActorLogging, Actor}

import models.Messages._

import com.whatamidoing.utils._
import com.whatamidoing.actors.hls.model.Value.{FrameData, AddToSegment}
import spray.json.{JsonParser, DefaultJsonProtocol, JsObject}



class VideoEncoderHls(streamName: String) extends Actor with ActorLogging {


  var segmentor: ActorRef = null



  /*
  object FrameDataJsonProtocol extends DefaultJsonProtocol {
    implicit val jsonFrameData = jsonFormat2(FrameData)
  }
  */


  override def receive: Receive = {

    case fr: EncodeFrame =>
      import sun.misc.BASE64Decoder
      try {


        /*
       //log info ("Frame:"+frameWithTimeStamp)
       val jsonFrame = JsonParser(frameWithTimeStamp)

       import FrameDataJsonProtocol._
       val frameData = jsonFrame.convertTo[FrameData]
        val frame = frameData.data
        val diff = frameData.time

         */
        val frame = fr.frame
        val diff = fr.time

        //log info("size of frame with time stamp:"+frameData+"] timestamp["+diff+"]")

        val base64: BASE64Decoder = new BASE64Decoder()
        val bytes64: Array[Byte] = base64.decodeBuffer(frame)
        val uncompressed: String  = Compressor.decompress(bytes64)

        val newBytes64: Array[Byte] = base64.decodeBuffer(uncompressed)

        import java.io.ByteArrayInputStream
        val bais: ByteArrayInputStream = new ByteArrayInputStream(newBytes64)
        import javax.imageio.ImageIO
        val bufferedImage = ImageIO.read(bais)

        if (segmentor == null) {
          segmentor =context.actorOf(Segmentor.props(streamName), "segmentor:" + streamName)
        }
        segmentor ! AddToSegment(bufferedImage,diff)

      } catch {
        case ex: Throwable =>
          println("PROBLEMS SHOULD STOP:"+ex)
          sender ! ProblemsEncoding

      }

    case e: EndTransmission =>
        segmentor != e
        context.stop(segmentor)
        segmentor = null

  }

  override def postStop() {

  }

}

object VideoEncoderHls {


  def props(streamName: String) : Props= {
    Props(classOf[VideoEncoderHls],streamName)
  }

}

