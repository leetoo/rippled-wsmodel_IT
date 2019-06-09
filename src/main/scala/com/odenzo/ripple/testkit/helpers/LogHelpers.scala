package com.odenzo.ripple.testkit.helpers

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging

import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr

trait LogHelpers extends StrictLogging {
  /** This will strip "field"=null */
  def reqres2string(rr: JsonReqRes): String = {
    import io.circe.syntax._
    val rq = CirceUtils.print(rr.rq.asJson)
    val rs = CirceUtils.print(rr.rs.asJson)
    s"""\n\n{\n\t "Request":$rq, \n\t "Response":$rs\n } """
  }

  /** Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: ErrorOr[JsonReqRes]): Unit = {
    ans.foreach(rr ⇒ logger.info(reqres2string(rr)))

  }

  /** Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: List[JsonReqRes]): Unit = {
    val arr = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    logger.info(arr)

  }

  def logAnswerToFile(file: String, ans: List[JsonReqRes]): Path = {
    val arr: String = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    overwriteFileWith(file, arr)

  }


  /** Worth a expirement with Cats Resource handling! */
  def overwriteFileWith(filePath: String, data: String): Path = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.{Files, Paths}
    Files.write(Paths.get(filePath), data.getBytes(StandardCharsets.UTF_8))
  }
}

object LogHelpers extends LogHelpers
