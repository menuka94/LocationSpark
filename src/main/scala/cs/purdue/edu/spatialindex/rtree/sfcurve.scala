package cs.purdue.edu.spatialindex.rtree

import com.google.common.base.{Functions, Function}
import com.google.common.collect.ImmutableList
import com.google.uzaygezen.core.BacktrackingQueryBuilder
import com.google.uzaygezen.core.BitVector
import com.google.uzaygezen.core.BitVectorFactories
import com.google.uzaygezen.core.CompactHilbertCurve
import com.google.uzaygezen.core.FilteredIndexRange
import com.google.uzaygezen.core.LongContent
import com.google.uzaygezen.core.PlainFilterCombiner
import com.google.uzaygezen.core.Query
import com.google.uzaygezen.core.QueryBuilder
import com.google.uzaygezen.core.RegionInspector
import com.google.uzaygezen.core.SimpleRegionInspector
import com.google.uzaygezen.core.ZoomingSpaceVisitorAdapter
import com.google.uzaygezen.core.ranges.LongRange
import com.google.uzaygezen.core.ranges.LongRangeHome


/**
 * Created by merlin on 1/21/16.
 */

/**
 * this class is used to map data to space filling curve data
 */

trait SpaceFillingCurve2D {
  def toIndex(x: Double, y: Double): Long

  def toPoint(i: Long): (Double, Double)

  def toRanges(xmin: Double, ymin: Double, xmax: Double, ymax: Double): Seq[(Long, Long)]
}

class HilbertCurve2D(resolution: Int) extends SpaceFillingCurve2D {
  val precision = math.pow(2, resolution).toLong
  val chc = new CompactHilbertCurve(Array(resolution, resolution))

  final def getNormalizedLongitude(x: Double): Long =
    ((x + 180) * (precision - 1) / 360d).toLong
  //    (x * (precision - 1) / 360d).toLong

  final def getNormalizedLatitude(y: Double): Long =
    ((y + 90) * (precision - 1) / 180d).toLong
  //    (y * (precision - 1) / 180d).toLong

  final def setNormalizedLatitude(latNormal: Long) = {
    if (!(latNormal >= 0 && latNormal <= precision))
      throw new NumberFormatException("Normalized latitude must be greater than 0 and less than the maximum precision")

    latNormal * 180d / (precision - 1)
  }

  final def setNormalizedLongitude(lonNormal: Long) = {
    if (!(lonNormal >= 0 && lonNormal <= precision))
      throw new NumberFormatException("Normalized longitude must be greater than 0 and less than the maximum precision")

    lonNormal * 360d / (precision - 1)
  }


  def toIndex(x: Double, y: Double): Long = {
    val normX = getNormalizedLongitude(x)
    val normY = getNormalizedLatitude(y)
    val p =
      Array[BitVector](
        BitVectorFactories.OPTIMAL(resolution),
        BitVectorFactories.OPTIMAL(resolution)
      )

    p(0).copyFrom(normX)
    p(1).copyFrom(normY)

    val hilbert = BitVectorFactories.OPTIMAL.apply(resolution * 2)

    chc.index(p, 0, hilbert)
    hilbert.toLong
  }

  def toPoint(i: Long): (Double, Double) = {
    val h = BitVectorFactories.OPTIMAL.apply(resolution * 2)
    h.copyFrom(i)
    val p =
      Array[BitVector](
        BitVectorFactories.OPTIMAL(resolution),
        BitVectorFactories.OPTIMAL(resolution)
      )

    chc.indexInverse(h, p)

    val x = setNormalizedLongitude(p(0).toLong) - 180
    val y = setNormalizedLatitude(p(1).toLong) - 90
    (x, y)
  }

  def toRanges(xmin: Double, ymin: Double, xmax: Double, ymax: Double): Seq[(Long, Long)] = {
    val min = (xmin, ymin)
    val max = (xmax, ymax)

    var chc = new CompactHilbertCurve(Array[Int](resolution, resolution))
    var region = new java.util.ArrayList[LongRange]()

    val minNormalizedLongitude = getNormalizedLongitude(xmin)
    val minNormalizedLatitude = getNormalizedLatitude(ymin)

    val maxNormalizedLongitude = getNormalizedLongitude(xmax)
    val maxNormalizedLatitude = getNormalizedLatitude(ymax)

    region.add(LongRange.of(minNormalizedLongitude, maxNormalizedLongitude))
    region.add(LongRange.of(minNormalizedLatitude, maxNormalizedLatitude))

    var zero = new LongContent(0L)
    var LongRangeIDFunction: Function[LongRange, LongRange] = Functions.identity()

    var inspector: RegionInspector[LongRange, LongContent] =
      SimpleRegionInspector.create(

        ImmutableList.of(region),
        new LongContent(1L),
        LongRangeIDFunction,
        LongRangeHome.INSTANCE,
        zero
      )

    var combiner =
      new PlainFilterCombiner[LongRange, java.lang.Long, LongContent, LongRange](LongRange.of(0, 1))

    var queryBuilder: QueryBuilder[LongRange, LongRange] = BacktrackingQueryBuilder.create(inspector, combiner, Int.MaxValue, true, LongRangeHome.INSTANCE, zero)

    chc.accept(new ZoomingSpaceVisitorAdapter(chc, queryBuilder))

    var query: Query[LongRange, LongRange] = queryBuilder.get()

    var ranges: java.util.List[FilteredIndexRange[LongRange, LongRange]] = query.getFilteredIndexRanges()

    //result
    var result = List[(Long, Long)]()
    val itr = ranges.iterator

    while (itr.hasNext()) {
      var l = itr.next()
      result = (
        l.getIndexRange().getStart().asInstanceOf[Long],
        l.getIndexRange().getEnd().asInstanceOf[Long]
      ) :: result
    }
    result
  }
}

