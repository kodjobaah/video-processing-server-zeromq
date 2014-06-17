package com.whatamidoing.actors.hls

import akka.actor.{ActorLogging, Actor, Props}

import models.Messages._

import com.whatamidoing.actors.hls.model.Value.{SegmentInfo, SegmentData, TooManyActiveStreams, AddToSegment}
import com.typesafe.config.ConfigFactory
import org.greencheek.spray.cache.memcached.MemcachedCache
import com.whatamidoing.actors.hls.model.PlayListGenerator
import scala.concurrent.ExecutionContext
import com.redis.RedisClient

class Segmentor(val streamName: String) extends Actor with ActorLogging {


  import Segmentor.redisPort
  import Segmentor.redisServer
  import Segmentor.segmentTime

  val redisClient = new RedisClient(redisServer, redisPort)

  var segments: List[SegmentData] = List()
  var createSegment: List[SegmentInfo] = List()
  var segCounter = 1
  var time = 0;
  var prevSegmentCount = 0

  override def receive: Receive = {

    case AddToSegment(image, ts) =>
      val actSeg: Option[SegmentData] = getActiveSegment()

      var activeSegment: Segment = null
      if (actSeg == None) {
        activeSegment = Segment(streamName + "-" + segCounter + ".ts")
        activeSegment.init()
        val segData = SegmentData(activeSegment, segCounter, streamName)
        segCounter = segCounter + 1
        segments = segments :+ segData
      } else {
        activeSegment = actSeg.get.segment
      }
      activeSegment.addImage(image, ts)

      time = ts + time;
      log.info("total time of frames received:" + time)
      if ((time / 1000) > segmentTime) {
        activeSegment.duration = time / 1000
        activeSegment.close()
        time = 0;
        log.info("reseting the time:")
      }


      if (segments.size >= Segmentor.playListSize) {

          //First Time Creating playlist
          if ((segments.size == Segmentor.playListSize)
            && (segments.size == (segCounter -1)
            && (getActiveSegment() == None)
            )) {
              redisClient.set("PlayList-" + streamName, PlayListGenerator.genPlayList(segments))
              log.info("Updated Memcache-- For the First Time:PlayList-" + streamName)

          } else if (getActiveSegment() == None) {
            val toRemove = segments.head
            segments = segments.tail
            //We need to create a new playlist
            if ((toRemove.number % Segmentor.playListSize) == 0) {
              redisClient.set("PlayList-" + streamName, PlayListGenerator.genPlayList(segments))
              log.info("Updated Memcache:PlayList-" + streamName)

            }
          }
      }

//Check to see if we should create a new segment

    case EndTransmission =>
      val actSeg: Option[SegmentData] = getActiveSegment()

      if (actSeg != None) {
        actSeg.get.segment.close()
        actSeg.get.segment.removeSegment
      }

  }


  def getActiveSegment(): Option[SegmentData] = {

    val activeSegments: List[SegmentData] = for (p <- segments; if p.segment.activeSegment == true) yield (p)

    var activeSegment: Option[SegmentData] = None

    if (activeSegments.size > 1) throw TooManyActiveStreams("NUMBER OF ACTIVE STREAMS=" + activeSegments.size)

    if (activeSegments.size == 1) {
      activeSegment = Option(activeSegments.head)
    }

    activeSegment
  }

}

object Segmentor {

  val config = ConfigFactory.load()
  val playListSize: Int = config.getInt("playlist.size")
  val redisServer = config.getString("redis.server")
  val redisPort = config.getInt("redis.port")
  val segmentTime = config.getInt("segment.time")


  def props(streamName: String): Props = {
    Props(classOf[Segmentor], streamName)
  }

}