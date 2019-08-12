// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gpi

import cats.data.EitherT
import cats.data.Kleisli
import cats.effect.Sync
import cats.effect.Timer
import cats.implicits._
import edu.gemini.spModel.config2.Config
import edu.gemini.spModel.gemini.gpi.Gpi._
import edu.gemini.spModel.seqcomp.SeqConfigNames._
import edu.gemini.spModel.obscomp.InstConstants
import edu.gemini.spModel.obsclass.ObsClass
import fs2.Stream
import java.lang.{ Boolean => JBoolean }
import java.lang.{ Double => JDouble }
import java.lang.{ Integer => JInt }
import gem.enum.GpiReadMode
import gem.enum.GpiReadModeOps
import gem.enum.LightSinkName
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.Instrument
import seqexec.model.enum.ObserveCommandResult
import seqexec.server.ConfigUtilOps._
import seqexec.server._
import seqexec.server.keywords.GdsClient
import seqexec.server.keywords.GdsInstrument
import seqexec.server.keywords.KeywordsClient
import scala.concurrent.duration._
import squants.Time
import squants.time.Milliseconds
import squants.time.Seconds
import squants.time.Time

final case class Gpi[F[_]: Sync: Timer](controller: GpiController[F])
    extends GdsInstrument[F]
    with InstrumentSystem[F] {

  override val gdsClient: GdsClient[F] = controller.gdsClient

  // Taken from the gpi isd
  val readoutOverhead: Time  = Seconds(4)
  val writeOverhead: Time    = Seconds(2)
  val perCoaddOverhead: Time = Seconds(2.7)
  val timeoutTolerance: Time = Seconds(30)

  override val keywordsClient: KeywordsClient[F] = this

  override val resource: Instrument = Instrument.Gpi

  override def sfName(config: Config): LightSinkName = LightSinkName.Gpi

  override val contributorName: String = "gpi"

  override def calcStepType(config: Config): Either[SeqexecFailure, StepType] =
    if (Gpi.isAlignAndCalib(config)) {
      StepType.AlignAndCalib.asRight
    } else {
      SequenceConfiguration.calcStepType(config)
    }

  override val observeControl: InstrumentSystem.ObserveControl[F] =
    InstrumentSystem.Uncontrollable

  override def observe(
    config: Config
  ): Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      calcObserveTime(config).flatMap { ot =>
        controller
          .observe(fileId, timeoutTolerance + ot)
          .as(ObserveCommandResult.Success: ObserveCommandResult)
      }
    }

  override def configure(config: Config): F[ConfigResult[F]] =
    if (Gpi.isAlignAndCalib(config)) {
      controller.alignAndCalib.as(ConfigResult(this))
    } else {
      Gpi
        .fromSequenceConfig[F](config)
        .flatMap(controller.applyConfig)
        .as(ConfigResult(this))
    }

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def calcObserveTime(config: Config): F[Time] = Sync[F].delay {
    val obsTime =
      for {
        exp <- config.extractAs[JDouble](OBSERVE_KEY / EXPOSURE_TIME_PROP)
        coa <- config.extractAs[JInt](OBSERVE_KEY / COADDS_PROP).map(_.toInt)
      } yield
        (Seconds(exp.toDouble) + perCoaddOverhead) * coa.toDouble + readoutOverhead + writeOverhead
    obsTime.getOrElse(Milliseconds(100))
  }

  override def observeProgress(
    total:   Time,
    elapsed: InstrumentSystem.ElapsedTime
  ): Stream[F, Progress] =
    ProgressUtil.countdown[F](total, elapsed.self)

}

object Gpi {
  val name: String = INSTRUMENT_NAME_PROP

  private def gpiAoFlags(config: Config): Either[ExtractFailure, AOFlags] =
    for {
      useAo      <- config.extractAs[JBoolean](INSTRUMENT_KEY / USE_AO_PROP)
      useCal     <- config.extractAs[JBoolean](INSTRUMENT_KEY / USE_CAL_PROP)
      aoOptimize <- config.extractAs[JBoolean](INSTRUMENT_KEY / AO_OPTIMIZE_PROP)
      alignFpm   <- config.extractAs[JBoolean](INSTRUMENT_KEY / ALIGN_FPM_PINHOLE_BIAS_PROP)
      magH       <- config.extractAs[JDouble](INSTRUMENT_KEY / MAG_H_PROP)
      magI       <- config.extractAs[JDouble](INSTRUMENT_KEY / MAG_I_PROP)
    } yield AOFlags(useAo, useCal, aoOptimize, alignFpm, magH, magI)

