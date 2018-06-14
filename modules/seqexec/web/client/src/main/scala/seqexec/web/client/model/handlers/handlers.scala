// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.handlers

import cats.implicits._
import diode.{Action, ActionHandler, ActionResult, Effect, ModelRW, NoAction}
import gem.Observation
import gem.enum.Site
import seqexec.model.UserDetails
import seqexec.model.Model._
import seqexec.web.client.model._
import seqexec.web.client.model.Pages._
import seqexec.web.client.model.SeqexecAppRootModel.LoadedSequences
import seqexec.web.client.ModelOps._
import seqexec.web.client.actions._
import seqexec.web.client.circuit._
import seqexec.web.client.services.SeqexecWebClient
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class NavigationHandler[M](modelRW: ModelRW[M, Pages.SeqexecPages]) extends ActionHandler(modelRW) with Handlers {
  def handleNavigateTo: PartialFunction[Any, ActionResult[M]] = {
    case NavigateTo(page) =>
      updated(page)
  }

  def handleSilentTo: PartialFunction[Any, ActionResult[M]] = {
    case NavigateSilentTo(page) =>
      val effect = page match {
        case InstrumentPage(i)               =>
          Effect(Future(SelectInstrumentToDisplay(i)))
        case SequencePage(_, id, _)          =>
          Effect(Future(SelectIdToDisplay(id)))
        case SequenceConfigPage(_, id, step) =>
          Effect(Future(ShowStepConfig(id, step)))
        case _                               =>
          VoidEffect
      }
      updatedSilent(page, effect)
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleNavigateTo, handleSilentTo).combineAll
}

/**
* Handles actions requesting sync
*/
class SyncRequestsHandler[M](modelRW: ModelRW[M, Boolean]) extends ActionHandler(modelRW) with Handlers {
  def handleSyncRequestOperation: PartialFunction[Any, ActionResult[M]] = {
    case RequestSync(s) =>
      updated(true, Effect(SeqexecWebClient.sync(s).map(r => if (r.queue.isEmpty) RunSyncFailed(s) else RunSync(s))))
  }

  def handleSyncResult: PartialFunction[Any, ActionResult[M]] = {
    case RunSyncFailed(_) =>
      updated(false)

    case RunSync(_) =>
      updated(false)
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleSyncRequestOperation,
      handleSyncResult).combineAll
}


/**
  * Handles sequence execution actions
  */
class SequenceExecutionHandler[M](modelRW: ModelRW[M, LoadedSequences]) extends ActionHandler(modelRW) with Handlers {
  def handleUpdateObserver: PartialFunction[Any, ActionResult[M]] = {
    case UpdateObserver(sequenceId, name) =>
      val updateObserverE = Effect(SeqexecWebClient.setObserver(sequenceId, name).map(_ => NoAction))
      val updatedSequences = value.copy(queue = value.queue.collect {
        case s if s.id === sequenceId =>
          s.copy(metadata = s.metadata.copy(observer = Some(Observer(name))))
        case s                        => s
      })
      updated(updatedSequences, updateObserverE)
  }

  def handleFlipSkipBreakpoint: PartialFunction[Any, ActionResult[M]] = {
    case FlipSkipStep(sequenceId, step) =>
      val skipRequest = Effect(SeqexecWebClient.skip(sequenceId, step.flipSkip).map(_ => NoAction))
      updated(value.copy(queue = value.queue.collect {
        case s if s.id === sequenceId => s.flipSkipMarkAtStep(step)
        case s                        => s
      }), skipRequest)

    case FlipBreakpointStep(sequenceId, step) =>
      val breakpointRequest = Effect(SeqexecWebClient.breakpoint(sequenceId, step.flipBreakpoint).map(_ => NoAction))
      updated(value.copy(queue = value.queue.collect {
        case s if s.id === sequenceId => s.flipBreakpointAtStep(step)
        case s                        => s
      }), breakpointRequest)
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleUpdateObserver, handleFlipSkipBreakpoint).combineAll
}

/**
  * Handles actions related to opening/closing a modal
  */
class ModalBoxHandler[M](openAction: Action, closeAction: Action, modelRW: ModelRW[M, SectionVisibilityState]) extends ActionHandler(modelRW) with Handlers {
  def openModal: PartialFunction[Any, ActionResult[M]] = {
    case x if x == openAction && value === SectionClosed =>
      updated(SectionOpen)

    case x if x == openAction                            =>
      noChange
  }

