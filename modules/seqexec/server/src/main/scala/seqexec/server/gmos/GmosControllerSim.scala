// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gmos

import seqexec.model.dhs.ImageFileId
import seqexec.server.gmos.GmosController.{GmosConfig, NorthTypes, SiteDependentTypes, SouthTypes}
import seqexec.server.{InstrumentControllerSim, ObserveCommand, SeqAction}
import squants.Time

private class GmosControllerSim[T<:SiteDependentTypes](name: String) extends GmosController[T] {

  override def getConfig: SeqAction[GmosConfig[T]] = ??? // scalastyle:ignore

  private val sim: InstrumentControllerSim = InstrumentControllerSim(s"GMOS $name")

  override def observe(fileId: ImageFileId, expTime: Time): SeqAction[ObserveCommand.Result] =
    sim.observe(fileId, expTime)

  override def applyConfig(config: GmosConfig[T]): SeqAction[Unit] = sim.applyConfig(config)

  override def stopObserve: SeqAction[Unit] = sim.stopObserve

  override def abortObserve: SeqAction[Unit] = sim.abortObserve

  override def endObserve: SeqAction[Unit] = sim.endObserve

  override def pauseObserve: SeqAction[Unit] = sim.pauseObserve

  override def resumePaused(expTime: Time): SeqAction[ObserveCommand.Result] = sim.resumePaused

  override def stopPaused: SeqAction[ObserveCommand.Result] = sim.stopPaused

  override def abortPaused: SeqAction[ObserveCommand.Result] = sim.abortPaused

}

object GmosControllerSim {
  val south: GmosController[SouthTypes] = new GmosControllerSim[SouthTypes]("South")
  val north: GmosController[NorthTypes] = new GmosControllerSim[NorthTypes]("North")
}