// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.web.client.components.sequence.toolbars

import edu.gemini.seqexec.model.Model.SequenceId
import edu.gemini.seqexec.web.client.actions.UpdateObserver
import edu.gemini.seqexec.web.client.circuit.StatusAndObserverFocus
import edu.gemini.seqexec.web.client.semanticui.elements.label.FormLabel
import edu.gemini.seqexec.web.client.semanticui.elements.input.InputEV
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import japgolly.scalajs.react.extra.{StateSnapshot, TimerSupport}
import japgolly.scalajs.react.component.Scala.Unmounted
import diode.react.ModelProxy
import org.scalajs.dom.html.Div

import scalaz.syntax.equal._
import scalaz.syntax.std.option._
import scalaz.std.string._
import scalaz.std.option._

import scala.concurrent.duration._

/**
  * Encapsulates the field to change the observer name
  */
object SequenceObserverField {
  final case class Props(p: ModelProxy[StatusAndObserverFocus])

  final case class State(currentText: Option[String])

  class Backend(val $: BackendScope[Props, State]) extends TimerSupport {
    def updateObserver(id: SequenceId, name: String): Callback =
      $.props >>= { p => Callback.when(p.p().isLogged)(p.p.dispatchCB(UpdateObserver(id, name))) }

    def updateState(value: String): Callback =
      $.state >>= { s => Callback.when(!s.currentText.contains(value))($.modState(_.copy(currentText = Some(value)))) }

    def submitIfChanged: Callback =
      ($.state zip $.props) >>= {
        case (s, p) => Callback.when(p.p().isLogged && p.p().observer.map(_.value) =/= s.currentText)(p.p().id.map(updateObserver(_, s.currentText.getOrElse(""))).getOrEmpty)
      }

    def setupTimer: Callback =
      // Every 2 seconds check if the field has changed and submit
      setInterval(submitIfChanged, 2.second)

    def render(p: Props, s: State): VdomTagOf[Div] = {
      val observerEV = StateSnapshot(~s.currentText)(updateState)
      val StatusAndObserverFocus(_, _, instrument, _, _) = p.p()
      <.div(
        ^.cls := "ui form",
        <.div(
          ^.cls := "ui inline fields",
          <.div(
            ^.cls := "field four wide required",
            FormLabel(FormLabel.Props("Observer"))
          ),
          <.div(
            ^.cls := "field fourteen wide",
            InputEV(InputEV.Props(
              s"$instrument.observer",
              s"$instrument.observer",
              observerEV,
              placeholder = "Observer...",
              onBlur = _ => submitIfChanged))
          )
        )
      )
    }
  }

  private val component = ScalaComponent.builder[Props]("SequenceObserverField")
    .initialState(State(None))
    .renderBackend[Backend]
    .configure(TimerSupport.install)
    .componentWillMount(f => f.backend.$.props >>= {p => Callback.when(p.p().observer.isDefined)(f.backend.updateState(p.p().observer.map(_.value).getOrElse("")))})
    .componentDidMount(_.backend.setupTimer)
    .componentWillReceiveProps { f =>
      val observer = f.nextProps.p().observer
      // Update the observer field
      Callback.when((observer.map(_.value) =/= f.state.currentText) && observer.nonEmpty)(f.backend.updateState(observer.map(_.value).getOrElse("")))
    }
    .shouldComponentUpdatePure { f =>
      val observer = f.nextProps.p().observer
      observer.map(_.value) =/= f.currentState.currentText
    }
    .build

  def apply(p: ModelProxy[StatusAndObserverFocus]): Unmounted[Props, State, Backend] = component(Props(p))
}