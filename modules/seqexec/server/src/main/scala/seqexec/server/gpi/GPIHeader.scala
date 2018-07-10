// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gpi

import seqexec.model.dhs.ImageFileId
import seqexec.server.{Header, SeqAction}

object GPIHeader {
  def header: Header = new Header {
    override def sendBefore(id: ImageFileId): SeqAction[Unit] = {
      SeqAction.void
    }

    override def sendAfter(id: ImageFileId): SeqAction[Unit] =
      SeqAction.void
  }
}
