// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.components.tabs

import gem.enum.Site
import japgolly.scalajs.react.React
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.semanticui.collections.grid.GridColumn
import react.semanticui.widths._
import seqexec.web.client.circuit._
import seqexec.web.client.components.SeqexecStyles
import seqexec.web.client.components.queue.CalQueueTabContent
import seqexec.web.client.model.Pages.SeqexecPages
import seqexec.web.client.reusability._

/**
  * Top level container of the tabs area
  */
final case class TabsArea(router: RouterCtl[SeqexecPages], site: Site)
    extends ReactProps[TabsArea](TabsArea.component)

object TabsArea {
  type Props = TabsArea

  implicit val propsReuse: Reusability[Props] = Reusability.by(_.site)
  private val tabsConnect                     = SeqexecCircuit.connect(SeqexecCircuit.seqexecTabs)

  val component = ScalaComponent
    .builder[Props]("TabsArea")
    .stateless
    .render_P(p =>
      GridColumn(width = Sixteen, clazz = SeqexecStyles.sequencesArea)(
        SeqexecTabs(p.router),
        tabsConnect(x =>
          React
            .Fragment(
              x().toList.collect {
                case t: SequenceTabContentFocus =>
                  SequenceTabContent(p.router, t): VdomNode
                case t =>
                  CalQueueTabContent(
                    CalQueueTabContent.Props(t.canOperate, t.active, t.logDisplayed)
                  ): VdomNode
              }: _*
            )
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build
}
