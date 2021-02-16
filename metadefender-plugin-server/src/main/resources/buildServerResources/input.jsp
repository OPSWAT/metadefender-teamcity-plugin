<%@ include file="/include.jsp" %>

    <form action="/app/metadefender/config" method="post">
        <table class="runnerFormTable">
            <tbody>
                <tr class="groupingTitle">
                    <td colspan="2">MetaDefender Configuration</td>
                </tr>
                <tr>
                    <td class="grayNote" colspan="2">
                        Configure MetaDefender REST and API key for scans.
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mURL">MetaDefender URL</label>
                    </td>
                    <td>
                        <input type="text" id="mURL" name="mURL" value="${mURL}" class="longField">
                        <div class="grayNote">e.g.: https://api.metadefender.com/v4/file <br> or http://&lt;MetaDefender
                            Core V4 IP address&gt;:8008/file <br> or http://&lt;MetaDefender Core V3 IP
                            address&gt;:8008/metascan_rest/file</div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mAPIKey">API key</label>
                    </td>
                    <td>
                        <input type="text" id="mAPIKey" name="mAPIKey" value="${mAPIKey}" class="longField">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mTimeOut">Scan timeout</label>
                    </td>
                    <td>
                        <input type="text" id="mTimeOut" name="mTimeOut" value="${mTimeOut}"> mins
                        <div class="grayNote">Enter scan timeout per file (default is 30 mins)</div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mForceScan">Scan automatically when build runs</label>
                    </td>
                    <td>
                        <input type="checkbox" id="mForceScan" name="mForceScan" ${mForceScan} value="checked">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mViewDetail">Display scan result link</label>
                    </td>
                    <td>
                        <input type="checkbox" id="mViewDetail" name="mViewDetail" ${mViewDetail} value="checked">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mSandboxEnabled">Analyze files automatically with Sandbox (MetaDefender Cloud
                            only)</label>
                    </td>
                    <td>
                        <input type="checkbox" id="mSandboxEnabled" name="mSandboxEnabled" ${mSandboxEnabled}
                            value="checked">
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mSandboxOS">Sandbox operating system (MetaDefender Cloud only)</label>
                    </td>
                    <td>
                        <input type="text" id="mSandboxOS" name="mSandboxOS" value="${mSandboxOS}">
                        <div class="grayNote">Possible values: windows7 or windows10</div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mSandboxTimeOut">Sandbox analysis timeout (MetaDefender Cloud only)</label>
                    </td>
                    <td>
                        <input type="text" id="mSandboxTimeOut" name="mSandboxTimeOut" value="${mSandboxTimeOut}">
                        <div class="grayNote">Possible values: short or long (short=150s, long=300s)</div>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="mSandboxBrowser">Sandbox browser to use during analysis (MetaDefender Cloud
                            only)</label>
                    </td>
                    <td>
                        <input type="text" id="mSandboxBrowser" name="mSandboxBrowser" value="${mSandboxBrowser}">
                        <div class="grayNote">Possible values: os_default, chrome or firefox</div>
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <br>
                        To set different options for a particular build, you can set the following system properties in
                        the Build Configuration Parameters: <br>
                        <b> system.metadefender_scan_artifact</b> - If set to 1, the artifact will automatically be
                        scanned when the build runs. The default value is 0.<br>
                        <b> system.metadefender_scan_log</b> - If set to 1, the scan log metadefender_scan_log.txt will
                        be created in the artifact folder. The default value is 0.<br>
                        <b> system.metadefender_fail_build</b> - If set to 1, and a threat is found the build will
                        automatically fail. The default value is 1.<br>
                        <b> system.metadefender_scan_timeout</b> - Enter time out for one file in mins<br>
                        <b> system.metadefender_scan_artifact_with_sandbox</b> - If set to 1, the artifact will
                        automatically be analyzed by Sandbox when the build runs. The default value is 0.<br>
                        <b> system.metadefender_sandbox_os</b> - Set the Sandbox operating system: windows7 or
                        windows10. The default value is windows10.<br>
                        <b> system.metadefender_sandbox_timeout</b> - Set the Sandbox analysis timeout: short (150s) or
                        long (300s). The default value is short.<br>
                        <b> system.metadefender_sandbox_browser</b> - Set the Sandbox browser to use during analysis:
                        os_default, chrome or firefox. The default value is os_default.<br>
                        <i>Note that Sandbox analysis only works with MetaDefender Cloud (the MetaDefender URL parameter
                            must include "api.metadefender.com").</i><br>
                        <i>Note that specifying system properties in the build parameters will override the global
                            options. If you wish to use the global settings for the build, do not enter any system
                            properties.</i>
                        <br>
                        <br>
                    </td>
                </tr>
            </tbody>
        </table>
        <label for="mFailBuild" style="display:none">Fail build when issue is found</label>
        <input style="display:none" type="checkbox" id="mFailBuild" name="mFailBuild" ${mFailBuild} value="checked">
        <input type="submit" class="btn btn-default" value="Save">
    </form>