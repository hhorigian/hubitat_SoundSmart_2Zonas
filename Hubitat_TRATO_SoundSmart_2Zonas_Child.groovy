/*

Copyright 2026 - VH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

-------------------------------------------

SoundSmart MZ21 - Zona (Child Driver)

Driver específico para as zonas do SS-MZ21.
Baseado no "SoundSmart - Player", com as diferenças do MZ21:
  - Adicionado: inputlinein (Line In / Segue Master Zone)
  - Removido: inputbluetooth, inputusb (MZ21 não possui esses inputs nas zonas)

Change history:
PreREQ: 
!!!!! Precisa ter o driver SoundSmart Player Instalado + Driver do SoundSmart MZ21 PAI !!!!

1.0.0 - 2026  Versão inicial baseada no SoundSmart - Player 2.2.3.

NOTE: estrutura baseada no driver original de @tomw / @hhorigian

*/


metadata
{
    definition(name: "SoundSmart MZ21 - Zona", namespace: "TRATO", author: "VH", importUrl: "")
    {
        capability "AudioVolume"
        capability "Configuration"
        capability "Initialize"
        capability "MusicPlayer"
        capability "Refresh"
        capability "SpeechSynthesis"
        capability "Switch"
        capability "PushableButton"

        command "executeCommand", ["command"]
        command "inputhdmi"
        command "inputwifi"
        command "inputoptical"
        command "inputlinein"
        command "push"
        command "recreateChilds"
        command "removeChilds"
        command "recreateVolumeDimmerChild"
        command "removeVolumeDimmerChild"
        command "recreateMuteToggleChild"
        command "removeMuteToggleChild"
        command "playTuneInStation", ["stationId"]

        attribute "commStatus", "string"
        attribute "trackDescription", "string"
        attribute "input", "string"
        attribute "volume", "integer"
        attribute "numberOfButtons", "integer"
        attribute "URLLargeCoverFile", "string"
        attribute "ImageLargeCover", "string"
        attribute "trackname", "string"
        attribute "ipAddress", "string"
    }
}

    import groovy.transform.Field
    @Field static final String DRIVER = "by TRATO"
    @Field static final String USER_GUIDE = "https://github.com/hhorigian/hubitat_SoundSmart/blob/main/Player"


    String fmtHelpInfo(String str) {
    String prefLink = "<a href='${USER_GUIDE}' target='_blank'>${str}<br><div style='font-size: 70%;'>${DRIVER}</div></a>"
    return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>"
    }



preferences
{
    section
    {
        input "IP_address", "text", title: "IP address of SoundSmart Zone", required: true
        input "api_key_audio", "text",  title: "Audio CD Covers API Key ", required: false, defaultValue: "f72ca3d6b5086f9991adbfb3c183912b"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver")
        input name: "createButtonsOnSave", type: "bool", title: "Criar/atualizar Child Switches para botões ao salvar", defaultValue: false
    }
}

def playTuneInStation(stationId)
{
    logDebug("MZ21-Zona playTuneInStation(${stationId})")
    state.lastTuneInStationId = normalizeTuneInStationId(stationId)
    def tuneInUrl = "http://opml.radiotime.com/Tune.ashx?id=${state.lastTuneInStationId}"
    playTrack(tuneInUrl)
}


private String normalizeTuneInStationId(Object stationId) {
    String sid = (stationId ?: "").toString().trim()
    if (!sid) return ""
    if (!sid.toLowerCase().startsWith("s")) sid = "s${sid}"
    return sid
}

private Map getTuneInStationMeta(Object stationId) {
    String sid = normalizeTuneInStationId(stationId)
    if (!sid) return null

    String url = "https://opml.radiotime.com/describe.ashx?id=${sid}&render=json"
    logDebug("TuneIn describe URL = ${url}")

    try {
        def parsed = null
        httpGet(url) { resp ->
            if (resp?.data instanceof Map) {
                parsed = resp.data
            } else if (resp?.data) {
                parsed = new groovy.json.JsonSlurper().parseText(resp.data.toString())
            } else if (resp?.getData()) {
                def d = resp.getData()
                parsed = (d instanceof Map) ? d : new groovy.json.JsonSlurper().parseText(d.toString())
            }
        }

        if (!(parsed instanceof Map)) { logDebug("getTuneInStationMeta: resposta vazia"); return null }

        def candidates = []
        if (parsed?.body instanceof List) candidates.addAll(parsed.body)
        if (parsed?.head instanceof Map)  candidates << parsed.head
        if (parsed?.outline instanceof List) candidates.addAll(parsed.outline)

        def nested = []
        candidates.each { item ->
            if (item instanceof Map) {
                if (item?.children instanceof List) nested.addAll(item.children)
                if (item?.outline instanceof List)  nested.addAll(item.outline)
            }
        }
        candidates.addAll(nested)

        def best = candidates.find { item ->
            item instanceof Map && (
                item?.guide_id?.toString() == sid ||
                item?.preset_id?.toString() == sid ||
                item?.station_id?.toString() == sid ||
                item?.image || item?.logo || item?.text || item?.subtext || item?.name || item?.title
            )
        }

        if (!(best instanceof Map)) {
            return [name: null, logo: buildTuneInLogoUrl(sid), id: sid]
        }

        String name = best?.text?.toString()?.trim() ?: best?.subtext?.toString()?.trim() ?:
                      best?.name?.toString()?.trim() ?: best?.title?.toString()?.trim() ?:
                      best?.current_track?.toString()?.trim() ?: null
        String logo = best?.image?.toString()?.trim() ?: best?.logo?.toString()?.trim() ?: buildTuneInLogoUrl(sid)
        String foundId = best?.guide_id?.toString()?.trim() ?: best?.preset_id?.toString()?.trim() ?:
                         best?.station_id?.toString()?.trim() ?: sid

        return [name: name, logo: logo, id: foundId]
    } catch (e) {
        logDebug("getTuneInStationMeta error: ${e.message}")
        return [name: null, logo: buildTuneInLogoUrl(sid), id: sid]
    }
}

