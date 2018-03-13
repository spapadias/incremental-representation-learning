package au.csiro.data61.randomwalk.common

import java.io.{BufferedWriter, File, FileWriter}

import better.files._

import scala.collection.mutable
import scala.collection.parallel.ParSeq
import scala.io.Source
import scala.util.Try


/**
  * Created by Hooman on 2018-02-16.
  */
case class FileManager(config: Params) {


  def readFromFile(directed: Boolean): ParSeq[(Int, Seq[(Int, Float)])] = {
    val lines = Source.fromFile(config.input).getLines.toArray.par

    lines.flatMap { triplet =>
      val parts = triplet.split("\\s+")
      // if the weights are not specified it sets it to 1.0

      val weight = config.weighted && parts.length > 2 match {
        case true => Try(parts.last.toFloat).getOrElse(1.0f)
        case false => 1.0f
      }

      val (src, dst) = (parts.head.toInt, parts(1).toInt)
      if (directed) {
        Seq((src, Seq((dst, weight))), (dst, Seq.empty[(Int, Float)]))
      } else {
        Seq((src, Seq((dst, weight))), (dst, Seq((src, weight))))
      }
    }.groupBy(_._1).map { case (src, edges) =>
      val neighbors = edges.foldLeft(Seq.empty[(Int, Float)])(_ ++ _._2)
      (src, neighbors)
    }.toSeq
  }

  def readJsonFile(): ParSeq[(String, String, Int)] = {
    val lines = Source.fromFile(config.input).getLines.toList.par
    val toRemove = "[\"]".toSet
    val toRemove2 = ",".toSet
    lines.flatMap { case l =>
      val fl = l.filterNot(toRemove).split(", ")
      if (fl.length > 1) {
        val date = fl(2).filterNot(toRemove2) match {
          case "null" => 0
          case other => other.toInt
        }
        Seq((fl(0), fl(1), date))
      } else {
        Seq.empty[(String, String, Int)]
      }
    }
  }

  def readEdgeList(): Seq[(Int, Int)] = {
    val lines = Source.fromFile(config.input).getLines.toArray

    lines.flatMap { triplet =>
      val parts = triplet.split("\\s+")

      Seq((parts.head.toInt, parts(1).toInt))
    }

  }

  def saveProbs(probs: Seq[Seq[Double]]): Unit = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.probSuffix}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(probs.map(array => array.map(a => f"$a%1.4f").mkString("\t")).mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveNumSteps(vertices: Seq[Int], numSteps: Array[Array[Int]], suffix: String): Unit = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${config.rrType}-$suffix-wl${
      config.walkLength
    }-nw${config.numWalks}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(s"${vertices.mkString("\t")}\n")
    for (steps <- numSteps) {
      bw.write(s"${steps.mkString("\t")}\n")
    }
    bw.flush()
    bw.close()

  }

  def saveComputations(numSteps: Array[Array[Int]], suffix: String): Unit = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${config.rrType}-$suffix-wl${
      config.walkLength
    }-nw${config.numWalks}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    for (steps <- numSteps) {
      bw.write(s"${steps.mkString("\t")}\n")
    }
    bw.flush()
    bw.close()

  }

  def savePaths(paths: ParSeq[Seq[Int]], suffix: String): ParSeq[Seq[Int]] = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${config.cmd}-$suffix.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(paths.map { case (path) =>
      val pathString = path.mkString("\t")
      s"$pathString"
    }.mkString("\n"))
    bw.flush()
    bw.close()
    paths
  }

  def saveEdgeList(edges: Seq[(Int, (Int, Float))], suffix: String) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${config.rrType}-$suffix.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(edges.map { case (edge) =>
      s"${edge._1}\t${edge._2._1}"
    }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def savePaths(paths: ParSeq[Seq[Int]]): ParSeq[Seq[Int]] = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.pathSuffix}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(paths.map {
      case (path) =>
        val pathString = path.mkString("\t")
        s"$pathString"
    }.mkString("\n"))
    bw.flush()
    bw.close()
    paths
  }

  def saveCounts(counts: Seq[(Int, (Int, Int))]) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.countsSuffix}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(counts.sortWith(_._2._2 > _._2._2).map {
      case (vId, (count, occurs)) =>
        s"$vId\t$count\t$occurs"
    }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveDegrees(degrees: Seq[(Int, Int)]) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.degreeSuffix}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(degrees.map { case (v, d) => s"$v\t$d" }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveSecondOrderProbs(edgeIds: mutable.HashMap[(Int, Int), Int], probs: Seq[(Int, Int,
    Double)]) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.edgeIds}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(edgeIds.map { case ((src, dst), id) => s"$src\t$dst\t$id" }.mkString("\n"))
    bw.flush()
    bw.close()

    val file2 = new File(s"${config.output}/${Property.soProbs}.txt")
    val bw2 = new BufferedWriter(new FileWriter(file2))
    bw2.write(probs.map { case (sId, dId, p) => s"$sId\t$dId\t$p" }.mkString("\n"))
    bw2.flush()
    bw2.close()
  }

  def saveAffecteds(afs: Seq[(Int, Array[Int])]) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/${Property.affecteds}.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(afs.map { case (vId, af) => s"$vId\t${af.mkString("\t")}" }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveCoAuthors(tuples: List[(String, String, Int)]) = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/coauthors.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(tuples.par.map { case (a1, a2, year) => s"$a1,$a2,$year" }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveIds(authors: ParSeq[(String, Int)]): Unit = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/authors-ids.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(authors.map { case (a, id) => s"$a,$id" }.mkString("\n"))
    bw.flush()
    bw.close()
  }

  def saveCoAuthors(coauthors: ParSeq[(Int, Int, Int)]): Unit = {
    config.output.toFile.createIfNotExists(true)
    val file = new File(s"${config.output}/coauthors-edge-list.txt")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(coauthors.par.map { case (a1, a2, year) => s"$a1,$a2,$year" }.mkString("\n"))
    bw.flush()
    bw.close()
  }
}
