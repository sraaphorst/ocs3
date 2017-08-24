// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.server

import edu.gemini.seqexec.odb.SeqFailure

sealed trait SeqexecFailure

object SeqexecFailure {

  /** Seqexec does not know how to deal with instrument in sequence. */
  final case class UnrecognizedInstrument(name: String) extends SeqexecFailure

  /** Something went wrong while running a sequence. **/
  final case class Execution(errMsg: String) extends SeqexecFailure

  /** Exception thrown while running a sequence. */
  final case class SeqexecException(ex: Throwable) extends SeqexecFailure

  /** Invalid operation on a Sequence */
  final case class InvalidOp(errMsg: String) extends SeqexecFailure

  /** Indicates an unexpected problem while performing a Seqexec operation. */
  final case class Unexpected(msg: String) extends SeqexecFailure

  /** Timeout */
  final case class Timeout(msg: String) extends SeqexecFailure

  /** Sequence loading errors */
  final case class ODBSeqError(fail: SeqFailure) extends SeqexecFailure

  def explain(f: SeqexecFailure): String = f match {
    case UnrecognizedInstrument(name) => s"Unrecognized instrument: $name"
    case Execution(errMsg)            => s"Sequence execution failed with error $errMsg"
    case SeqexecException(ex)         => "Application exception: " + ex.getMessage
    case InvalidOp(msg)               => s"Invalid operation: $msg"
    case Unexpected(msg)              => s"Unexpected error: $msg"
    case Timeout(msg)                 => s"Timeout while waiting for $msg"
    case ODBSeqError(fail)             => SeqFailure.explain(fail)
  }

}