private String buildTuneInLogoUrl(Object stationId) {
    String sid = normalizeTuneInStationId(stationId)
    if (!sid) return ""
    return "http://cdn-profiles.tunein.com/${sid}/images/logoq.jpg"
}

private Map searchTuneInByName(String query) {
    if (!query?.trim()) return null
    String url = "http://opml.radiotime.com/Search.ashx?query=${java.net.URLEncoder.encode(query, 'UTF-8')}&types=station&render=json"
    logDebug("TuneIn search URL = ${url}")
    try {
        def parsed = null
        httpGet(url) { resp ->
            if (resp?.data instanceof Map)   parsed = resp.data
            else if (resp?.data)             parsed = new groovy.json.JsonSlurper().parseText(resp.data.toString())
        }
        if (!(parsed instanceof Map)) return null
        def items = []
        if (parsed?.body instanceof List) items.addAll(parsed.body)
        def station = items.find { it instanceof Map && it?.type == "audio" }
        if (!station) station = items.find { it instanceof Map && it?.guide_id }
        if (!station) return null
        String sid  = normalizeTuneInStationId(station?.guide_id ?: station?.preset_id ?: station?.station_id ?: "")
        String logo = station?.image?.toString()?.trim() ?: station?.logo?.toString()?.trim() ?: buildTuneInLogoUrl(sid)
        String name = station?.text?.toString()?.trim() ?: station?.name?.toString()?.trim() ?: query
        return [name: name, logo: logo, id: sid]
    } catch (e) {
        logDebug("searchTuneInByName() error: ${e.message}")
        return null
    }
}


def logDebug(msg)
{
    if (logEnable) { log.debug(msg) }
}

def installed()
{
    logDebug("MZ21-Zona installed()")
    initialize()
    scheduleDebugAutoOff()
}

def initialize()
{
    logDebug("MZ21-Zona initialize()")
    sendEvent(name: "commStatus", value: "unknown")
    sendEvent(name: "input", value: "--")
    sendEvent(name: "volume", value: 0)
    sendEvent(name: "trackDescription", value: "--")
    sendEvent(name: "numberOfButtons", value: 30)
    refresh()
    setVoicePromptsState()
    scheduleDebugAutoOff()
    syncIpAddressState()
}

def updated()
{
    logDebug("MZ21-Zona updated()")
    sendEvent(name: "volume", value: 0)
    sendEvent(name: "trackDescription", value: "--")
    sendEvent(name: "numberOfButtons", value: 30)
    configure()
    if (createButtonsOnSave) { createOrUpdateChildButtons(true) }
    scheduleDebugAutoOff()
    syncIpAddressState()
}

def configure()
{
    logDebug("MZ21-Zona configure()")
    unschedule()
    state.clear()
    initialize()
}

def refresh()  { refresh(false) }
def refreshTracks() { refreshTracks(false) }

def refresh(useCachedValues)
{
    logDebug("MZ21-Zona refresh()")
    if (null == IP_address) { return }
    unschedule()
    updateStatusEx(useCachedValues)
    updatePlayerStatus(useCachedValues)
    updateUriAndDesc(true)
    runIn(5, refresh)
}

def uninstalled()
{
    logDebug("MZ21-Zona uninstalled()")
    unschedule()
}

private void syncIpAddressState() {
    sendEvent(name: "ipAddress", value: (IP_address ?: ""))
}

// ---------------------------------------------------------------------------
// Push buttons
// ---------------------------------------------------------------------------

