package com.opswat.teamcity

import jetbrains.buildServer.controllers.admin.AdminPage
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.web.openapi.{Groupable, PagePlaces, PluginDescriptor}
import javax.servlet.http.HttpServletRequest
import java.util.Map

class MConfigPage(extension: MConfigManager, pagePlaces: PagePlaces, descriptor: PluginDescriptor)
    extends AdminPage(
      pagePlaces,
      "OPSWAT MetaDefender",
      descriptor.getPluginResourcesPath("input.jsp"),
      "OPSWAT MetaDefender"
    ) {

  register()

  override def fillModel(model: Map[String, AnyRef], request: HttpServletRequest) {
    import collection.convert.wrapAll._

    model.putAll(extension.details.mapValues(_.getOrElse("")))
  }

  override def isAvailable(request: HttpServletRequest): Boolean = {
    super.isAvailable(request) && checkHasGlobalPermission(request, Permission.CHANGE_SERVER_SETTINGS)
  }

  def getGroup: String = {
    Groupable.SERVER_RELATED_GROUP
  }
}
