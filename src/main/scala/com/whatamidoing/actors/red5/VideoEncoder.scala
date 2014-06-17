package com.whatamidoing.actors.red5

import akka.actor.{ActorRef, ActorLogging, Actor, Props}

import com.whatamidoing.actors.red5.services.Xuggler
import models.Messages._

import com.whatamidoing.utils._
import spray.http.parser.HttpParser

class VideoEncoder(streamName: String) extends Actor with ActorLogging {

  def nameOfStream = streamName
  var xuggler: Xuggler =_


  try {
   xuggler = Xuggler(streamName)

  } catch {
    case iae: java.lang.IllegalArgumentException =>
      log info "problems occured should be stopping:"
      throw new IllegalArgumentException("could not connect to red5")
  }

  override def receive: Receive = {

    case fm: EncodeFrame =>
      import sun.misc.BASE64Decoder
      try {


        val frame = fm.frame
        val diff = fm.time
        log debug("size of frame with time stamp:"+frame.length+"] timestamp["+diff+"]")

        val base64: BASE64Decoder = new BASE64Decoder()
        val bytes64: Array[Byte] = base64.decodeBuffer(frame)
        /*
              val compressBytes64: Array[Byte] = base64.decodeBuffer(frame)

        import com.waid.compress.ArithmeticCodeCompression

        val acc : ArithmeticCodeCompression = new ArithmeticCodeCompression()
        val uncompressBytes64: Array[Byte] = acc.decompress(compressBytes64)
              val zippedData: Array[Byte] = base64.decodeBuffer(new String(uncompressBytes64,"UTF-8"))

        val uncompressed: String  = Compressor.decompress(zippedData)
        */
        val uncompressed: String  = Compressor.decompress(bytes64)

        val newBytes64: Array[Byte] = base64.decodeBuffer(uncompressed)

        import java.io.ByteArrayInputStream
        val bais: ByteArrayInputStream = new ByteArrayInputStream(newBytes64)

        import javax.imageio.ImageIO
        val bufferedImage = ImageIO.read(bais)
        //Logger("MyApp").info("--converted buffered image:" + bufferedImage)
        xuggler.transmitBufferedImage(bufferedImage,diff)
      } catch {
        case ex: Throwable =>
          println("PROBLEMS SHOULD STOP:"+ex)
          sender ! ProblemsEncoding

      }

    case EndTransmission =>
      if (xuggler != null) {
        xuggler.close()
        xuggler = null
      }


  }

  override def postStop() {
    if (xuggler != null) {
      xuggler.close()
    }

  }

}

object VideoEncoder {


  def props(streamName: String) : Props= {
      Props(classOf[VideoEncoder],streamName)
  }

}