def push(pushed) {
    log.debug("push: button = ${pushed}")
    if (pushed == null) { log.warn("push: pushed is null.  Input ignored"); return }
    switch(pushed) {
        case "1" : inputwifi();     break
        case "2" : inputoptical();  break
        case "4" : inputlinein();   break   // Line In (was aux/bluetooth on base driver)
        case "6" : inputhdmi();     break
        case "20": preset1();  break
        case "21": preset2();  break
        case "22": preset3();  break
        case "23": preset4();  break
        case "24": preset5();  break
        case "25": preset6();  break
        case "26": preset7();  break
        case "27": preset8();  break
        case "28": preset9();  break
        case "29": preset10(); break
        case "35": promptDisable(); break
        case "36": promptEnable();  break
        case "37": loopMode(0); break
        case "38": loopMode(1); break
        case "39": loopMode(2); break
        case "40": loopMode(3); break
        default:
            "${pushed}"()
            break
    }
}

// ---------------------------------------------------------------------------
// Playback controls
// ---------------------------------------------------------------------------

def play()   { logDebug("MZ21-Zona play()"); resume() }

def resume()
{
    logDebug("MZ21-Zona resume()")
    if (httpGetExec("setPlayerCmd:resume")) { refresh() }
}

def pause()
{
    logDebug("MZ21-Zona pause()")
    if (httpGetExec("setPlayerCmd:pause")) { refresh() }
}

def stop()
{
    logDebug("MZ21-Zona stop()")
    if (httpGetExec("setPlayerCmd:stop")) { refresh() }
}

def mute()
{
    logDebug("MZ21-Zona mute()")
    if (httpGetExec("setPlayerCmd:mute:1")) { refresh() }
}

def unmute()
{
    logDebug("MZ21-Zona unmute()")
    if (httpGetExec("setPlayerCmd:mute:0")) { refresh() }
}

def setLevel(volumelevel)
{
    logDebug("MZ21-Zona setLevel()")
    if (!checkCommStatus()) { refresh(); return }
    def intVolumeLevel = (volumelevel >= 0) ? ((volumelevel <= 100) ? volumelevel : 100) : 0
    if (httpGetExec("setPlayerCmd:vol:" + intVolumeLevel)) { refresh() }
    if (volumelevel <= 0) { mute() } else { unmute() }
}

def setVolume(volumelevel) { logDebug("MZ21-Zona setVolume()"); setLevel(volumelevel) }

def volumeDown() { logDebug("MZ21-Zona volumeDown()"); setLevel(getCurrentVolumeLevel() - 5) }
def volumeUp()   { logDebug("MZ21-Zona volumeUp()");   setLevel(getCurrentVolumeLevel() + 5) }

def nextTrack()
{
    logDebug("MZ21-Zona nextTrack()")
    if (httpGetExec("setPlayerCmd:next")) { refresh() }
}

def previousTrack()
{
    logDebug("MZ21-Zona previousTrack()")
    if (httpGetExec("setPlayerCmd:prev")) { refresh() }
}

def playTrack(trackuri)
{
    logDebug("MZ21-Zona playTrack(${trackuri})")
    if (httpGetExec("setPlayerCmd:play:${trackuri}")) { refresh() }
}

def restoreTrack(trackuri) { playTrack(trackuri) }
def resumeTrack(trackuri)  { playTrack(trackuri) }

def setTrack(trackuri)
{
    logDebug("MZ21-Zona setTrack(${trackuri})")
    refresh()
    if (!checkCommStatus()) { refresh(); return }
    def muteStatus = getPlayerStatus().mute
    def playStatus = getPlayerStatus().status.toString()
    mute()
    playTrack(trackuri)
    switch (playStatus) {
        case "paused":  pause(); break
        case "stopped": stop();  break
    }
    if (!muteStatus.toInteger()) { unmute() }
    refresh()
}

def playText(text) { playTrack(textToSpeech(text, "Joanna").uri) }
def speak(text)    { playText(text) }

def executeCommand(suffix)
{
    logDebug("MZ21-Zona executeCommand(${suffix})")
    return httpGetExec(suffix)
}

// ---------------------------------------------------------------------------
// Loop / Presets / Prompts
// ---------------------------------------------------------------------------

def repeatall()       { loopMode(0) }
def repeatsingle()    { loopMode(1) }
def shufflerepeat()   { loopMode(2) }
def shufflenorepeat() { loopMode(3) }

def loopMode(loopmodevalue)
{
    logDebug("MZ21-Zona loopMode(${loopmodevalue})")
    if (httpGetExec("setPlayerCmd:loopmode:${loopmodevalue}")) { refresh() }
}

def promptDisable() { logDebug("Prompt Disabled"); executeCommand("PromptDisable") }
def promptEnable()  { logDebug("Prompt Enabled");  executeCommand("PromptEnable")  }

def preset1()  { executeCommand("MCUKeyShortClick:1")  }
def preset2()  { executeCommand("MCUKeyShortClick:2")  }
def preset3()  { executeCommand("MCUKeyShortClick:3")  }
def preset4()  { executeCommand("MCUKeyShortClick:4")  }
def preset5()  { executeCommand("MCUKeyShortClick:5")  }
def preset6()  { executeCommand("MCUKeyShortClick:6")  }
def preset7()  { executeCommand("MCUKeyShortClick:7")  }
def preset8()  { executeCommand("MCUKeyShortClick:8")  }
def preset9()  { executeCommand("MCUKeyShortClick:19") }
def preset10() { executeCommand("MCUKeyShortClick:10") }