  def closeModal: PartialFunction[Any, ActionResult[M]] = {
    case x if x == closeAction && value === SectionOpen =>
      updated(SectionClosed)

    case x if x == closeAction                          =>
      noChange
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    openModal |+| closeModal
}

/**
  * Handles actions related to opening/closing the login box
  */
class UserLoginHandler[M](modelRW: ModelRW[M, Option[UserDetails]]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case LoggedIn(u) =>
      // Close the login box
      val effect = Effect(Future(CloseLoginBox))
      // Close the websocket and reconnect
      val reconnect = Effect(Future(Reconnect))
      updated(Some(u), reconnect + effect)

    case Logout =>
      val effect = Effect(SeqexecWebClient.logout().map(_ => NoAction))
      val reConnect = Effect(Future(Reconnect))
      // Remove the user and call logout
      updated(None, effect + reConnect)
  }
}

/**
  * Handles actions related to the changing the selection of the displayed sequence
  */
class SequenceDisplayHandler[M](modelRW: ModelRW[M, (SequencesOnDisplay, Option[Site])]) extends ActionHandler(modelRW) with Handlers {
  def handleSelectSequenceDisplay: PartialFunction[Any, ActionResult[M]] = {
    case SelectInstrumentToDisplay(_) =>
      noChange
      // updated(value.copy(_1 = value._1.focusOnInstrument(i)))

    case SelectIdToDisplay(_) =>
      // val seq = SeqexecCircuit.sequenceRef(id)
      // updated(value.copy(_1 = value._1.focusOnSequence(seq)))
      noChange

  }

  def handleInitialize: PartialFunction[Any, ActionResult[M]] = {
    case Initialize(site) =>
      updated(value.copy(_1 = value._1.withSite(site), _2 = Some(site)))
  }

  // def handleShowHideStep: PartialFunction[Any, ActionResult[M]] = {
  //   case ShowStepConfig(id, step)         =>
  //     val seq = SeqexecCircuit.sequenceRef(id)
  //     updated(value.copy(_1 = value._1.focusOnSequence(seq).showStepConfig(step - 1)))
  //
  //   case HideStepConfig(instrument) =>
  //     if (value._1.instrumentSequences.focus.sequence.exists(_.metadata.instrument == instrument)) {
  //       updated(value.copy(_1 = value._1.hideStepConfig))
  //     } else {
  //       noChange
  //     }
  // }

  def handleRememberCompleted: PartialFunction[Any, ActionResult[M]] = {
    case RememberCompleted(s) =>
      updated(value.copy(_1 = value._1.markCompleted(s)))
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(handleSelectSequenceDisplay,
      handleInitialize,
      // handleShowHideStep,
      handleRememberCompleted).combineAll
}

/**
 * Handles updates to the operator
 */
class OperatorHandler[M](modelRW: ModelRW[M, Option[Operator]]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case UpdateOperator(name) =>
      val updateOperatorE = Effect(SeqexecWebClient.setOperator(name).map(_ => NoAction))
      updated(name.some, updateOperatorE)
  }
}

/**
  * Handles updates to the log
  */
class GlobalLogHandler[M](modelRW: ModelRW[M, GlobalLog]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case AppendToLog(s) =>
      updated(value.copy(log = value.log.append(s)))

    case ToggleLogArea =>
      updated(value.copy(display = value.display.toggle))
  }
}

/**
  * Handles setting what sequence is in conflict
  */
class SequenceInConflictHandler[M](modelRW: ModelRW[M, Option[Observation.Id]]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case SequenceInConflict(id) =>
      updated(Some(id))
  }
}

/**
  * Handle for UI debugging events
  */
class DebuggingHandler[M](modelRW: ModelRW[M, LoadedSequences]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case MarkStepAsRunning(obsId, step) =>
      updated(value.copy(queue = value.queue.collect {
        case v: SequenceView if v.id === obsId => v.showAsRunning(step)
        case v                                 => v
      }))
  }
}

/**
  * Handle to preserve the steps table state
  */
class StepConfigTableStateHandler[M](modelRW: ModelRW[M, TableStates]) extends ActionHandler(modelRW) with Handlers {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case UpdateStepsConfigTableState(state) =>
      updatedSilent(value.copy(stepConfigTable = state)) // We should only do silent updates as these change too quickly

    case UpdateQueueTableState(state) =>
      updatedSilent(value.copy(queueTable = state)) // We should only do silent updates as these change too quickly
  }
}
