// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.web.client.components.sequence.toolbars

import edu.gemini.seqexec.model.Model.{Instrument, SeqexecSite}
import edu.gemini.seqexec.web.client.circuit.SeqexecCircuit
import edu.gemini.seqexec.web.client.components.SeqexecStyles
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import scalacss.ScalaCssReact._

/**
  * Toolbar for anonymous users
  */
object SequenceAnonymousToolbar {
  final case class Props(site: SeqexecSite, instrument: Instrument) {
    protected[sequence] val instrumentConnects = site.instruments.list.toList.map(i => (i, SeqexecCircuit.connect(SeqexecCircuit.sequenceObserverReader(i)))).toMap
  }

  private def component = ScalaComponent.builder[Props]("SequencesDefaultToolbar")
    .stateless
    .render_P ( p =>
      <.div(
        ^.cls := "ui column",
        <.div(
          ^.cls := "ui row",
          SeqexecStyles.shorterRow,
          <.div(
            ^.cls := "left column bottom aligned sixteen wide computer ten wide tablet only",
            p.instrumentConnects.get(p.instrument).whenDefined(_(SequenceInfo.apply))
          )
        )
      )
    ).build

  def apply(site: SeqexecSite, i: Instrument): Unmounted[Props, Unit, Unit] = component(Props(site, i))
}