// ---------------------------------------------------------------------------
// Input commands (MZ21-specific set — sem Bluetooth, sem USB)
// ---------------------------------------------------------------------------

def inputwifi()
{
    logDebug("MZ21-Zona → WiFi/Streaming")
    executeCommand("setPlayerCmd:switchmode:wifi")
}

def inputoptical()
{
    logDebug("MZ21-Zona → Óptico")
    executeCommand("setPlayerCmd:switchmode:optical")
}

def inputhdmi()
{
    logDebug("MZ21-Zona → HDMI")
    executeCommand("setPlayerCmd:switchmode:HDMI")
}

def inputlinein()
{
    logDebug("MZ21-Zona → Line In (Segue Master)")
    executeCommand("setPlayerCmd:switchmode:line-in")
    // Line In não é "playing" para automações
    sendEvent(name: "status", value: "stopped")
}

// Mantido como alias para compatibilidade com push() e automações existentes
def inputaux() { inputlinein() }

// ---------------------------------------------------------------------------
// HTTP / Status
// ---------------------------------------------------------------------------

def getBaseURI() { return "http://" + IP_address + "/httpapi.asp?command=" }

def updateStatusEx(useCachedValues)
{
    def resp_json
    if (useCachedValues) {
        resp_json = getStatusEx()
    } else {
        resp_json = parseJson(httpGetExec("getStatusEx"))
        if (resp_json) {
            setStatusEx(resp_json)
            sendEvent(name: "commStatus", value: "good")
        } else {
            sendEvent(name: "commStatus", value: "error")
            return
        }
    }
}

def setStatusEx(statusEx) { state.StatusEx = statusEx }
def getStatusEx()         { return state.StatusEx }

def updatePlayerStatus(useCachedValues)
{
    def resp_json
    if (useCachedValues) {
        resp_json = getPlayerStatus()
    } else {
        resp_json = parseJson(httpGetExec("getPlayerStatus"))
        if (resp_json) {
            setPlayerStatus(resp_json)
            sendEvent(name: "commStatus", value: "good")
        } else {
            sendEvent(name: "commStatus", value: "error")
            return
        }
    }

    sendEvent(name: "level",  value: resp_json.vol.toInteger())
    sendEvent(name: "volume", value: resp_json.vol.toInteger())
    sendEvent(name: "mute",   value: (resp_json.mute.toInteger() ? "muted" : "unmuted"))
    syncVolumeDimmerChild(resp_json.vol.toInteger())
    syncMuteToggleChild((resp_json.mute.toInteger() ? "muted" : "unmuted"))

    def tempStatus = ""
    switch (resp_json.status.toString()) {
        case "stop":  tempStatus = "stopped"; break
        case "play":  tempStatus = "playing"; break
        case "load":  tempStatus = "loading"; break
        case "pause": tempStatus = "paused";  break
    }
    if (resp_json.mode?.toString() == "40") { tempStatus = "stopped" }
    sendEvent(name: "status", value: tempStatus)

    def tempInput = ""
    switch (resp_json.mode.toString()) {
        case "10": tempInput = "Radio Online";           break
        case "31": tempInput = "Spotify";                break
        case "40": tempInput = "Line In (Segue Master)"; break
        case "43": tempInput = "Óptico";                 break
        case "1":  tempInput = "Airplay";                break
        case "0":  tempInput = "Sem Input";              break
        case "11": tempInput = "USB";                    break
        case "49": tempInput = "HDMI";                   break
    }
    sendEvent(name: "input", value: tempInput)
}

def setPlayerStatus(playerStatus) { state.PlayerStatus = playerStatus }
def getPlayerStatus()             { return state.PlayerStatus }
def getCurrentVolumeLevel()       { return getPlayerStatus().vol.toInteger() }

def setVoicePromptsState()
{
    if (logEnable) { executeCommand("PromptEnable") } else { executeCommand("PromptDisable") }
}

def checkCommStatus()
{
    switch (device.currentValue("commStatus")) {
        case "good":    logDebug("checkCommStatus() success"); return true
        case "error":
        case "unknown":
        default:        logDebug("checkCommStatus() failed"); return false
    }
}

