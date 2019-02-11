package com.opswat.teamcity

import jetbrains.buildServer.serverSide.{SBuild, BuildServerListener}
import jetbrains.buildServer.util.EventDispatcher

class MPlugin(eventDispatcher: EventDispatcher[BuildServerListener], mConfigManager: MConfigManager) {
  eventDispatcher.addListener(new ArtifactScanner(mConfigManager))
}

object MPlugin {
  def cleanFullName(build: SBuild): String = build.getFullName.split(" :: ").mkString("::")
}