package com.opswat.teamcity

import java.io.{File, PrintWriter}

import jetbrains.buildServer.serverSide.ServerPaths
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization._

case class MConfig(
    mURL: Option[String],
    mAPIKey: Option[String],
    mRule: Option[String],
    mViewDetail: Option[String],
    mForceScan: Option[String],
    mFailBuild: Option[String],
    mTimeOut: Option[String],
    mSandboxEnabled: Option[String],
    mSandboxOS: Option[String],
    mSandboxTimeOut: Option[String],
    mSandboxBrowser: Option[String]
)

class MConfigManager(paths: ServerPaths) {
  implicit val formats = Serialization.formats(NoTypeHints)

  val configFile = new File(s"${paths.getConfigDir}/metadefender.json")

  private[teamcity] var config: Option[MConfig] = {
    if (configFile.exists()) {
      parse(configFile).extractOpt[MConfig]
    } else None
  }

  def mURL: Option[String] = config.flatMap(_.mURL)
  def mAPIKey: Option[String] = config.flatMap(_.mAPIKey)
  def mRule: Option[String] = config.flatMap(_.mRule)
  def mViewDetail: Option[String] = config.flatMap(_.mViewDetail)
  def mForceScan: Option[String] = config.flatMap(_.mForceScan)
  def mFailBuild: Option[String] = config.flatMap(_.mFailBuild)
  def mTimeOut: Option[String] = {
    if (config.flatMap(_.mTimeOut) == None || config.flatMap(_.mTimeOut) == Option(""))
      return Option("30")
    return config.flatMap(_.mTimeOut)
  }
  def mSandboxEnabled: Option[String] = config.flatMap(_.mSandboxEnabled)
  def mSandboxOS: Option[String] = config.flatMap(_.mSandboxOS)
  def mSandboxTimeOut: Option[String] = config.flatMap(_.mSandboxTimeOut)
  def mSandboxBrowser: Option[String] = config.flatMap(_.mSandboxBrowser)

  private[teamcity] def update(config: MConfig): Unit = {
    this.config = Some(config)
  }

  def updateAndPersist(newConfig: MConfig): Unit = {
    synchronized {
      update(newConfig)
      val out = new PrintWriter(configFile, "UTF-8")
      try { writePretty(config, out) }
      finally { out.close }
    }
  }

  def details: Map[String, Option[String]] = Map(
    "mURL" -> mURL,
    "mAPIKey" -> mAPIKey,
    "mRule" -> mRule,
    "mViewDetail" -> mViewDetail,
    "mForceScan" -> mForceScan,
    "mFailBuild" -> mFailBuild,
    "mTimeOut" -> mTimeOut,
    "mSandboxEnabled" -> mSandboxEnabled,
    "mSandboxOS" -> mSandboxOS,
    "mSandboxTimeOut" -> mSandboxTimeOut,
    "mSandboxBrowser" -> mSandboxBrowser
  )

}
