// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.sequence.toolbars

import cats.syntax.all._
import gem.Observation
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import mouse.all._
import react.common._
import react.semanticui.colors._
import seqexec.web.client.actions.RequestCancelPause
import seqexec.web.client.actions.RequestPause
import seqexec.web.client.actions.RequestRun
import seqexec.web.client.actions.RequestSync
import seqexec.web.client.actions.RunOptions
import seqexec.web.client.circuit._
import seqexec.web.client.components.SeqexecStyles
import seqexec.web.client.icons._
import seqexec.web.client.model.CancelPauseOperation
import seqexec.web.client.model.PauseOperation
import seqexec.web.client.model.RunOperation
import seqexec.web.client.model.SyncOperation
import seqexec.web.client.reusability._
import seqexec.web.client.semanticui.controlButton

final case class SequenceControl(p: SequenceControlFocus)
    extends ReactProps[SequenceControl](SequenceControl.component) {
  private val runRequested: RunOperation =
    p.control.tabOperations.runRequested

  private val syncRequested: SyncOperation =
    p.control.tabOperations.syncRequested

  private val pauseRequested: PauseOperation =
    p.control.tabOperations.pauseRequested

  private val cancelPauseRequested: CancelPauseOperation =
    p.control.tabOperations.cancelPauseRequested

  private val syncIdle: Boolean        =
    syncRequested === SyncOperation.SyncIdle
  private val runIdle: Boolean         =
    runRequested === RunOperation.RunIdle
  private val pauseIdle: Boolean       =
    pauseRequested === PauseOperation.PauseIdle
  private val cancelPauseIdle: Boolean =
    cancelPauseRequested === CancelPauseOperation.CancelPauseIdle

  val canSync: Boolean        =
    p.canOperate && syncIdle && runIdle
  val canRun: Boolean         =
    p.canOperate && runIdle && syncIdle && !p.control.tabOperations.anyResourceInFlight
  val canPause: Boolean       = p.canOperate && pauseIdle
  val canCancelPause: Boolean = p.canOperate && cancelPauseIdle
}

/**
 * Control buttons for the sequence
 */
object SequenceControl {
  type Props = SequenceControl

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  def requestRun(s: Observation.Id): Callback =
    SeqexecCircuit.dispatchCB(RequestRun(s, RunOptions.Normal))

  def requestSync(s: Observation.Id): Callback =
    SeqexecCircuit.dispatchCB(RequestSync(s))

  def requestPause(s: Observation.Id): Callback =
    SeqexecCircuit.dispatchCB(RequestPause(s))

  def requestCancelPause(s: Observation.Id): Callback =
    SeqexecCircuit.dispatchCB(RequestCancelPause(s))

  private def syncButton(id: Observation.Id, canSync: Boolean) =
    controlButton(icon = IconRefresh,
                  color = Purple,
                  onClick = requestSync(id),
                  disabled = !canSync,
                  tooltip = "Sync sequence",
                  text = "Sync"
    )

  private def runButton(
    id:                  Observation.Id,
    isPartiallyExecuted: Boolean,
    nextStepToRun:       Int,
    canRun:              Boolean
  ) = {
    val runContinueTooltip =
      s"${isPartiallyExecuted.fold("Continue", "Run")} the sequence from the step $nextStepToRun"
    val runContinueButton  =
      s"${isPartiallyExecuted.fold("Continue", "Run")} from step $nextStepToRun"
    controlButton(icon = IconPlay,
                  color = Blue,
                  onClick = requestRun(id),
                  disabled = !canRun,
                  tooltip = runContinueTooltip,
                  text = runContinueButton
    )
  }

  private def cancelPauseButton(id: Observation.Id, canCancelPause: Boolean) =
    controlButton(
      icon = IconBan,
      color = Brown,
      onClick = requestCancelPause(id),
      disabled = !canCancelPause,
      tooltip = "Cancel process to pause the sequence",
      text = "Cancel Pause"
    )

  private def pauseButton(id: Observation.Id, canPause: Boolean) =
    controlButton(
      icon = IconPause,
      color = Teal,
      onClick = requestPause(id),
      disabled = !canPause,
      tooltip = "Pause the sequence after the current step completes",
      text = "Pause"
    )

  private def component =
    ScalaComponent
      .builder[Props]("SequenceControl")
      .stateless
      .render_P { p =>
        val SequenceControlFocus(_, control)               = p.p
        val ControlModel(id, partial, nextStep, status, _) = control
        val nextStepToRun                                  = nextStep.foldMap(_ + 1)

        <.div(
          SeqexecStyles.controlButtons,
          List(
            // Sync button
            syncButton(id, p.canSync)
              .when(status.isIdle || status.isError),
            // Run button
            runButton(id, partial, nextStepToRun, p.canRun)
              .when(status.isIdle || status.isError),
            // Cancel pause button
            cancelPauseButton(id, p.canCancelPause)
              .when(status.userStopRequested),
            // Pause button
            pauseButton(id, p.canPause)
              .when(status.isRunning && !status.userStopRequested)
          ).toTagMod
        )
      }
      .build

  def apply(p: Props): Unmounted[Props, Unit, Unit] =
    component(p)
}
