package edu.gemini.seqexec.web.server.http4s.encoder

import edu.gemini.seqexec.model._
import edu.gemini.seqexec.web.common._
import boopickle.Default._

/**
  * Contains http4s implicit encoders of model objects
  */
trait BooEncoders {
  import edu.gemini.seqexec.web.common.LogMessage._
  import CliCommand._

  // Decoders, Included here instead of the on the object definitions to avoid
  // a circular dependency on http4s
  implicit val userLoginDecoder      = booOf[UserLoginRequest]
  implicit val userDetailEncoder     = booEncoderOf[UserDetails]
  implicit val logMessageDecoder     = booOf[LogMessage]
  implicit val sequenceEncoder       = booEncoderOf[Sequence]
  // The next one seems redundant but apparently we don't find recursive picklers
  implicit val listSequenceEncoder   = booEncoderOf[List[Sequence]]
  implicit val sequexecQueueEncoder  = booEncoderOf[SeqexecQueue]
  implicit val commandsEncoder       = booEncoderOf[CliCommand]
}