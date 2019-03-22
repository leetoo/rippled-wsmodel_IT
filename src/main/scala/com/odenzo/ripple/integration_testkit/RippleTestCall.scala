package com.odenzo.ripple.integration_testkit

import java.io.{FileOutputStream, FileWriter, ObjectOutputStream}
import java.nio.file.Path
import scala.util.Try

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.support.{RippleGenericResponse, RippleRq, RippleRs}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.{AppException, OError}

case class RippleTestCall[A <: RippleRq, B <: RippleRs](
  rq:      A,
  json:    ErrorOr[RequestResponse[Json, Json]],
  generic: ErrorOr[RippleGenericResponse],
  result:  ErrorOr[B]
) extends StrictLogging {

  def hasErrors: Boolean = json.isLeft || generic.isLeft || result.isLeft

  def showMe(): String = {
    // In testing mode its really the JSON that is more interesting, so not showing the objects.
    // Its errors and/or request response JSON.

    val errMsg: String = if (hasErrors) {
      Seq(("JSON PARSING ERROR", json), ("GENERIC DECODING ERROR", generic), ("RESULT DECODING ERROR", result))
        .flatMap(tuple ⇒ tuple._2.left.toOption.map(e ⇒ (s"\t\t${tuple._1}\n\t ${e.show}", e)))
        .map(_._1)
        .mkString("===================== SHOWME ERRORS:\n\n", "=============\n\n", "=========== SHOWME ERRORS END")
    } else {
      "NO ERRORS!\n"
    }

    val jDump = json
      .map(rawJson ⇒ rawJson.show)
      .getOrElse("\n\n No JSON Request/Response")

    errMsg + "\n" + jDump
  }

  /** Want to run a bunch of integration tests, and use the requests and response as unit tests (with no network) later.
   *  DO this by dumping the Request / Response JSON and the Request Object
   *  What do we do:
   *  Write the Request A serialized Java style  ({A.getClass.getName}_{subname}_rq.serialized)
   *  Write the Request A serialzied in JSON     (_rq.json)
   *  Write the Response B in JSON (from the write) (_rs.json)
   *  Write the Response B decoded from JSON in Java Serialization format (_rs.serialized)
   *
   *  Actually we will make a subdir for each scenario. Filename is for humans.
   */
  def dumpSerializedForUnitTests(dir: Path, sequence: Int): ErrorOr[Path] = {
    logger.info(s"Dumping Serialized to $dir $sequence")
    val requestName = rq.getClass.getCanonicalName
    val fileName = rq.getClass.getSimpleName
    val subdir: Path = dir.resolve(requestName + "_" + sequence)
    logger.debug(s"Resolving to Dir $subdir for Reqyest ${rq.getClass}")

    val targetDir = if (subdir.toFile.mkdirs()) subdir.asRight else OError(s"Couldnt Create $subdir").asErrorOr

    val rqFile = subdir.resolve(fileName + "_rq.serialized")
    val rsFile = subdir.resolve(fileName + "_rs.serlized")
    val rqJsonFile = subdir.resolve(fileName + "_rq.json")
    val rsJsonFile = subdir.resolve(fileName + "_rs.json")

    val status: ErrorOr[Path] = for {
      _ ← targetDir
      res ← result
      jsonPair ← json
      _ ← RippleTestCall.writeImpure(rqFile, rq)
      _ ← RippleTestCall.writeImpure(rsFile, res)
      _ ← RippleTestCall.writeJsonImpure(rqJsonFile, jsonPair.rq)
      _ ← RippleTestCall.writeJsonImpure(rsJsonFile, jsonPair.rs)

    } yield subdir
    status.left.foreach(err ⇒ logger.error("Status: " + err.show))
    status
  }

}

object RippleTestCall extends StrictLogging {

  def dump[A <: RippleRq, B <: RippleRs](v: RippleTestCall[A, B]): String = {
    v.result match {
      case Left(err) ⇒ "** ERROR ** \n" + dumpErrorCase(v)
      case Right(ok) ⇒ "SUCCESS     \n" + dumpSuccessCase(v)
    }
  }

  def dumpSuccessCase[A <: RippleRq, B <: RippleRs](v: RippleTestCall[A, B]): String = {

    v.result
      .map { rs: B ⇒
        s"Success Request:\n${v.rq.toString}\n \n======\n  ${v.json.show}\n==== Final: $rs"

      }
      .getOrElse("Dumping a Success Case but there was an error!")

  }

  def dumpErrorCase[A <: RippleRq, B <: RippleRs](v: RippleTestCall[A, B]): String = {
    v.result.swap
      .map { rs ⇒
        s"""Failed Request:\n${v.rq.toString}
           |\n ${v.json.show}
           |\n ${v.generic}
           |\n==== Error: ${rs.show}
           |""".stripMargin

      }
      .getOrElse("Dumping a Error Case but it was success!")

  }

  /** Serializes an object to disk java style. This WILL NOT make the path to the file via mkdirs */
  protected def writeImpure[T <: Serializable](file: Path, a: T): ErrorOr[Path] = {
    //    val options: util.Set[StandardOpenOption] =
    //      JavaConverters.setAsJavaSet(
    //        Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    //      )
    //
    //    val fileChannel = FileChannel.open(file, options)
    //                             val fos = new FileOutputStream(fileChannel.)
    logger.debug(s"Writing Object $a to file $file")
    val attempt = Try {
      val out: FileOutputStream = new FileOutputStream(file.toFile, false)
      val oos: ObjectOutputStream = new ObjectOutputStream(out)
      oos.writeObject(a)
      oos.flush()
      oos.close()
      file
    }
    Either.fromTry(attempt).leftMap(ex ⇒ AppException(s"Trouble Serializing Call Results to Disk $file", ex))
  }

  /** No fancy piping or streaming or anything.
   *
   *  @param file
   *  @param j
   *
   *  @return
   */
  protected def writeJsonImpure(file: Path, j: Json): ErrorOr[Path] = {
    val attempt = Try {
      val str = j.spaces2
      val writer: FileWriter = new FileWriter(file.toFile)
      try { writer.append(str).append("\n") } finally { writer.close() }
      file
    }
    Either.fromTry(attempt).leftMap(ex ⇒ AppException(s"Trouble Serializing JSON to Disk $file", ex))
  }
}
