package au.csiro.data61.randomwalk.common

import au.csiro.data61.randomwalk.algorithm.{GraphMap, RandomSample}
import com.sun.org.apache.bcel.internal.generic.Type

import scala.collection.mutable
import scala.collection.parallel.{ParMap, ParSeq, ParSet}

/**
  * Created by Hooman on 2018-03-06.
  */
object GraphUtils {

  def computeSecondOrderProbs(config: Params): (mutable.HashMap[(Int, Int), Int], Seq[(Int, Int,
    Double)]) = {
    val vertices = GraphMap.getVertices()
    val edgeIds = new mutable.HashMap[(Int, Int), Int]
    var id = 0
    for (v <- vertices) {
      val neighbors = GraphMap.getNeighbors(v)
      for (e <- neighbors) {
        val dst = e._1
        edgeIds.put((v, dst), id)
        id += 1
      }
    }

    val edges = edgeIds.keySet
    val rSample = RandomSample()
    val soProbs = edges.flatMap { case (prev, curr) =>
      val srcEdge: Int = edgeIds.getOrElse((prev, curr), throw new Exception(s"Edge $prev -> " +
        s"$curr is not found."))
      val prevNeighbors = GraphMap.getNeighbors(prev)
      val currNeighbors = GraphMap.getNeighbors(curr)
      val unNormProbs = rSample.computeSecondOrderWeights(p = config.p, q = config.q, prev,
        prevNeighbors, currNeighbors)
      val sum = unNormProbs.foldLeft(0.0) { case (w1, (_, w2)) => w1 + w2 }
      var probs = Seq.empty[(Int, Int, Double)]
      for (up <- unNormProbs) {
        val dst = up._1
        val w = up._2
        val dstEdge: Int = edgeIds.getOrElse((curr, dst), throw new Exception(s"Edge $curr -> " +
          s"$dst is not found."))
        probs ++= Seq((srcEdge, dstEdge, w / sum))
      }
      probs
    }.toSeq

    (edgeIds, soProbs)

  }

  def computeSecondOrderProbsWithNoId(config: Params): ParMap[(Int, Int, Int), Double] = {

    val vertices = GraphMap.getVertices().par
    val rSample = RandomSample()
    vertices.flatMap { case prev =>
      val neighbors = GraphMap.getNeighbors(prev)
      val prevNeighbors = GraphMap.getNeighbors(prev)
      neighbors.flatMap { case curr =>
        val currNeighbors = GraphMap.getNeighbors(curr._1)
        val unNormProbs = rSample.computeSecondOrderWeights(p = config.p, q = config.q, prev,
          prevNeighbors, currNeighbors)
        val sum = unNormProbs.foldLeft(0.0) { case (w1, (_, w2)) => w1 + w2 }
        var probs = Seq.empty[((Int, Int, Int), Double)]
        for (up <- unNormProbs) {
          val dst = up._1
          val w = up._2
          probs ++= Seq(((prev, curr._1, dst), w / sum))
        }
        probs
      }
    }.toMap
  }

  def computeEmpiricalTransitionProbabilities(walks: ParSeq[Seq[Int]], possibleSteps: ParSet[
    (Int, Int, Int)]): ParSeq[((Int, Int, Int), Double)] = {
    val transitions: ParMap[(Int, Int, Int), Int] = walks.flatMap { case w =>
      var counts = Seq.empty[((Int, Int, Int), Int)]
      for (i <- 0 until w.length - 2) {
        counts ++= Seq(((w(i), w(i + 1), w(i + 2)), 1))
      }
      counts
    }.groupBy(_._1).map { case (k, counts) => (k, counts.foldLeft(0)(_ + _._2)) }

    val mProbs = possibleSteps.toSeq.map { case (prev, curr, dst) =>
      val currNeighbors = GraphMap.getNeighbors(curr)
      var sum: Double = 0
      for (n <- currNeighbors) {
        sum += transitions.getOrElse((prev, curr, n._1), 0)
      }
      val count = transitions.getOrElse((prev, curr, dst), 0).toDouble
      val tp = count / math.max(sum, 1.0)
      ((prev, curr, dst), tp)
    }
    return mProbs
  }

  def computeErrorsMeanAndMax(walks: ParSeq[Seq[Int]], config: Params): (Double, Double) = {

    val errors = computeErrors(walks, config)
    val mean = errors.reduce(_ + _) / errors.size
    val max = errors.max

    (mean, max)
  }

  def computeErrors(walks: ParSeq[Seq[Int]], config: Params): ParSeq[Double] = {
    val tProbs = computeSecondOrderProbsWithNoId(config)

    val mProbs = computeEmpiricalTransitionProbabilities(walks, tProbs.keySet)

    mProbs.map { case (k, mp) =>
      val tp = tProbs.getOrElse(k, throw new Exception(s"Theoretical prob for (${k._1}, ${k._2}, " +
        s"${k._3}) does not exist."))
      math.abs(tp - mp)
    }
  }
}
