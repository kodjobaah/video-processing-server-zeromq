package com.whatamidoing.actors.red5.services

import com.xuggle.xuggler.{IContainerFormat, IContainer, ICodec}
import com.xuggle.mediatool.IMediaWriter
import com.xuggle.mediatool.ToolFactory

import java.awt.image._
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import java.awt.geom.AffineTransform
import com.jhlabs.image.{LaplaceFilter, UnsharpFilter}
import java.awt.Graphics2D
import java.io.File
import javax.imageio.ImageIO

class Xuggler(rtmpUrl: String, streamName: String) {


  def SHARPEN3x3:  Array[Float]  = Array(0.0f, -1.0f, 0.0f,-1.0f, 5.0f, -1.0f,0.0f, -1.0f, 0.0f);
// def SHARPEN3x3:  Array[Float]  = Array(0.25f, -2.0f, 0.25f,-2.0f, 10.0f, -2.0f,0.25f, -2.0f, 0.25f);
//def SHARPEN3x3:  Array[Float]  = Array(-1.0f, -1.0f, -1.0f,-1.0f, 9.0f, -1.0f,-1.0f, -1.0f, -1.0f);

  def log = LoggerFactory.getLogger("Xuggler")

  log.info("INSIDE CONSTRUCTOR:" + rtmpUrl + streamName)

  def this() = this("", "")

  //Accessing the constants


  //ToolFactory.makeWriter("rtmp://192.168.0.101:1935/HTTP@FLV/"+streamName)
  //   ToolFactory.makeWriter("rtmp://192.168.1.110:1935/oflaDemo/"+streamName)
  //  val mediaWriter: IMediaWriter =  ToolFactory.makeWriter("rtmp://www.whatamidoing.info:1935/hlsapp/"+streamName)
  val mediaWriter: IMediaWriter = ToolFactory.makeWriter(rtmpUrl + streamName)

  if (mediaWriter.getContainer.isOpened) {
        log info "open before"
  } else {
    log info "not opened beofre"
  }
  log info ("this is mediawrite:"+mediaWriter)
  //mediaWriter.getContainer()
  //mediaWriter.open();
  //mediaWriter.setForceInterleave(true);
  val outFormat: IContainerFormat  = IContainerFormat.make();
  outFormat.setOutputFormat("flv", rtmpUrl+streamName, null);
  val container: IContainer  = mediaWriter.getContainer();
  container.open(rtmpUrl+streamName, IContainer.Type.WRITE, outFormat);
  //mediaWriter.addVideoStream(0, 0, ICodec.ID.CODEC_ID_H264, 352, 288)

  mediaWriter.addVideoStream(0, 0, ICodec.ID.CODEC_ID_FLV1,352, 288)

  if (mediaWriter.getContainer.isOpened) {
    log info "open after"
  } else {
    log info "not after beofre"
  }
  //mediaWriter.getContainer().getContainerFormat().setOutputFormat("flv",streamName,null)
  //mediaWriter.addVideoStream(0, 0, ICodec.ID.CODEC_ID_FLV1,640, 480)

  var startTime: Long = _


  var count = 0

  def transmitBufferedImage(image: BufferedImage,diff: Int) {
    import java.util.concurrent.TimeUnit


    if (count != 0) {
      startTime = startTime + diff
    }


    if (count < 20) {

      startTime = System.currentTimeMillis()
      log.info("CREATING FILE")
      val outputfile = new File("/tmp/image"+count+".jpg")
      ImageIO.write(image, "jpg", outputfile)
      count = count + 1;
    }



    var im = image
    if (im.getType()==BufferedImage.TYPE_3BYTE_BGR)
    {

      im=convertType(im, BufferedImage.TYPE_INT_RGB)
    }


    var kernelWidth = 2
    var kernelHeight = 2

    var xOffset = kernelWidth  / 2
    var yOffset = kernelHeight  / 2

    var newSource: BufferedImage = new BufferedImage(
      im.getWidth + kernelWidth,
    im.getHeight + kernelHeight,
    BufferedImage.TYPE_INT_ARGB)
    var g2: Graphics2D  = newSource.createGraphics()
    g2.drawImage(im, xOffset, yOffset, null)
    g2.dispose()

    //var dstbim: BufferedImage = new BufferedImage(im.getWidth,im.getHeight,BufferedImage.TYPE_INT_RGB)
    val kernel :Kernel = new Kernel(3,3,SHARPEN3x3)

    val cop: ConvolveOp = new ConvolveOp(kernel,ConvolveOp.EDGE_NO_OP,null)
    var dstbim: BufferedImage = cop.filter(im,null)


    if (dstbim.getType()==BufferedImage.TYPE_INT_RGB)
    {

      dstbim=convertType(dstbim, BufferedImage.TYPE_3BYTE_BGR)
    }




    val tx: AffineTransform = AffineTransform.getScaleInstance(-1, 1)
    tx.translate(-dstbim.getWidth(null),0)
    val op: AffineTransformOp  = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
    dstbim = op.filter(dstbim, null);

    if (dstbim.getType()==BufferedImage.TYPE_3BYTE_BGR)
    {

      dstbim=convertType(dstbim, BufferedImage.TYPE_INT_RGB)
    }


    val laplace: LaplaceFilter = new LaplaceFilter()
    var newDest: BufferedImage = null
    newDest = laplace.filter(dstbim,newDest)

    if (newDest.getType()==BufferedImage.TYPE_INT_RGB)
    {
      newDest=convertType(newDest, BufferedImage.TYPE_3BYTE_BGR)
    }

    var dstImage: BufferedImage  = null;
    var newOp: RescaleOp  = new RescaleOp(1.1f, 0.0f, null);
    dstImage = newOp.filter(newDest, null);
    mediaWriter.encodeVideo(0, dstImage, startTime, TimeUnit.MILLISECONDS)


    /*
    var im = image
    val tx: AffineTransform = AffineTransform.getScaleInstance(-1, 1)
    tx.translate(-im.getWidth(null),0)
    val op: AffineTransformOp  = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
    im = op.filter(im, null)

    if (im.getType()==BufferedImage.TYPE_3BYTE_BGR)
    {

      im=convertType(im, BufferedImage.TYPE_INT_RGB)
    }

    val unsharpFilter: UnsharpFilter = new UnsharpFilter()

    var newIm:BufferedImage = null
    newIm = unsharpFilter.filter(im,newIm)

    if (newIm.getType()==BufferedImage.TYPE_INT_RGB)
    {

      newIm=convertType(newIm, BufferedImage.TYPE_3BYTE_BGR)
    }
    mediaWriter.encodeVideo(0, newIm, startTime, TimeUnit.MILLISECONDS)
     */
  }

  def  convertType(src: BufferedImage, t: Int ): BufferedImage =  {
    //log.info("--------------------- TRYING TO CONVERT----")
    val cco: ColorConvertOp =new ColorConvertOp(null)
    val dest: BufferedImage =new BufferedImage(src.getWidth, src.getHeight, t)
    cco.filter(src, dest)
    //log.info("---------------------- FINNISHED CONVERTING")
    dest
  }

  def close() {
    mediaWriter.getContainer.writeHeader()
    mediaWriter.close()
  }

}

object Xuggler {

  val config = ConfigFactory.load()
  val rtmpUrl: String = config.getString("rtmp.url")

  def apply(streamName: String) = new Xuggler(rtmpUrl, streamName)

}
