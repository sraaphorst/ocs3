// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.server

import edu.gemini.seqexec.model.dhs.ImageFileId
import edu.gemini.spModel.config2.Config

trait InstrumentSystem extends System {
  // The name used for this instrument in the science fold configuration
  val sfName: String
  val contributorName: String
  val dhsInstrumentName: String
  val observeControl: InstrumentSystem.ObserveControl
  def observe(config: Config): SeqObserve[ImageFileId, ObserveResult]
}

object InstrumentSystem {
  sealed trait ObserveControl
  object Uncontrollable extends ObserveControl
  final case class StopObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class AbortObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class PauseObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class ContinueObserveCmd(self: SeqAction[Unit]) extends AnyVal
  final case class Controllable(stop: StopObserveCmd,
                                abort: AbortObserveCmd,
                                pause: PauseObserveCmd,
                                continue: ContinueObserveCmd) extends ObserveControl
}

//Placeholder for observe response
final case class ObserveResult(dataId: ImageFileId)