package com.opswat.teamcity

import java.util.Date
import java.security.MessageDigest
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpGet

import java.io._

import jetbrains.buildServer.BuildProblemData
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams

import scala.io.Source
import spray.json._
import jetbrains.buildServer.messages.{DefaultMessagesInfo, Status}
import jetbrains.buildServer.serverSide.{BuildServerAdapter, SRunningBuild}

import scala.util.Try

class ArtifactScanner(config: MConfigManager) extends BuildServerAdapter {
  override def beforeBuildFinish(runningBuild: SRunningBuild) {

    object lockerAllFiles
    //queue to store all file paths
    var allFiles = List("")
    var filePosition = 0

    object lockerInfectFiles
    var infectedFiles = List("")

    object lockerCleanFiles
    var cleanFiles = List("")

    object lockerOtherFiles
    var otherFiles = List("")

    object lockerInfectWithNumberEngine
    var infectedWithNumberEngine = List("")

    object lockerTotalFileInArchive
    var totalFileInArchive = 0

    var viewDetailsPrefix = ""
    var maxScanTimeSecond = 30 * 60
    val totalThread = 10
    var finishedThread = 0

    //read configuration and prepare some settings
    def prepareConfigs(): Unit = {
      //set Scan timeout
      val timeout = runningBuild.getParametersProvider.get("system.metadefender_scan_timeout")
      if (timeout == "" || !Try(timeout.toInt).isSuccess)
        maxScanTimeSecond = config.mTimeOut.mkString.toInt * 60
      else
        maxScanTimeSecond = timeout.toInt * 60

      //set Scan URL, it's kind of complicated checks dues to supported Core versions
      val URL = config.mURL.mkString
      if (URL contains "metadefender.com") {
        viewDetailsPrefix = "https://metadefender.opswat.com/results#!/file/"
        return
      }

      //detect Core V3 or V4
      val splitURL = URL.split("/")
      var apiVersionURL = URL + "/apiversion"
      if (URL.indexOf("/file") > 0) {
        apiVersionURL = splitURL(0) + "//" + splitURL(2) + "/apiversion"
      }

      val get = new HttpGet(apiVersionURL)
      if (config.mAPIKey.mkString != "") {
        get.setHeader("apikey", config.mAPIKey.mkString)
      }

      val httpParams: HttpParams = new BasicHttpParams()
      HttpConnectionParams.setConnectionTimeout(httpParams, 120000)
      val httpClient = new DefaultHttpClient(httpParams)

      try {
        val response = httpClient.execute(get)
        if (response.getStatusLine.getStatusCode == 200) {
          val jsonReturn = Source.fromInputStream(response.getEntity.getContent).mkString
          //hard to parse json here since v3 and v4 returning different result, just check string is fine
          if (jsonReturn contains "4") {
            viewDetailsPrefix = splitURL(0) + "//" + splitURL(2) + "/#/public/filescan/dataId/"
            return
          }
        }
      } catch {
        case e: Exception =>
          val tm = "Can't call " + URL + "/apiversion, error: " + e.getMessage
          reportError(tm)
          lockerOtherFiles.synchronized {
            otherFiles = tm :: otherFiles
          }
      }
      //all cases are treated as
      viewDetailsPrefix = splitURL(0) + "//" + splitURL(2) + "/#/scanresult/"
    }

    //return the value of a build parameter or the default if the parameter does not exist
    def getBuildParameter(parameter: String, default: String = ""): String = {
      val value = runningBuild.getParametersProvider.get(parameter)
      if (value != null && value != "") {
        return value
      }
      return default
    }

    //main scan function, it's a thread for faster scan
    def scanThread(): Unit = {
      val scanner = new Thread(new Runnable {
        def run() {
          val rootDir = runningBuild.getArtifactsDirectory.getAbsolutePath.mkString

          while (true) {

            //full file path to scan
            var fileToScan = ""

            //file name only, for logging
            var fileToLog = ""

            //make sure we have "/file" at the end
            var URL = config.mURL.mkString
            if (URL.indexOf("/file") < 0) {
              URL = URL + "/file"
            }

            try {
              //pick one file in queue to scan
              lockerAllFiles.synchronized {
                if (filePosition >= (allFiles.length - 1)) {
                  finishedThread += 1
                  return
                }
                fileToScan = allFiles(filePosition)
                filePosition += 1
              }

              //start submiting file to scan
              val f = new File(fileToScan)
              fileToLog = f.getName

              val post = new HttpPost(URL)
              post.setHeader("user_agent", "TeamCity")
              post.setHeader("Content-Type", "application/octet-stream")
              post.setHeader("filename", f.getName)
              if (config.mAPIKey.mkString != "") {
                post.setHeader("apikey", config.mAPIKey.mkString)
              }

              val buildParams = runningBuild.getParametersProvider
              val sandboxEnabled = (buildParams.get("system.metadefender_scan_artifact_with_sandbox") == "1" ||
                config.mSandboxEnabled.mkString == "checked")

              if (sandboxEnabled) {
                var sandboxOS =
                  getBuildParameter("system.metadefender_sandbox_os", config.mSandboxOS.mkString)
                if (sandboxOS == "") {
                  sandboxOS = "windows10"
                }
                post.setHeader("sandbox", sandboxOS)

                val sandboxTimeOut =
                  getBuildParameter("system.metadefender_sandbox_timeout", config.mSandboxTimeOut.mkString)
                if (sandboxTimeOut != "") {
                  post.setHeader("sandbox_timeout", sandboxTimeOut)
                }

                val sandboxBrowser =
                  getBuildParameter("system.metadefender_sandbox_browser", config.mSandboxBrowser.mkString)
                if (sandboxBrowser != "") {
                  post.setHeader("sandbox_browser", sandboxBrowser)
                }
                // A scan rule is needed to also trigger multiscanning
                post.setHeader("rule", "multiscan")
              }
              post.setEntity(new FileEntity(f))

              val httpParams: HttpParams = new BasicHttpParams()
              HttpConnectionParams.setConnectionTimeout(httpParams, 120000)
              val httpClient = new DefaultHttpClient(httpParams)
              var response = httpClient.execute(post)

              if (response.getStatusLine.getStatusCode == 200) {
                var jsonReturn = Source.fromInputStream(response.getEntity.getContent).mkString
                var jsonAst = JsonParser(jsonReturn)
                val sDataID: String = jsonAst.asJsObject.fields.get("data_id") match {
                  case Some(x: JsString) => x.value
                  case _                 => throw new Exception("Error data_id json: " + jsonReturn)
                }
                val restIP: Option[JsValue] = jsonAst.asJsObject.fields.get("rest_ip")
                if (restIP.isDefined) {
                  //HTTPS for metadefender.com...
                  val sRestIP: String = restIP match {
                    case Some(x: JsString) => x.value
                    case _                 => throw new Exception("Error rest_ip json: " + jsonReturn)
                  }
                  if (sRestIP.mkString contains "metadefender.com") {
                    URL = "https://" + sRestIP + "/file"
                  } else {
                    URL = "http://" + sRestIP + "/file"
                  }
                }
                val get = new HttpGet(URL + "/" + sDataID)
                if (config.mAPIKey.mkString != "")
                  get.setHeader("apikey", config.mAPIKey.mkString)

                var scanProgressPercentage: BigDecimal = 0
                var processProgressPercentage: BigDecimal = 0
                var startTime: BigDecimal = System.currentTimeMillis()
                var runTimeSeconds: BigDecimal = 0
                var scanAllResultI: BigDecimal = -1
                var scanResults: JsObject = JsObject()
                var processInfo: JsObject = JsObject()

                //Query scan result
                while (
                  (scanProgressPercentage != 100 || processProgressPercentage != 100) && runTimeSeconds < maxScanTimeSecond
                ) {
                  response = httpClient.execute(get)
                  if (response.getStatusLine.getStatusCode == 200) {
                    jsonReturn = Source.fromInputStream(response.getEntity.getContent)("UTF-8").mkString
                    jsonAst = JsonParser(jsonReturn)

                    scanResults = jsonAst.asJsObject.fields.get("scan_results") match {
                      case Some(x: JsObject) => x
                      case _                 => throw new Exception("Error scan_results json: " + jsonReturn)
                    }

                    processInfo = jsonAst.asJsObject.fields.get("process_info") match {
                      case Some(x: JsObject) => x
                      case _                 => null
                    }

                    //legacy v3
                    if (processInfo == null && scanProgressPercentage != 0) {
                      processProgressPercentage = 100
                    } else if (processInfo != null) {
                      processProgressPercentage = processInfo.fields.get("progress_percentage") match {
                        case Some(x: JsNumber) => x.value
                        case _                 => throw new Exception("Error progress_percentage json: " + jsonReturn)
                      }
                    }

                    scanProgressPercentage = scanResults.fields.get("progress_percentage") match {
                      case Some(x: JsNumber) => x.value
                      case _                 => throw new Exception("Error progress_percentage json: " + jsonReturn)
                    }

                    scanAllResultI = scanResults.fields.get("scan_all_result_i") match {
                      case Some(x: JsNumber) => x.value
                      case _                 => throw new Exception("Error scan_all_result_i json: " + jsonReturn)
                    }

                    Thread.sleep(500)
                  } else {
                    response.getEntity().consumeContent();
                    val tm = s"Scan error, code " + response.getStatusLine.getStatusCode + s" file: " + fileToLog
                    reportError(tm)
                    lockerOtherFiles.synchronized {
                      otherFiles = tm :: otherFiles
                    }

                    Thread.sleep(5000)
                  }
                  runTimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                }

                //Checking scan result i
                if (runTimeSeconds >= maxScanTimeSecond) {
                  reportError("Time out file: " + fileToLog)
                } else {

                  //build infected list
                  var fileInfo: JsObject = JsObject()
                  fileInfo = jsonAst.asJsObject.fields.get("file_info") match {
                    case Some(x: JsObject) => x
                    case _                 => throw new Exception("Error scan_results json: " + jsonReturn)
                  }

                  val displayName = fileInfo.asJsObject().fields.get("display_name") match {
                    case Some(x: JsString) => x.value
                    case _                 => null
                  }

                  var totalDetectedEngine = 0

                  //Parsing engine scan result to figure out number of engine detected
                  //there is no such total_infected_avs!
                  val scanDetails = scanResults.fields.get("scan_details") match {
                    case Some(x: JsObject) => x
                    case _                 => throw new Exception("Error scan_details json: " + jsonReturn);
                  }
                  val engineList: Map[String, JsValue] = scanDetails.fields
                  for ((engineName, v) <- engineList) {
                    val scanResultI = v.asJsObject.fields.get("scan_result_i") match {
                      case Some(x: JsNumber) => x.value
                      case _                 => throw new Exception("Error scan_result_i json: " + jsonReturn);
                    }
                    if (isInfectedResult(scanAllResultI)) {
                      totalDetectedEngine += 1
                    }
                  }

                  if (totalDetectedEngine > 0) {
                    val temp = displayName + " " + totalDetectedEngine + " "
                    lockerInfectWithNumberEngine.synchronized {
                      infectedWithNumberEngine = temp :: infectedWithNumberEngine
                    }
                  } else if (!isNoThreatDetected(scanAllResultI)) {
                    var temp = displayName + " " + scanAllResultI + " "
                    lockerInfectWithNumberEngine.synchronized {
                      infectedWithNumberEngine = temp :: infectedWithNumberEngine
                    }
                  }

                  //Check if it has progressInfo
                  if (processInfo == null) {
                    val scanAllResultA = scanResults.fields.get("scan_all_result_a") match {
                      case Some(x: JsString) => x.value
                      case _                 => throw new Exception("Error scan_all_result_a json: " + jsonReturn)
                    }
                    processScanResult(scanAllResultI, scanAllResultA, fileToLog, sDataID)
                  } else {
                    val processResult = processInfo.fields.get("result") match {
                      case Some(x: JsString) => x.value
                      case _                 => throw new Exception("Error process_info.result json: " + jsonReturn)
                    }
                    var tm = fileToLog + " process result: " + processResult + makeViewDetailLink(sDataID)
                    if (processResult.toLowerCase.equals("blocked")) {
                      val blockReason = processInfo.fields.get("blocked_reason") match {
                        case Some(x: JsString) => x.value
                        case _                 => throw new Exception("Error process_info.blocked_reason json: " + jsonReturn)
                      }
                      tm =
                        fileToLog + " process result: " + processResult + " | " + blockReason + " " + makeViewDetailLink(
                          sDataID
                        )
                      reportError(tm)
                      lockerInfectFiles.synchronized {
                        infectedFiles = tm :: infectedFiles
                      }
                    } else if (processResult.toLowerCase.equals("allowed")) {
                      reportInfo(tm)
                      lockerCleanFiles.synchronized {
                        cleanFiles = tm :: cleanFiles
                      }
                    } else {
                      reportError(tm)
                      lockerOtherFiles.synchronized {
                        otherFiles = tm :: otherFiles
                      }
                    }
                  }

                  //Check file inside archive
                  jsonAst.asJsObject.fields.get("extracted_files") match {
                    case Some(extractedFiles: JsObject) =>
                      extractedFiles.fields.get("files_in_archive") match {
                        case Some(filesInArchive: JsArray) =>
                          val totalFile = filesInArchive.elements.size
                          for (i <- 0 until totalFile) {
                            val t = filesInArchive.elements.apply(i)
                            val displayName = t.asJsObject().fields.get("display_name") match {
                              case Some(x: JsString) => x.value
                              case _                 => null
                            }
                            val detectedBy = t.asJsObject().fields.get("detected_by") match {
                              case Some(x: JsNumber) => x.value
                              case _                 => null
                            }
                            val scan_result_i = t.asJsObject().fields.get("scan_result_i") match {
                              case Some(x: JsNumber) => x.value
                              case _ =>
                                t.asJsObject().fields.get("scan_all_result_i") match {
                                  case Some(x: JsNumber) => x.value
                                  case _                 => null
                                }
                            }
                            val aId = t.asJsObject().fields.get("data_id") match {
                              case Some(x: JsString) => x.value
                              case _                 => null
                            }
                            lockerTotalFileInArchive.synchronized {
                              totalFileInArchive += 1
                            }
                            val tm =
                              displayName + " scan result i: " + scanResultItoA(scan_result_i) + makeViewDetailLink(aId)
                            if (displayName != null && scan_result_i != null && isInfectedResult(scan_result_i)) {
                              var temp = displayName + " " + detectedBy + " "
                              lockerInfectFiles.synchronized {
                                reportError(tm)
                                infectedFiles = tm :: infectedFiles
                              }
                              lockerInfectWithNumberEngine.synchronized {
                                infectedWithNumberEngine = temp :: infectedWithNumberEngine
                              }
                            } else if (isNoThreatDetected(scan_result_i)) {
                              lockerCleanFiles.synchronized {
                                cleanFiles = tm :: cleanFiles
                              }
                            }
                          }
                        case _ => null
                      }
                    case _ => null
                  }
                }
              } else {
                val tm = s"Scan error, code " + response.getStatusLine.getStatusCode + s", file: " + fileToLog
                reportError(tm)
                lockerOtherFiles.synchronized {
                  otherFiles = tm :: otherFiles
                }
              }
            } catch {
              case e: Exception =>
                val tm = "Scan error: " + fileToLog + ", error: " + e.getMessage.toString
                reportError(tm)
                lockerOtherFiles.synchronized {
                  otherFiles = tm :: otherFiles
                }
            }
          }
        }
      })
      scanner.start
    }

    if (
      (runningBuild.getParametersProvider.get("system.metadefender_scan_artifact") == "1" ||
        (runningBuild.getParametersProvider.get("system.metadefender_scan_artifact") != "0" &&
          config.mForceScan.mkString == "checked")) && config.mURL.mkString != ""
    ) {
      reportInfo("Scanning artifacts")
      prepareConfigs()
      reportInfo("Scan timeout: " + maxScanTimeSecond + " (s)")
      getAllFiles(runningBuild).foreach { case (name: String, artifact: File) =>
        //Push all files to list, do not put those folders such as
        //C:\ProgramData\JetBrains\TeamCity\system\artifacts\Test\tt\38\.teamcity\logs\buildLog.msg5
        //...
        if (artifact.getAbsolutePath.toString.indexOf(".teamcity" + File.separator) == -1)
          allFiles = artifact.getAbsoluteFile.toString :: allFiles
      }
      //Start all threads
      for (a <- 1 to 10) {
        scanThread()
      }

      //Checking scan finish
      while (finishedThread < totalThread) {
        Thread.sleep(1000)
      }

      //Scan finished
      reportInfo("Total scanned files: " + (Math.max(filePosition, 0) + totalFileInArchive))

      if (runningBuild.getParametersProvider.get("system.metadefender_fail_build") != "0") {
        if (infectedFiles.length > 1) {
          // first element is empty string
          infectedWithNumberEngine = infectedWithNumberEngine.sorted

          //This is very specific TC logic, it uses the hash to detect if a failure is new or old
          //The purpose of this code is if users "Mute" a build because of false positive, next time they run, it should not fail again
          //However, if there is any change such as number of engine, new file detected, it will generate a new hash which make build fail again
          //with this method, we can bring attention back to user
          var hash = " "
          for (x <- infectedWithNumberEngine) {
            hash += x
          }
          hash =
            MessageDigest.getInstance("MD5").digest(hash.getBytes).map(0xff & _).map { "%02x".format(_) }.foldLeft("") {
              _ + _
            }
          runningBuild.addBuildProblem(
            BuildProblemData.createBuildProblem(
              hash,
              " ",
              "Found " + (infectedFiles.length - 1) + " threat(s) + blocked result(s)"
            )
          )
        } else if (otherFiles.length > 1) {
          runningBuild.addBuildProblem(
            BuildProblemData.createBuildProblem(" ", " ", "Found " + (otherFiles.length - 1) + " issue(s)")
          )
        }
      }

      //Write log
      if (runningBuild.getParametersProvider.get("system.metadefender_scan_log") == "1") {
        //Write scan result
        val scanLogPath = runningBuild.getArtifactsDirectory + File.separator + "metadefender_scan_log.txt"
        val scanLogFile = new File(scanLogPath)
        if (scanLogFile.exists()) {
          scanLogFile.delete()
        }

        cleanFiles = "No threat found: " :: cleanFiles
        writeFile(cleanFiles, scanLogPath)

        infectedFiles = "Infected/Blocked: " :: infectedFiles
        writeFile(infectedFiles, scanLogPath)

        otherFiles = "Others: " :: otherFiles
        writeFile(otherFiles, scanLogPath)
      }
    }

    //display message in TC log, info level
    def reportInfo(msg: String): Unit = {
      runningBuild.getBuildLog.message(
        msg,
        Status.NORMAL,
        new Date,
        DefaultMessagesInfo.MSG_TEXT,
        DefaultMessagesInfo.SOURCE_ID,
        null
      )
    }

    //display message in TC log, error level (red line)
    def reportError(msg: String): Unit = {
      runningBuild.getBuildLog.message(
        msg,
        Status.ERROR,
        new Date,
        DefaultMessagesInfo.MSG_BUILD_FAILURE,
        DefaultMessagesInfo.SOURCE_ID,
        null
      )
    }

    def writeFile(listContent: List[String], filePath: String): Unit = {
      if (listContent.length == 2) return
      //empty value and title: Infected, no threat found, ...
      val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath, true)))
      for (x <- listContent) {
        writer.write(x + "\n")
      }
      writer.close()
    }

    //generate view details link, depends on Core environment, it has different link
    def makeViewDetailLink(dataID: String): String = {
      if (config.mViewDetail.mkString == "checked" && viewDetailsPrefix != "")
        if (viewDetailsPrefix contains "metadefender.opswat.com")
          return " | " + viewDetailsPrefix + dataID + "/regular/overview"
        else
          return " | " + viewDetailsPrefix + dataID
      else
        return ""
    }

    //prepare logging, data
    def processScanResult(scanAllResultI: BigDecimal, scanAllResultA: String, fileToLog: String, sDataID: String) = {
      if (isNoThreatDetected(scanAllResultI)) {
        lockerCleanFiles.synchronized {
          val tm = fileToLog + makeViewDetailLink(sDataID)
          cleanFiles = tm :: cleanFiles
        }
      } else if (isInfectedResult(scanAllResultI)) {
        val tm = fileToLog + " scan result: Infected" + makeViewDetailLink(sDataID)
        reportError(tm)
        lockerInfectFiles.synchronized {
          infectedFiles = tm :: infectedFiles
        }
      } else {
        val tm = fileToLog + " scan result: " + scanAllResultA + makeViewDetailLink(sDataID)
        reportError(tm)
        lockerOtherFiles.synchronized {
          otherFiles = tm :: otherFiles
        }
      }
    }

    //no threat detected or whitelist
    def isNoThreatDetected(scanAllResultI: BigDecimal): Boolean = {
      if (scanAllResultI == 0 || scanAllResultI == 7) {
        return true
      }

      return false
    }

    def isInfectedResult(scanAllResultI: BigDecimal): Boolean = {
      if (scanAllResultI == 1 || scanAllResultI == 2 || scanAllResultI == 6 || scanAllResultI == 8)
        return true

      return false
    }

    def scanResultItoA(scanAllResultI: BigDecimal): String = {
      var temp = scanAllResultI.toInt
      temp match {
        case 0  => return "No threat found"
        case 1  => return "Infected"
        case 2  => return "Suspicious"
        case 3  => return "Failed To Scan"
        case 4  => return "Cleaned"
        case 5  => return "Unknown"
        case 6  => return "Quarantined"
        case 7  => return "Skipped Clean"
        case 8  => return "Skipped Dirty"
        case 9  => return "Exceeded Archive Depth"
        case 10 => return "Not Scanned"
        case 11 => return "Aborted"
        case 12 => return "Encrypted"
        case 13 => return "Exceeded Archive Size"
        case 14 => return "Exceeded Archive File Number"
        case 15 => return "Encrypted document"
        case 16 => return "Exceeded Archive Timeout"
        case 17 => return "Filetype Mismatch"
        case 18 => return "Potentially Vulnerable File"
        // catch the default with a variable so you can print it
        case _ => return "Not defined"
      }
    }
  }

  def getAllFiles(runningBuild: SRunningBuild): Seq[(String, File)] = {
    if (!runningBuild.isArtifactsExists) {
      Nil
    } else {
      ArtifactScanner.getChildren(runningBuild.getArtifactsDirectory)
    }
  }
}

object ArtifactScanner {
  def getChildren(file: File, paths: Seq[String] = Nil, current: String = ""): Seq[(String, File)] = {
    file.listFiles.toSeq.flatMap { child =>
      if (child.isHidden) {
        Seq()
      } else {
        val newPath = current + child.getName
        if (child.isDirectory) {
          getChildren(child, paths, newPath + File.separator)
        } else {
          Seq((newPath, child))
        }
      }
    }
  }
}
