// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.web.server.common

import java.io.File
import java.nio.file.{Path, Paths}
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.logging.{Logger, Level, LogManager}

import scalaz.concurrent.Task

trait AppBaseDir {
  /**
    * Calculates the base dir of the application based on the location of "this" class jar file
    * It will throw an exception if unable to find the base dir
    */
  def baseDir: Task[Path] = Task.delay {
    val clazz = this.getClass
    val fileName = clazz.getResource(s"/${clazz.getName.replace(".", System.getProperty("file.separator"))}.class").getFile

    // find the separator for the intra classpath location
    fileName.replace("file:", "").split("!").headOption.map { (f: String) =>
      // Find the location of the basedir relative to this class
      // it assumes the jar is in a lib dir under base
      val jarFile = new File(f).getParentFile
      jarFile.getParentFile.toPath
    }.getOrElse(Paths.get(System.getProperty("user.home")))
  }
}

trait LogInitialization extends AppBaseDir {
  // Send logs from JULI (e.g. ocs) to SLF4J
  private def sendJuliToSLF4J: Task[Unit] = Task.delay {
    LogManager.getLogManager().reset()
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    // Required to include debugging info, may affect performance though
    Logger.getGlobal.setLevel(Level.FINE)
  }

  def configLog: Task[Unit] = sendJuliToSLF4J
}
