package com.whatamidoing.actors.hls.model

import com.whatamidoing.actors.hls.model.Value.SegmentData
import scala.collection.immutable.Range.Double

/**
 * Created by kodjobaah on 08/05/2014.
 */
object PlayListGenerator {
    val m3u3Header = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-ALLOW-CACHE:NO\n"

    def genPlayList(data: List[SegmentData]) : String = {
      val sb: StringBuilder  = new StringBuilder(m3u3Header)
      val head = data.head
      val init = "#EXT-X-TARGETDURATION:20\n#EXT-X-MEDIA-SEQUENCE:"+head.number+"\n"
      sb.append(init)
      for(segmentData <- data) {
        if (!segmentData.segment.isActiveSegment) {


          val duration = segmentData.segment.duration

          val seg = s"""#EXTINF:$duration, segment\n"""
          sb.append(seg)
          val strm = "%s-%s.ts".format(segmentData.streamName,segmentData.number)
          sb.append(strm+"\n")
          /*
          var tm = data
          if (data.reverse.head.segment.isActiveSegment) {
            tm = data.reverse.tail.reverse
          }

          if (segmentData.number == tm.reverse.head.number) {
              sb.append("#EXT-X-ENDLIST\n")
          }
          */
        }
      }
      sb.toString()
    }
}
