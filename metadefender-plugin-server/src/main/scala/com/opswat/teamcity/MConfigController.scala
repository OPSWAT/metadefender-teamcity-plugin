package com.opswat.teamcity

import jetbrains.buildServer.controllers.MultipartFormController
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jetbrains.annotations.NotNull
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import scala.util.Try

class MConfigController(config: MConfigManager, webControllerManager: WebControllerManager)
    extends MultipartFormController {
  webControllerManager.registerController("/app/metadefender/**", this)

  protected def doPost(request: HttpServletRequest, response: HttpServletResponse): ModelAndView = {
    def param(name: String) = MConfigController.emptyAsNone(request.getParameter(name), name)
    def checkbox(name: String): Option[String] = {
      var z: Array[String] = request.getParameterValues(name)
      if (z != null)
        return Option(z(0))
      return Option("")
    }
    config.updateAndPersist(
      MConfig(
        param("mURL"),
        param("mAPIKey"),
        param("mViewDetail"),
        checkbox("mForceScan"),
        checkbox("mFailBuild"),
        param("mTimeOut"),
        checkbox("mSandboxEnabled"),
        param("mSandboxOS"),
        param("mSandboxTimeOut"),
        param("mSandboxBrowser")
      )
    )

    new ModelAndView(new RedirectView("/admin/admin.html?item=MetaDefender"))
  }
}

object MConfigController {
  def emptyAsNone(s: String, n: String): Option[String] = {
    if (n == "mTimeOut" && (s == "" || !Try(s.toInt).isSuccess))
      return Option("30")
    return Option(s).filterNot(_.trim.isEmpty)
  }
}