def updateUriAndDesc(useCachedValues)
{
    if (!useCachedValues) { refresh() }
    if (!checkCommStatus()) { return }

    def tmpTrackDesc = "${hexToAscii(getPlayerStatus().Title)} | ${hexToAscii(getPlayerStatus().Artist)} "

    def tmpTitle  = hexToAscii(getPlayerStatus().Title)
    def tmpArtist = hexToAscii(getPlayerStatus().Artist)
    def tmpAlbum  = hexToAscii(getPlayerStatus().Album)

    if ((getPlayerStatus().mode == "31") && (getPlayerStatus().status == "play")) {
        tmpTrackData = "SPOTIFY"
        def tempapi_key_audio = "f72ca3d6b5086f9991adbfb3c183912b"
        def getcoverURI = "http://ws.audioscrobbler.com/2.0/?method=album.getInfo&api_key=" + api_key_audio + "&artist=" + tmpArtist + "&album=" + tmpAlbum + "&autocorrect=1&format=json"
        def coverfile2  = httpPOSTExec(getcoverURI)
        coverfileLarge  = httpPOSTExecLarge(getcoverURI)
        def tmpTrackDesc_temp = "${hexToAscii(getPlayerStatus().Title)}<br>${hexToAscii(getPlayerStatus().Artist)} "
        def tmpTrackName      = "${hexToAscii(getPlayerStatus().Title)} - ${hexToAscii(getPlayerStatus().Artist)}"
        def imgfile      = "<table style='border-collapse: collapse;margin-left: auto; margin-right: auto;border='0'><tr><td><img src=" + state.SmallAlbumCover + "></td>"
        def imgfileLarge = "<img src=" + state.LargeAlbumCover + " style=width:365px;>"
        tmpTrackDesc    = imgfile + tmpTrackDesc_temp
        tmpURLLargeCover = state.LargeAlbumCover
        tmpLargeCoverImg = imgfileLarge
        sendEvent(name: "trackData",        value: tmpTrackData)
        sendEvent(name: "trackDescription", value: tmpTrackDesc)
        sendEvent(name: "trackname",        value: tmpTrackName)
        sendEvent(name: "URLLargeCoverFile", value: tmpURLLargeCover)
        sendEvent(name: "ImageLargeCover",   value: tmpLargeCoverImg)

    } else if (getPlayerStatus()?.mode == "10") {
        def rTitle  = safeHexToAscii(getPlayerStatus()?.Title)
        def rArtist = safeHexToAscii(getPlayerStatus()?.Artist)
        def rAlbum  = safeHexToAscii(getPlayerStatus()?.Album)
        def st = getStatusEx()
        def sxStation = st?.StationName ?: st?.Station ?: st?.Title ?: st?.RadioName

        String currentTitleRaw = getPlayerStatus()?.Title ?: ""
        if (state.lastRadioTitleRaw != currentTitleRaw) {
            state.lastTuneInStationId = ""
            state.lastRadioTitleRaw   = currentTitleRaw
        }

        String tuneInStationId = normalizeTuneInStationId(state.lastTuneInStationId)
        Map tuneInMeta = tuneInStationId ? getTuneInStationMeta(tuneInStationId) : null

        if (!tuneInMeta || !tuneInMeta?.logo?.trim()) {
            String searchQuery = rTitle?.trim() ?: rArtist?.trim() ?: sxStation?.toString()?.trim()
            if (searchQuery) {
                Map found = searchTuneInByName(searchQuery)
                if (found) {
                    tuneInMeta = found
                    if (found.id) { state.lastTuneInStationId = found.id; tuneInStationId = found.id }
                }
            }
        }

        String station = tuneInMeta?.name?.toString()?.trim() ?: rTitle?.toString()?.trim() ?:
                         sxStation?.toString()?.trim() ?: rArtist?.toString()?.trim() ?:
                         rAlbum?.toString()?.trim() ?: "Rádio Online"
        String tuneInLogoUrl = tuneInMeta?.logo?.toString()?.trim() ?: buildTuneInLogoUrl(tuneInStationId)
        def hasLogo = tuneInLogoUrl?.trim()

        tmpTrackData = "RADIO"
        def imgfile = hasLogo
            ? "<table style='border-collapse: collapse;margin-left: auto; margin-right: auto;border='0'><tr><td><img src='${tuneInLogoUrl}'></td>"
            : "<table style='border-collapse: collapse;margin-left: auto; margin-right: auto;border='0'><tr><td></td>"
        def imgfileLarge = hasLogo ? "<img src='${tuneInLogoUrl}' style='width:365px;'>" : ""

        tmpTrackDesc    = imgfile + station
        tmpURLLargeCover = hasLogo ? tuneInLogoUrl : ""
        tmpLargeCoverImg = imgfileLarge

        sendEvent(name: "trackData",         value: tmpTrackData)
        sendEvent(name: "trackDescription",  value: tmpTrackDesc)
        sendEvent(name: "trackname",         value: station)
        sendEvent(name: "URLLargeCoverFile", value: tmpURLLargeCover)
        sendEvent(name: "ImageLargeCover",   value: tmpLargeCoverImg)

        state.currentTuneInStation = tuneInMeta ?: [name: null, logo: tuneInLogoUrl, id: tuneInStationId]
        state.SmallAlbumCover = hasLogo ? tuneInLogoUrl : ""
        state.LargeAlbumCover = hasLogo ? tuneInLogoUrl : ""
        return

    } else {
        tmpTrackData = "N/A"
    }
}

