// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec

import cats.data._
import cats.effect.IO
import cats.implicits._
import cats.kernel.Eq
import edu.gemini.spModel.`type`.SequenceableSpType
import edu.gemini.spModel.guide.StandardGuideOptions
import fs2.async.mutable.Queue
import gem.Observation
import monocle.macros.Lenses
import monocle.Lens
import monocle.macros.GenLens
import monocle.function.At.at
import monocle.function.At.atMap
import seqexec.engine.{ActionMetadata, ActionMetadataGenerator, Engine}
import seqexec.model.Model.{CloudCover, Conditions, Instrument, ImageQuality, Observer, Operator, SequenceState, SkyBackground, WaterVapor}
import seqexec.model.UserDetails

package server {
  @Lenses
  final case class EngineMetadata(queues: ExecutionQueues, selected: Map[Instrument, Observation.Id], conditions: Conditions, operator: Option[Operator])
  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  object EngineMetadata {
    implicit val eq: Eq[EngineMetadata] = Eq.by(x => (x.queues, x.selected, x.conditions, x.operator))

    val default: EngineMetadata = EngineMetadata(Map(CalibrationQueueName -> Nil), Map.empty, Conditions.default, None)

    def selectedML(instrument: Instrument): Lens[EngineMetadata, Option[Observation.Id]] = GenLens[EngineMetadata](_.selected) ^|-> at(instrument)
  }

  sealed trait SeqEvent
  final case class SetOperator(name: Operator, user: Option[UserDetails]) extends SeqEvent
  final case class SetObserver(id: Observation.Id, user: Option[UserDetails], name: Observer) extends SeqEvent
  final case class SetConditions(conditions: Conditions, user: Option[UserDetails]) extends SeqEvent
  final case class SetSelectedSequence(instrument: Instrument, sid: Observation.Id, user: Option[UserDetails]) extends SeqEvent
  final case class SetImageQuality(iq: ImageQuality, user: Option[UserDetails]) extends SeqEvent
  final case class SetWaterVapor(wv: WaterVapor, user: Option[UserDetails]) extends SeqEvent
  final case class SetSkyBackground(wv: SkyBackground, user: Option[UserDetails]) extends SeqEvent
  final case class SetCloudCover(cc: CloudCover, user: Option[UserDetails]) extends SeqEvent
  case object NullSeqEvent extends SeqEvent
}

package object server {
  implicit def geEq[D <: SequenceableSpType]: Eq[D] =
    Eq[String].contramap(_.sequenceValue())

  implicit val sgoEq: Eq[StandardGuideOptions.Value] =
    Eq[Int].contramap(_.ordinal())

  val CalibrationQueueName: String = "Calibration Queue"

  type TrySeq[A] = Either[SeqexecFailure, A]

  object TrySeq {
    def apply[A](a: A): TrySeq[A]             = Either.right(a)
    def fail[A](p: SeqexecFailure): TrySeq[A] = Either.left(p)
  }

  type SeqAction[A] = EitherT[IO, SeqexecFailure, A]
  type SeqActionF[F[_], A] = EitherT[F, SeqexecFailure, A]

  type SeqObserve[A, B] = Reader[A, SeqAction[B]]

  type ExecutionQueue = List[Observation.Id]
  type ExecutionQueues = Map[String, ExecutionQueue]

  implicit object ExecutionQueuesCanGenerateActionMetadata extends ActionMetadataGenerator[EngineMetadata] {
    override def generate(a: EngineMetadata)(v: ActionMetadata): ActionMetadata =
      v.copy(conditions = a.conditions, operator = a.operator)
  }

  val executeEngine: Engine[EngineMetadata, SeqEvent] = new Engine[EngineMetadata, SeqEvent]

  type EventQueue = Queue[IO, executeEngine.EventType]

  object SeqAction {
    def apply[A](a: => A): SeqAction[A]          = EitherT(IO.apply(TrySeq(a)))
    def either[A](a: => TrySeq[A]): SeqAction[A] = EitherT(IO.apply(a))
    def fail[A](p: SeqexecFailure): SeqAction[A] = EitherT(IO.apply(TrySeq.fail(p)))
    def void: SeqAction[Unit]                    = SeqAction.apply(())
  }

  implicit class MoreDisjunctionOps[A,B](ab: Either[A, B]) {
    def validationNel: ValidatedNel[A, B] =
      ab.fold(a => Validated.Invalid(NonEmptyList.of(a)), b => Validated.Valid(b))
  }

  implicit class ExecutionQueueOps(q: ExecutionQueue) {
    def status(st: executeEngine.StateType): SequenceState = {
      val statuses: List[SequenceState] = q.map(st.sequences.get(_).map(_.status).getOrElse(SequenceState.Idle))

      statuses.find(_.isRunning).orElse(statuses.find(_.isError)).orElse(statuses.find(_.isStopped))
        .orElse(statuses.find(_.isIdle)).getOrElse(SequenceState.Completed)
    }
  }

}
