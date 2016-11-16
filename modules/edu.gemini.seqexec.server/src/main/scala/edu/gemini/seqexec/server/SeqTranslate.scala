package edu.gemini.seqexec.server

import java.time.LocalDate

import edu.gemini.seqexec.engine.{Action, Result, Sequence, Step}
import edu.gemini.seqexec.server.SeqexecFailure.UnrecognizedInstrument
import edu.gemini.spModel.config2.{Config, ConfigSequence, ItemKey}
import edu.gemini.spModel.obscomp.InstConstants._
import edu.gemini.spModel.seqcomp.SeqConfigNames._

import scalaz.Scalaz._
import scalaz._
import scalaz.concurrent.Task

/**
  * Created by jluhrs on 9/14/16.
  */
object SeqTranslate {

  case class Settings(dhsSim: Boolean,
                    tcsSim: Boolean,
                    instSim: Boolean,
                    gcalSim: Boolean)

  private var settings = Settings(false, false, false, false)
  private val dhsSimulator = DhsClientSim(LocalDate.now)
  def setConfig(c: Settings): Unit = { settings = c }

  implicit def toAction[A](x: SeqAction[A]): Action = x.run map {
    case -\/(e) => Result.Error(e)
    case \/-(r) => Result.OK(r)
  }

  def step(dhsClient: DhsClient)(i: Int, config: Config): SeqexecFailure \/ Step[Action] = {

    val instName = config.getItemValue(new ItemKey(INSTRUMENT_KEY, INSTRUMENT_NAME_PROP))
    val instrument = instName match {
      case GmosSouth.name => Some(GmosSouth)
      case Flamingos2.name => Some(Flamingos2(if(settings.instSim) Flamingos2ControllerSim else Flamingos2ControllerEpics))
      case _ => None
    }

    instrument.map { a =>
      val systems = List(Tcs(if(settings.tcsSim) TcsControllerSim else TcsControllerEpics), a)
      // TODO Find a proper way to inject the subsystems
      Step[Action](
        i,
        List(
          // TODO: implicit function doesn't work here, why?
          systems.map(x => toAction(x.configure(config))),
          List((a.observe(config)(dhsClient)))
        )
      ).right
    }.getOrElse(UnrecognizedInstrument(instName.toString).left[Step[Action]])
  }

  def sequence(obsId: String, sequenceConfig: ConfigSequence): SeqexecFailure \/ Sequence[Action] = {
    val configs = sequenceConfig.getAllSteps.toList

    val steps = configs.zipWithIndex.traverseU {
      case (c, i) => step(if(settings.dhsSim) dhsSimulator else DhsClientHttp)(i, c)
    }

    steps.map(Sequence[Action](obsId, _))
  }
}