private String safeHexToAscii(Object hexStr) {
    try { return (hexStr ? hexToAscii(hexStr as String) : "") ?: "" }
    catch (ignored) { return "" }
}

def updateIP(ip)
{
    if (null == ip) { device.clearSetting("IP_address"); return }
    device.updateSetting("IP_address", ip.toString())
}

def getGroupingDetails()
{
    updateStatusEx(false)
    def statusEx = getStatusEx()
    if (null == statusEx) { logDebug("failed getGroupingDetails()"); return }
    def grpStrat = ("0.0.0.0" == statusEx?.eth2) ? "direct" : "router"
    return ([upnp_uuid: statusEx.upnp_uuid, ssid: statusEx.ssid, eth2: statusEx.eth2,
             apcli0: statusEx.apcli0, strategy: grpStrat, groupIP: "", name: statusEx.DeviceName, chan: statusEx.WifiChannel])
}

def hexToAscii(hexStr)
{
    return new String(hubitat.helper.HexUtils.hexStringToByteArray(hexStr))
}

def parseJson(resp)
{
    def jsonSlurper = new groovy.json.JsonSlurper()
    try { resp_json = jsonSlurper.parseText(resp.toString()); return resp_json }
    catch (Exception e) { log.warn "parse failed: ${e.message}"; return null }
}

def httpGetExec(suffix) {
    try {
        String url = (getBaseURI() + suffix).replaceAll(' ', '%20')
        if (suffix?.toLowerCase()?.startsWith("get")) {
            def ret = null
            logDebug("URL no httpget = " + url)
            httpGet(url) { resp -> if (resp?.data) ret = resp.data?.toString() }
            return ret
        }
        Map params = [uri: url, timeout: 7]
        asynchttpGet('httpCmdGetCallback', params, [suffix: suffix])
        logDebug("URL no ASYNChttpget = " + url)
        return true
    } catch (Exception e) {
        logDebug("httpGetExec() failed: ${e.message}")
        return null
    }
}

def httpPOSTExec(URI)
{
    try {
        getString = URI; segundo = ""
        httpPostJson(getString.replaceAll(' ', '%20'), segundo) { resp ->
            if (resp.data) { state.SmallAlbumCover = resp.data.album.image[1]."#text" }
        }
    } catch (Exception e) { logDebug("httpPostExec() failed: ${e.message}") }
}

def httpPOSTExecLarge(URI)
{
    try {
        getString = URI; segundo = ""
        httpPostJson(getString.replaceAll(' ', '%20'), segundo) { resp ->
            if (resp.data) { state.LargeAlbumCover = resp.data.album.image[4]."#text" }
        }
    } catch (Exception e) { logDebug("httpPostExec() failed: ${e.message}") }
}

void httpCmdGetCallback(resp, data) {
    Integer st = null
    try { st = resp?.status as Integer } catch (ignored) {}
    String suf = data?.suffix ?: ""
    if (st && st >= 200 && st < 300) {
        if (logEnable) log.debug "HTTP CMD OK (${st}) -> ${suf}"
    } else {
        log.warn "HTTP CMD FAIL (status=${st}) -> ${suf}"
    }
}

// ---------------------------------------------------------------------------
// Child Switches (Button Devices)
// ---------------------------------------------------------------------------

@Field static final List<Map> SS_CHILD_BUTTON_DEFS = [
  // Inputs disponíveis no MZ21
  [label:"MZ21 - Input WiFi/Streaming",     handler:"inputwifi"],
  [label:"MZ21 - Input Óptico",             handler:"inputoptical"],
  [label:"MZ21 - Input HDMI",               handler:"inputhdmi"],
  [label:"MZ21 - Line In (Segue Master)",   handler:"inputlinein"],

  // Volume e transporte
  [label:"MZ21 - Volume Up",                handler:"volumeUp"],
  [label:"MZ21 - Volume Down",              handler:"volumeDown"],
  [label:"MZ21 - Play/Resume",              handler:"play"],
  [label:"MZ21 - Pause",                    handler:"pause"],
  [label:"MZ21 - Stop",                     handler:"stop"],
  [label:"MZ21 - Next Track",               handler:"nextTrack"],
  [label:"MZ21 - Previous Track",           handler:"previousTrack"],

  // Presets 1..10
  [label:"MZ21 - Preset 1",                 handler:"preset1"],
  [label:"MZ21 - Preset 2",                 handler:"preset2"],
  [label:"MZ21 - Preset 3",                 handler:"preset3"],
  [label:"MZ21 - Preset 4",                 handler:"preset4"],
  [label:"MZ21 - Preset 5",                 handler:"preset5"],
  [label:"MZ21 - Preset 6",                 handler:"preset6"],
  [label:"MZ21 - Preset 7",                 handler:"preset7"],
  [label:"MZ21 - Preset 8",                 handler:"preset8"],
  [label:"MZ21 - Preset 9",                 handler:"preset9"],
  [label:"MZ21 - Preset 10",               handler:"preset10"],

  // Prompts
  [label:"MZ21 - Prompt Disable",           handler:"promptDisable"],
  [label:"MZ21 - Prompt Enable",            handler:"promptEnable"],

  // Loop/Shuffle
  [label:"MZ21 - Repeat All",               handler:"repeatall"],
  [label:"MZ21 - Repeat Single",            handler:"repeatsingle"],
  [label:"MZ21 - Shuffle Repeat",           handler:"shufflerepeat"],
  [label:"MZ21 - Shuffle No Repeat",        handler:"shufflenorepeat"]
]

