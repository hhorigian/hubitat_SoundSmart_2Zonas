/*

Copyright 2026 - VH / TRATO

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

SoundSmart SS-MZ21 - Parent Driver

O MZ21 possui 3 zonas LinkPlay independentes:
  - Zona Master : sem amplificação, recebe HDMI ARC / Óptico / Streaming
  - Zona 1      : com amplificação, inputs próprios + Line In (segue Master)
  - Zona 2      : com amplificação, inputs próprios + Line In (segue Master)

Arquitetura:
  - Este driver pai gerencia as 3 zonas e exibe o status agregado
  - Ao criar os Zone Devices, 3 filhos do tipo "SoundSmart - Player" são
    adicionados automaticamente (um por zona), cada um com o seu próprio IP
  - O controle individual completo de cada zona é feito pelo filho
  - Este driver adiciona os comandos específicos do MZ21:
      * Zone 1 / Zone 2 seguem a Master via Line In
      * Controle de input da Master (HDMI, Óptico, Streaming)
      * Comandos de grupo (todas as zonas seguem master, etc.)

Change history:
PreREQ: Precisa ter o driver SoundSmart Player Instalado
1.0.0 - 2026  Versão inicial. Driver pai MZ21 com child devices por zona.

*/

import groovy.transform.Field

metadata {
    definition(name: "SoundSmart MZ21", namespace: "TRATO", author: "VH", importUrl: "") {

        capability "Refresh"
        capability "Configuration"
        capability "Initialize"

        // --- Gerenciamento dos filhos ---
        command "createZoneDevices", [[name: "Criar/atualizar os 3 dispositivos de zona (filhos)"]]
        command "removeZoneDevices",  [[name: "Remover todos os dispositivos de zona (filhos)"]]

        // --- Master Zone ---
        command "masterSetHDMI",      [[name: "Muda Master para HDMI (ARC)"]]
        command "masterSetOptical",   [[name: "Muda Master para Óptico"]]
        command "masterSetStreaming",  [[name: "Muda Master para Streaming (WiFi)"]]
        command "masterSetBluetooth", [[name: "Muda Master para Bluetooth"]]

        // --- Zona 1 ---
        command "zone1FollowMaster",  [[name: "Zona 1 segue Master (Line In)"]]
        command "zone1Independent",   [[name: "Zona 1 volta para Streaming independente"]]
        command "zone1SetHDMI",       [[name: "Zona 1 → HDMI"]]
        command "zone1SetOptical",    [[name: "Zona 1 → Óptico"]]
        command "zone1SetBluetooth",  [[name: "Zona 1 → Bluetooth"]]
        command "zone1SetVolume",     [[name: "volume*", type: "NUMBER", description: "0-100"]]
        command "zone1VolumeUp",      [[name: "Aumenta volume da Zona 1 em 5"]]
        command "zone1VolumeDown",    [[name: "Diminui volume da Zona 1 em 5"]]
        command "zone1Mute",          [[name: "Muta Zona 1"]]
        command "zone1Unmute",        [[name: "Desmuta Zona 1"]]
        command "zone1Play",          [[name: "Play/Resume Zona 1"]]
        command "zone1Pause",         [[name: "Pause Zona 1"]]
        command "zone1Stop",          [[name: "Stop Zona 1"]]
        command "zone1NextTrack",     [[name: "Próxima faixa Zona 1"]]
        command "zone1PrevTrack",     [[name: "Faixa anterior Zona 1"]]

        // --- Zona 2 ---
        command "zone2FollowMaster",  [[name: "Zona 2 segue Master (Line In)"]]
        command "zone2Independent",   [[name: "Zona 2 volta para Streaming independente"]]
        command "zone2SetHDMI",       [[name: "Zona 2 → HDMI"]]
        command "zone2SetOptical",    [[name: "Zona 2 → Óptico"]]
        command "zone2SetBluetooth",  [[name: "Zona 2 → Bluetooth"]]
        command "zone2SetVolume",     [[name: "volume*", type: "NUMBER", description: "0-100"]]
        command "zone2VolumeUp",      [[name: "Aumenta volume da Zona 2 em 5"]]
        command "zone2VolumeDown",    [[name: "Diminui volume da Zona 2 em 5"]]
        command "zone2Mute",          [[name: "Muta Zona 2"]]
        command "zone2Unmute",        [[name: "Desmuta Zona 2"]]
        command "zone2Play",          [[name: "Play/Resume Zona 2"]]
        command "zone2Pause",         [[name: "Pause Zona 2"]]
        command "zone2Stop",          [[name: "Stop Zona 2"]]
        command "zone2NextTrack",     [[name: "Próxima faixa Zona 2"]]
        command "zone2PrevTrack",     [[name: "Faixa anterior Zona 2"]]

        // --- Grupo ---
        command "allZonesFollowMaster",  [[name: "Zona 1 e Zona 2 seguem Master"]]
        command "allZonesIndependent",   [[name: "Zona 1 e Zona 2 voltam para Streaming independente"]]
        command "allZonesStop",          [[name: "Stop em todas as zonas"]]
        command "allZonesMute",          [[name: "Muta todas as zonas"]]
        command "allZonesUnmute",        [[name: "Desmuta todas as zonas"]]

        // --- Atributos de status por zona ---
        attribute "masterInput",   "string"
        attribute "masterStatus",  "string"
        attribute "masterVolume",  "number"
        attribute "masterTrack",   "string"

        attribute "zone1Input",    "string"
        attribute "zone1Status",   "string"
        attribute "zone1Volume",   "number"
        attribute "zone1Track",    "string"
        attribute "zone1Muted",    "string"

        attribute "zone2Input",    "string"
        attribute "zone2Status",   "string"
        attribute "zone2Volume",   "number"
        attribute "zone2Track",    "string"
        attribute "zone2Muted",    "string"

        attribute "commStatus",    "string"
    }
}

