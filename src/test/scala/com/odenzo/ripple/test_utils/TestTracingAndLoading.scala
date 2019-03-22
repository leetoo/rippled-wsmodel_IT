package com.odenzo.ripple.test_utils
import java.io.{File, FileInputStream, FileOutputStream, ObjectInput, ObjectInputStream, ObjectOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryStream, Files, Path, Paths}
import java.util.stream
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import cats._
import cats.data._
import cats.implicits._
import cats.instances.stream
import com.typesafe.scalalogging.StrictLogging
import io.circe
import io.circe.Json

import com.odenzo.ripple.models.support.{RippleCommand, RippleRq, RippleRs}
import com.odenzo.ripple.utils.{CirceUtils, caterrors}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.{AppError, OError, AppException}

/** Each test trace is in a directory, with 4 files we find by the file suffix only.
 *  By convention we know the request.
 */
trait TestTracingAndLoading extends StrictLogging {

  def readTraceFiles(dir: Path, fileName: String): ErrorOr[(Json, Json, AnyRef, AnyRef)] = {
    val rqFile = "_rq.serialized"
    val rsFile = "_rs.serialized"
    val rqJsonFile = "_rq.json"
    val rsJsonFile = "_rs.json"

    import scala.collection.JavaConverters._
    //
    val allFiles = dir.toFile.listFiles().toArray

    def findFile(in: Array[File], suffix: String): ErrorOr[File] = {
      allFiles.find(_.getName.endsWith(suffix)) match {
        case None              ⇒ AppError(s"File ending in $suffix not found").asErrorOr
        case Some(found: File) ⇒ found.asRight
      }
    }

    // JsonRq, JsonRs, Rq[A<: RippleRq] Rs[B<:RippleRs] but last two not cast
    val done: ErrorOr[(Json, Json, AnyRef, AnyRef)] = for {
      jsonRq ← findFile(allFiles, rqJsonFile).flatMap(CirceUtils.parseAsJson)
      jsonRs ← findFile(allFiles, rsJsonFile).flatMap(CirceUtils.parseAsJson)
      rq ← findFile(allFiles, rqFile).flatMap(f ⇒ loadRequest(f.toPath))
      rs ← findFile(allFiles, rsFile).flatMap(f ⇒ loadRequest(f.toPath))
    } yield (jsonRq, jsonRs, rq, rs)
    done
  }

  /** THe joys of java exceptions, see if Cats can help or write pedantically and throw in utils
   *  Since this is testing leave a smoldering pile of leaves instead of using better-files.
   *  ANd see if Scala3 has ARM.
   *
   *  @param file
   *
   *  @return
   */
  private def loadRequest(file: Path): Either[AppException, AnyRef] = {
    logger.info(s"Deserialising FIle $file")
    import java.util.stream
    val attempt: Try[AnyRef] = Try {
      val inSrc = new FileInputStream(file.toFile) // Exception and ARM
      val ois: ObjectInput = new ObjectInputStream(inSrc) // Exception and ARM
      val something = ois.readObject() // Likely this can throw an exception
      ois.close() // Note that this can throw an exception in theory
      inSrc.close() // Note that this can throw an exception too!
      something
    } match {
      case e @ Failure(NonFatal(err)) ⇒
        logger.warn("Leaking Un-Closed File Handles!")
        // Would like to close but opening FIDS and OIS managed resources can fail on open even.
        // They are out of scope
        // We need a block that catches instantiation errors and nests ARM
        e

      case Failure(ex) ⇒ // The Fatal Exceptions :-/  Dont this they ever get trapped to Failure by Try
        logger.error("Fatal Exception was trapped", ex)
        Failure(ex)
      case other ⇒ other
    }
    Either.fromTry(attempt).leftMap(ex ⇒ AppException(s"Trouble Reading Object $file", ex))

  }

}

object TestTracingAndLoading extends TestTracingAndLoading {

}