def recreateChilds() { createOrUpdateChildButtons(true) }
def removeChilds()   { removeChildButtons() }

private void createOrUpdateChildButtons(Boolean removeExtras=false) {
  log.warn "Criando/atualizando Child Switches MZ21-Zona..."
  List<Map> defs = SS_CHILD_BUTTON_DEFS
  Set<String> keep = [] as Set

  createOrUpdateVolumeDimmerChild(false)
  keep << "${device.id}${SS_VOL_DIMMER_DNI_SUFFIX}"
  createOrUpdateMuteToggleChild(false)
  keep << "${device.id}${SS_MUTE_TOGGLE_DNI_SUFFIX}"

  defs.eachWithIndex { m, idx ->
    String label   = m.label as String
    String handler = m.handler as String
    String dni     = "${device.id}-SSBTN-${idx+1}"
    def child      = getChildDevice(dni)

    if (!child) {
      try {
        child = addChildDevice("hubitat", "Generic Component Switch", dni,
            [name: label, label: label, componentName: label, componentLabel: label, isComponent: true])
        log.warn "  ✓ Child criado: ${child?.displayName}"
      } catch (e) { log.error "  ✗ Falha '${label}' (${dni}): ${e}", e }
    } else {
      try { if (child.label != label) child.setLabel(label) } catch (ignored) {}
    }

    if (child) {
      try { child.updateDataValue("handler", handler); child.parse([[name:"switch", value:"off"]]) }
      catch (ignored) {}
      keep << dni
    }
  }

  if (removeExtras) {
    def extras = childDevices?.findAll { !(it.deviceNetworkId in keep) &&
        !it.deviceNetworkId?.endsWith(SS_VOL_DIMMER_DNI_SUFFIX) &&
        !it.deviceNetworkId?.endsWith(SS_MUTE_TOGGLE_DNI_SUFFIX) } ?: []
    extras.each {
      try { log.warn "Removendo extra: ${it.displayName}"; deleteChildDevice(it.deviceNetworkId) }
      catch (e) { log.error "Falha ao remover '${it.displayName}': ${e}", e }
    }
  }
  log.warn "Concluído. Childs ativos: ${childDevices?.size() ?: 0}"
}

def componentOn(cd) {
    if (cd?.deviceNetworkId?.endsWith(SS_MUTE_TOGGLE_DNI_SUFFIX)) { mute();   syncMuteToggleChild("muted");   return }
    if (cd?.deviceNetworkId?.endsWith(SS_VOL_DIMMER_DNI_SUFFIX))  { unmute(); return }
    handleChildPress(cd)
}

def componentOff(cd) {
    if (cd?.deviceNetworkId?.endsWith(SS_MUTE_TOGGLE_DNI_SUFFIX)) { unmute(); syncMuteToggleChild("unmuted"); return }
    if (cd?.deviceNetworkId?.endsWith(SS_VOL_DIMMER_DNI_SUFFIX))  { mute();   return }
}

def componentRefresh(cd) {
  try { cd.parse([[name:"switch", value:"off"]]) } catch (ignored) {}
}

private void handleChildPress(cd) {
  String handler = cd?.getDataValue("handler") ?: ""
  log.debug "Press '${cd?.displayName}' → handler='${handler}'"
  if (!handler) { log.warn "Child ${cd?.displayName} sem handler."; return }
  try { this."${handler}"() }
  catch (e) { log.error "Erro executando handler '${handler}': ${e}", e }
  runIn(1, "childOffSafe", [data:[dni: cd?.deviceNetworkId], overwrite: true])
}

def childOffSafe(data) {
  def child = data?.dni ? getChildDevice(data.dni as String) : null
  if (child) { try { child.parse([[name:"switch", value:"off"]]) } catch (ignored) {} }
}

private void removeChildButtons() {
  def toRemove = childDevices ?: []
  log.warn "Removendo ${toRemove.size()} child(s)..."
  int removed = 0
  toRemove.each { cd ->
    try { deleteChildDevice(cd.deviceNetworkId); removed++ }
    catch (e) { log.error "Falha ao remover '${cd.displayName}': ${e}", e }
  }
  log.warn "Remoção concluída. Total: ${removed}"
}

// ---------------------------------------------------------------------------
// Volume Dimmer Child (HomeKit)
// ---------------------------------------------------------------------------