@Field static final String DRIVER_VERSION = "1.0.0"
@Field static final String DRIVER_NAME    = "SoundSmart MZ21"

preferences {
    section {
        input "IP_master", "text", title: "IP da Zona Master (entrada Ethernet/LAN do MZ21)", required: true
        input "IP_zone1",  "text", title: "IP da Zona 1", required: true
        input "IP_zone2",  "text", title: "IP da Zona 2", required: true
        input name: "pollInterval", type: "enum", title: "Intervalo de atualização de status",
              options: ["5":"5 segundos", "10":"10 segundos", "30":"30 segundos", "60":"1 minuto"],
              defaultValue: "10"
        input name: "logEnable", type: "bool", title: "Ativar debug logging (desliga em 30 min)", defaultValue: false
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    logDebug("${DRIVER_NAME} installed()")
    initialize()
    scheduleDebugAutoOff()
}

def updated() {
    logDebug("${DRIVER_NAME} updated()")
    configure()
    scheduleDebugAutoOff()
    syncChildIPs()
}

def configure() {
    logDebug("${DRIVER_NAME} configure()")
    unschedule()
    schedulePolling()
    refresh()
}

def initialize() {
    logDebug("${DRIVER_NAME} initialize()")
    sendEvent(name: "commStatus",   value: "unknown")
    sendEvent(name: "masterInput",  value: "--")
    sendEvent(name: "zone1Input",   value: "--")
    sendEvent(name: "zone2Input",   value: "--")
    sendEvent(name: "masterStatus", value: "--")
    sendEvent(name: "zone1Status",  value: "--")
    sendEvent(name: "zone2Status",  value: "--")
    schedulePolling()
    refresh()
}

def uninstalled() {
    logDebug("${DRIVER_NAME} uninstalled()")
    unschedule()
}

// ---------------------------------------------------------------------------
// Polling / Refresh
// ---------------------------------------------------------------------------

private void schedulePolling() {
    Integer secs = (pollInterval ?: "10").toInteger()
    logDebug("schedulePolling() a cada ${secs}s")
    runIn(secs, "refresh")
}

def refresh() {
    logDebug("${DRIVER_NAME} refresh()")
    unschedule("refresh")

    if (!IP_master && !IP_zone1 && !IP_zone2) {
        logDebug("Nenhum IP configurado, skip.")
        schedulePolling()
        return
    }

    boolean anyGood = false
    if (IP_master) anyGood = pollZoneStatus(IP_master, "master") || anyGood
    if (IP_zone1)  anyGood = pollZoneStatus(IP_zone1,  "zone1")  || anyGood
    if (IP_zone2)  anyGood = pollZoneStatus(IP_zone2,  "zone2")  || anyGood

    sendEvent(name: "commStatus", value: anyGood ? "good" : "error")
    schedulePolling()
}

private boolean pollZoneStatus(String ip, String prefix) {
    try {
        def raw = httpGetZone(ip, "getPlayerStatus")
        if (!raw) { logDebug("pollZoneStatus(${prefix}) sem resposta"); return false }

        def json = parseJson(raw)
        if (!json) return false

        // Volume
        Integer vol = json.vol?.toInteger() ?: 0
        sendEvent(name: "${prefix}Volume", value: vol)

        // Mute (zone1/zone2 only)
        if (prefix != "master") {
            String muteStr = json.mute?.toString() == "1" ? "muted" : "unmuted"
            sendEvent(name: "${prefix}Muted", value: muteStr)
        }

        // Status
        String rawStatus = json.status?.toString() ?: ""
        String status
        switch (rawStatus) {
            case "play":  status = "playing"; break
            case "pause": status = "paused";  break
            case "load":  status = "loading"; break
            default:      status = "stopped"; break
        }
        // Line-In / AUX = stopped for dash integrations
        if (json.mode?.toString() == "40") status = "stopped"
        sendEvent(name: "${prefix}Status", value: status)

        // Input mode
        String inputLabel = resolveInputLabel(json.mode?.toString())
        sendEvent(name: "${prefix}Input", value: inputLabel)

        // Track
        String title  = safeHexToAscii(json.Title)
        String artist = safeHexToAscii(json.Artist)
        String track  = (title && artist) ? "${title} | ${artist}" : (title ?: artist ?: "--")
        sendEvent(name: "${prefix}Track", value: track)

        return true
    } catch (Exception e) {
        logDebug("pollZoneStatus(${prefix}) erro: ${e.message}")
        return false
    }
}

private String resolveInputLabel(String mode) {
    switch (mode) {
        case "10":  return "Radio Online"
        case "31":  return "Spotify"
        case "40":  return "Line In"
        case "41":  return "Bluetooth"
        case "43":  return "Óptico"
        case "49":  return "HDMI"
        case "1":   return "Airplay"
        case "11":  return "USB"
        case "0":   return "Sem Input"
        default:    return mode ? "Modo ${mode}" : "--"
    }
}

// ---------------------------------------------------------------------------
// Master Zone Commands
// ---------------------------------------------------------------------------

def masterSetHDMI() {
    logDebug("Master → HDMI")
    zoneCmd(IP_master, "setPlayerCmd:switchmode:HDMI")
}

def masterSetOptical() {
    logDebug("Master → Óptico")
    zoneCmd(IP_master, "setPlayerCmd:switchmode:optical")
}

def masterSetStreaming() {
    logDebug("Master → Streaming (WiFi)")
    zoneCmd(IP_master, "setPlayerCmd:switchmode:wifi")
}

def masterSetBluetooth() {
    logDebug("Master → Bluetooth")
    zoneCmd(IP_master, "setPlayerCmd:switchmode:bluetooth")
}

// ---------------------------------------------------------------------------
// Zona 1 Commands
// ---------------------------------------------------------------------------

def zone1FollowMaster() {
    logDebug("Zona 1 → Line In (segue Master)")
    zoneCmd(IP_zone1, "setPlayerCmd:switchmode:line-in")
    sendEvent(name: "zone1Status", value: "stopped")
}

def zone1Independent() {
    logDebug("Zona 1 → Streaming independente")
    zoneCmd(IP_zone1, "setPlayerCmd:switchmode:wifi")
}

def zone1SetHDMI() {
    logDebug("Zona 1 → HDMI")
    zoneCmd(IP_zone1, "setPlayerCmd:switchmode:HDMI")
}

def zone1SetOptical() {
    logDebug("Zona 1 → Óptico")
    zoneCmd(IP_zone1, "setPlayerCmd:switchmode:optical")
}

def zone1SetBluetooth() {
    logDebug("Zona 1 → Bluetooth")
    zoneCmd(IP_zone1, "setPlayerCmd:switchmode:bluetooth")
}

def zone1SetVolume(Number vol) {
    Integer v = clampVolume(vol)
    logDebug("Zona 1 volume → ${v}")
    zoneCmd(IP_zone1, "setPlayerCmd:vol:${v}")
}

def zone1VolumeUp() {
    def cur = (device.currentValue("zone1Volume") ?: 0).toInteger()
    zone1SetVolume(cur + 5)
}

def zone1VolumeDown() {
    def cur = (device.currentValue("zone1Volume") ?: 0).toInteger()
    zone1SetVolume(cur - 5)
}

def zone1Mute()   { zoneCmd(IP_zone1, "setPlayerCmd:mute:1") }
def zone1Unmute() { zoneCmd(IP_zone1, "setPlayerCmd:mute:0") }

def zone1Play()      { zoneCmd(IP_zone1, "setPlayerCmd:resume") }
def zone1Pause()     { zoneCmd(IP_zone1, "setPlayerCmd:pause") }
def zone1Stop()      { zoneCmd(IP_zone1, "setPlayerCmd:stop") }
def zone1NextTrack() { zoneCmd(IP_zone1, "setPlayerCmd:next") }
def zone1PrevTrack() { zoneCmd(IP_zone1, "setPlayerCmd:prev") }

// ---------------------------------------------------------------------------
// Zona 2 Commands
// ---------------------------------------------------------------------------

def zone2FollowMaster() {
    logDebug("Zona 2 → Line In (segue Master)")
    zoneCmd(IP_zone2, "setPlayerCmd:switchmode:line-in")
    sendEvent(name: "zone2Status", value: "stopped")
}

def zone2Independent() {
    logDebug("Zona 2 → Streaming independente")
    zoneCmd(IP_zone2, "setPlayerCmd:switchmode:wifi")
}

def zone2SetHDMI() {
    logDebug("Zona 2 → HDMI")
    zoneCmd(IP_zone2, "setPlayerCmd:switchmode:HDMI")
}

def zone2SetOptical() {
    logDebug("Zona 2 → Óptico")
    zoneCmd(IP_zone2, "setPlayerCmd:switchmode:optical")
}

def zone2SetBluetooth() {
    logDebug("Zona 2 → Bluetooth")
    zoneCmd(IP_zone2, "setPlayerCmd:switchmode:bluetooth")
}

def zone2SetVolume(Number vol) {
    Integer v = clampVolume(vol)
    logDebug("Zona 2 volume → ${v}")
    zoneCmd(IP_zone2, "setPlayerCmd:vol:${v}")
}

def zone2VolumeUp() {
    def cur = (device.currentValue("zone2Volume") ?: 0).toInteger()
    zone2SetVolume(cur + 5)
}

def zone2VolumeDown() {
    def cur = (device.currentValue("zone2Volume") ?: 0).toInteger()
    zone2SetVolume(cur - 5)
}

def zone2Mute()   { zoneCmd(IP_zone2, "setPlayerCmd:mute:1") }
def zone2Unmute() { zoneCmd(IP_zone2, "setPlayerCmd:mute:0") }

def zone2Play()      { zoneCmd(IP_zone2, "setPlayerCmd:resume") }
def zone2Pause()     { zoneCmd(IP_zone2, "setPlayerCmd:pause") }
def zone2Stop()      { zoneCmd(IP_zone2, "setPlayerCmd:stop") }
def zone2NextTrack() { zoneCmd(IP_zone2, "setPlayerCmd:next") }
def zone2PrevTrack() { zoneCmd(IP_zone2, "setPlayerCmd:prev") }

// ---------------------------------------------------------------------------
// Aggregate Commands
// ---------------------------------------------------------------------------

def allZonesFollowMaster() {
    logDebug("Zona 1 e Zona 2 → Line In (seguem Master)")
    zone1FollowMaster()
    zone2FollowMaster()
}

def allZonesIndependent() {
    logDebug("Zona 1 e Zona 2 → Streaming independente")
    zone1Independent()
    zone2Independent()
}

def allZonesStop() {
    logDebug("Stop em todas as zonas")
    zoneCmd(IP_master, "setPlayerCmd:stop")
    zoneCmd(IP_zone1,  "setPlayerCmd:stop")
    zoneCmd(IP_zone2,  "setPlayerCmd:stop")
    runIn(2, "refresh")
}

def allZonesMute() {
    logDebug("Muta todas as zonas")
    zoneCmd(IP_master, "setPlayerCmd:mute:1")
    zoneCmd(IP_zone1,  "setPlayerCmd:mute:1")
    zoneCmd(IP_zone2,  "setPlayerCmd:mute:1")
}

def allZonesUnmute() {
    logDebug("Desmuta todas as zonas")
    zoneCmd(IP_master, "setPlayerCmd:mute:0")
    zoneCmd(IP_zone1,  "setPlayerCmd:mute:0")
    zoneCmd(IP_zone2,  "setPlayerCmd:mute:0")
}

// ---------------------------------------------------------------------------
// Child Device Management
// ---------------------------------------------------------------------------

@Field static final String CHILD_DRIVER_NAMESPACE = "TRATO"
@Field static final String CHILD_DRIVER_TYPE      = "SoundSmart MZ21 - Zona"

def createZoneDevices() {
    log.warn "${DRIVER_NAME}: Criando/atualizando dispositivos de zona..."

    [
        [suffix: "-MZ21-MASTER", label: "MZ21 - Master Zone", ip: IP_master],
        [suffix: "-MZ21-ZONE1",  label: "MZ21 - Zona 1",      ip: IP_zone1],
        [suffix: "-MZ21-ZONE2",  label: "MZ21 - Zona 2",      ip: IP_zone2]
    ].each { z ->
        String dni = "${device.id}${z.suffix}"
        def child = getChildDevice(dni)

        if (!child) {
            try {
                child = addChildDevice(
                    CHILD_DRIVER_NAMESPACE,
                    CHILD_DRIVER_TYPE,
                    dni,
                    [name: z.label, label: z.label, isComponent: false]
                )
                log.warn "  ✓ Filho criado: ${z.label} (${dni})"
            } catch (e) {
                log.error "  ✗ Falha ao criar filho '${z.label}' (DNI=${dni}): ${e}", e
                return
            }
        } else {
            log.warn "  ↺ Filho já existe: ${child.displayName}"
        }

        // Atualiza o IP do filho
        if (child && z.ip) {
            try {
                child.updateSetting("IP_address", [value: z.ip, type: "text"])
                child.initialize()
                log.warn "     IP atualizado → ${z.ip}"
            } catch (e) {
                logDebug("Falha ao setar IP no filho ${z.label}: ${e.message}")
            }
        }
    }

    log.warn "${DRIVER_NAME}: Concluído. Filhos ativos: ${childDevices?.size() ?: 0}"
}

def removeZoneDevices() {
    log.warn "${DRIVER_NAME}: Removendo dispositivos de zona..."
    int removed = 0
    (childDevices ?: []).each { cd ->
        try { deleteChildDevice(cd.deviceNetworkId); removed++ }
        catch (e) { log.error "Falha ao remover '${cd.displayName}': ${e}", e }
    }
    log.warn "${DRIVER_NAME}: ${removed} filho(s) removido(s)."
}

private void syncChildIPs() {
    [
        [suffix: "-MZ21-MASTER", ip: IP_master],
        [suffix: "-MZ21-ZONE1",  ip: IP_zone1],
        [suffix: "-MZ21-ZONE2",  ip: IP_zone2]
    ].each { z ->
        def child = getChildDevice("${device.id}${z.suffix}")
        if (child && z.ip) {
            try {
                child.updateSetting("IP_address", [value: z.ip, type: "text"])
            } catch (ignored) {}
        }
    }
}

// ---------------------------------------------------------------------------
// HTTP Helpers
// ---------------------------------------------------------------------------

private String baseURI(String ip) {
    return "http://${ip}/httpapi.asp?command="
}

private void zoneCmd(String ip, String cmd) {
    if (!ip) { logDebug("zoneCmd(): IP nulo, ignorando '${cmd}'"); return }
    String url = (baseURI(ip) + cmd).replaceAll(' ', '%20')
    logDebug("zoneCmd → ${url}")
    try {
        Map params = [uri: url, timeout: 7]
        asynchttpGet('zoneCmdCallback', params, [ip: ip, cmd: cmd])
    } catch (Exception e) {
        logDebug("zoneCmd() erro: ${e.message}")
    }
    runIn(2, "refresh")
}

void zoneCmdCallback(resp, data) {
    Integer st = null
    try { st = resp?.status as Integer } catch (ignored) {}
    String info = "${data?.ip} → ${data?.cmd}"
    if (st && st >= 200 && st < 300) {
        logDebug("CMD OK (${st}): ${info}")
    } else {
        log.warn "${DRIVER_NAME} CMD FAIL (status=${st}): ${info}"
    }
}

private String httpGetZone(String ip, String cmd) {
    String url = (baseURI(ip) + cmd).replaceAll(' ', '%20')
    logDebug("httpGetZone → ${url}")
    try {
        def ret = null
        httpGet(url) { resp ->
            if (resp?.data) ret = resp.data?.toString()
        }
        return ret
    } catch (Exception e) {
        logDebug("httpGetZone() erro: ${e.message}")
        return null
    }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

private Integer clampVolume(Number v) {
    Integer iv = v?.toInteger() ?: 0
    return Math.max(0, Math.min(100, iv))
}

private String safeHexToAscii(Object hexStr) {
    if (!hexStr) return ""
    try {
        return new String(hubitat.helper.HexUtils.hexStringToByteArray(hexStr.toString()))
    } catch (ignored) {
        return ""
    }
}

private def parseJson(String raw) {
    if (!raw) return null
    try {
        return new groovy.json.JsonSlurper().parseText(raw)
    } catch (Exception e) {
        logDebug("parseJson() falhou: ${e.message}")
        return null
    }
}

def logDebug(msg) {
    if (logEnable) log.debug("${DRIVER_NAME}: ${msg}")
}

def logsOff() {
    log.warn "${DRIVER_NAME}: Debug logging desligado automaticamente."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

private void scheduleDebugAutoOff() {
    if (logEnable) {
        log.warn "${DRIVER_NAME}: Debug ativado — desligará em 30 minutos."
        runIn(1800, "logsOff")
    } else {
        unschedule("logsOff")
    }
}