  private def gpiASU(
    config: Config
  ): Either[ExtractFailure, ArtificialSources] =
    for {
      ir          <- config.extractAs[ArtificialSource](INSTRUMENT_KEY / IR_LASER_LAMP_PROP)
      vis         <- config.extractAs[ArtificialSource](INSTRUMENT_KEY / VISIBLE_LASER_LAMP_PROP)
      sc          <- config.extractAs[ArtificialSource](INSTRUMENT_KEY / SUPER_CONTINUUM_LAMP_PROP)
      attenuation <- config.extractAs[JDouble](INSTRUMENT_KEY / ARTIFICIAL_SOURCE_ATTENUATION_PROP)
                       .map(_.toDouble)
    } yield ArtificialSources(ir, vis, sc, attenuation)

  private def gpiShutters(config: Config): Either[ExtractFailure, Shutters] =
    for {
      entrance     <- config.extractAs[Shutter](INSTRUMENT_KEY / ENTRANCE_SHUTTER_PROP)
      calEntrance  <- config.extractAs[Shutter](INSTRUMENT_KEY / CAL_ENTRANCE_SHUTTER_PROP)
      scienceArm   <- config.extractAs[Shutter](INSTRUMENT_KEY / SCIENCE_ARM_SHUTTER_PROP)
      referenceArm <- config.extractAs[Shutter](INSTRUMENT_KEY / REFERENCE_ARM_SHUTTER_PROP)
    } yield Shutters(entrance, calEntrance, scienceArm, referenceArm)

  private def gpiMode(config: Config)
    : Either[ExtractFailure, Either[ObservingMode, NonStandardModeParams]] =
    config
      .extractAs[ObservingMode](INSTRUMENT_KEY / OBSERVING_MODE_PROP)
      .flatMap { mode =>
        if (mode === ObservingMode.NONSTANDARD) {
          for {
            apodizer <- config.extractAs[Apodizer](INSTRUMENT_KEY / APODIZER_PROP)
            fpm      <- config.extractAs[FPM](INSTRUMENT_KEY / FPM_PROP)
            lyot     <- config.extractAs[Lyot](INSTRUMENT_KEY / LYOT_PROP)
            filter   <- config.extractAs[Filter](INSTRUMENT_KEY / FILTER_PROP)
          } yield NonStandardModeParams(apodizer, fpm, lyot, filter).asRight
        } else mode.asLeft.asRight
      }

  val AcquisitionKey = ObsClass.ACQ.headerValue()

  // TODO wrap this on F to keep RT, It involves a large change upstream
  def isAlignAndCalib(config: Config): Boolean = {
    (config.extractAs[String](INSTRUMENT_KEY / InstConstants.INSTRUMENT_NAME_PROP), config.extractAs[String](OBSERVE_KEY / InstConstants.OBS_CLASS_PROP)).mapN {
      case (Gpi.name, "acq") => true
      case _                 => false
    }.getOrElse(false)
  }

  def gpiReadMode(config: Config): Either[ExtractFailure, GpiReadMode] =
    config.extractAs[JInt](OBSERVE_KEY / DETECTOR_SAMPLING_MODE_PROP)
      .map(_.toInt)
      .flatMap(GpiReadMode.fromValue(_)
        .toRight(
          ConversionError(OBSERVE_KEY / DETECTOR_SAMPLING_MODE_PROP,
          "Cannot read gpi Read mode")))

  private def regularSequenceConfig[F[_]: Sync](config: Config): F[GpiConfig] =
    EitherT(Sync[F].delay(
      (for {
        adc      <- config.extractAs[Adc](INSTRUMENT_KEY / ADC_PROP)
        exp      <- config.extractAs[JDouble](OBSERVE_KEY / EXPOSURE_TIME_PROP)
                      .map(x => Duration(x, SECONDS))
        coa      <- config.extractAs[JInt](OBSERVE_KEY / COADDS_PROP)
                      .map(_.toInt)
        readMode <- gpiReadMode(config)
        mode     <- gpiMode(config)
        pol      <- config.extractAs[Disperser](INSTRUMENT_KEY / DISPERSER_PROP)
        polA     <- config.extractAs[JDouble](INSTRUMENT_KEY / HALF_WAVE_PLATE_ANGLE_VALUE_PROP)
                      .map(_.toDouble)
        shutters <- gpiShutters(config)
        asu      <- gpiASU(config)
        pc       <- config.extractAs[PupilCamera](INSTRUMENT_KEY / PUPUL_CAMERA_PROP)
        ao       <- gpiAoFlags(config)
      } yield RegularGpiConfig(adc, exp, coa, readMode, mode, pol, polA, shutters, asu, pc, ao))
        .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))
    )).widenRethrowT.widen[GpiConfig]

  private def alignAndCalibConfig[F[_]: Sync]: F[GpiConfig] =
    AlignAndCalibConfig.pure[F].widen[GpiConfig]

  def fromSequenceConfig[F[_]: Sync](config: Config): F[GpiConfig] =
    Sync[F].delay(isAlignAndCalib(config)).ifM(alignAndCalibConfig, regularSequenceConfig(config))

}