@Field static final String SS_VOL_DIMMER_DNI_SUFFIX = "-SSVOL-1"
@Field static final String SS_VOL_DIMMER_LABEL      = "MZ21-Zona - Volume (HomeKit)"

def recreateVolumeDimmerChild() { createOrUpdateVolumeDimmerChild(true) }

def removeVolumeDimmerChild() {
    String dni = "${device.id}${SS_VOL_DIMMER_DNI_SUFFIX}"
    def child = getChildDevice(dni)
    if (child) { try { deleteChildDevice(dni); log.warn "Child dimmer removido." } catch (e) { log.error "${e}", e } }
}

private void createOrUpdateVolumeDimmerChild(Boolean force=false) {
    String dni = "${device.id}${SS_VOL_DIMMER_DNI_SUFFIX}"
    def child = getChildDevice(dni)
    if (!child) {
        try {
            child = addChildDevice("hubitat", "Generic Component Dimmer", dni,
                [name: SS_VOL_DIMMER_LABEL, label: SS_VOL_DIMMER_LABEL,
                 componentName: SS_VOL_DIMMER_LABEL, componentLabel: SS_VOL_DIMMER_LABEL, isComponent: true])
            log.warn "✓ Child dimmer criado: ${child?.displayName}"
        } catch (e) { log.error "✗ Falha child dimmer (${dni}): ${e}", e; return }
    } else {
        try { if (child.label != SS_VOL_DIMMER_LABEL) child.setLabel(SS_VOL_DIMMER_LABEL) } catch (ignored) {}
    }
    try {
        Integer v = device.currentValue("volume") as Integer ?: device.currentValue("level") as Integer ?: 0
        v = Math.max(0, Math.min(100, v))
        child.parse([[name:"level", value: v, unit:"%"], [name:"switch", value: (v > 0 ? "on":"off")]])
    } catch (ignored) {}
}

def componentSetLevel(cd, level, duration=null) {
    try { setLevel((level as Integer)) }
    catch (e) { log.error "Erro componentSetLevel: ${e}", e }
}

private void syncVolumeDimmerChild(Integer v) {
    String dni = "${device.id}${SS_VOL_DIMMER_DNI_SUFFIX}"
    def child  = getChildDevice(dni)
    if (!child) return
    try {
        v = Math.max(0, Math.min(100, v ?: 0))
        child.parse([[name:"level", value: v, unit:"%"], [name:"switch", value: (v > 0 ? "on":"off")]])
    } catch (ignored) {}
}

// ---------------------------------------------------------------------------
// Mute Toggle Child (HomeKit)
// ---------------------------------------------------------------------------

@Field static final String SS_MUTE_TOGGLE_DNI_SUFFIX = "-SSMUTE-1"
@Field static final String SS_MUTE_TOGGLE_LABEL      = "MZ21-Zona - Mute/Unmute"

def recreateMuteToggleChild() { createOrUpdateMuteToggleChild(true) }

def removeMuteToggleChild() {
    String dni = "${device.id}${SS_MUTE_TOGGLE_DNI_SUFFIX}"
    def child  = getChildDevice(dni)
    if (child) { try { deleteChildDevice(dni); log.warn "Child mute removido." } catch (e) { log.error "${e}", e } }
}

private void createOrUpdateMuteToggleChild(Boolean force=false) {
    String dni = "${device.id}${SS_MUTE_TOGGLE_DNI_SUFFIX}"
    def child  = getChildDevice(dni)
    if (!child) {
        try {
            child = addChildDevice("hubitat", "Generic Component Switch", dni,
                [name: SS_MUTE_TOGGLE_LABEL, label: SS_MUTE_TOGGLE_LABEL,
                 componentName: SS_MUTE_TOGGLE_LABEL, componentLabel: SS_MUTE_TOGGLE_LABEL, isComponent: true])
            log.warn "✓ Child mute criado: ${child?.displayName}"
        } catch (e) { log.error "✗ Falha child mute (${dni}): ${e}", e; return }
    } else {
        try { if (child.label != SS_MUTE_TOGGLE_LABEL) child.setLabel(SS_MUTE_TOGGLE_LABEL) } catch (ignored) {}
    }
    syncMuteToggleChild(device.currentValue("mute"))
}

private void syncMuteToggleChild(Object muteVal) {
    String dni = "${device.id}${SS_MUTE_TOGGLE_DNI_SUFFIX}"
    def child  = getChildDevice(dni)
    if (!child) return
    String sw = ((muteVal ?: "").toString() == "muted") ? "on" : "off"
    try { child.parse([[name:"switch", value: sw]]) } catch (ignored) {}
}

// ---------------------------------------------------------------------------
// Debug Auto-Off
// ---------------------------------------------------------------------------

def logsOff() {
    log.warn "Debug logging desligado automaticamente."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}

private void scheduleDebugAutoOff() {
    if (logEnable) {
        log.warn "Debug ativado — desligará em 30 minutos."
        runIn(1800, "logsOff")
    } else {
        unschedule("logsOff")
    }
}
