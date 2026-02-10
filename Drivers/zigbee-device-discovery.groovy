/**
 *  Zigbee Device Discovery Tool
 *
 *  A diagnostic driver that discovers endpoints, clusters, and attributes
 *  from any Zigbee device to help create custom drivers.
 *
 *  Version: 2.1.1 - Fixed distance 0E+1 formatting
 *
 *  Instructions:
 *  1. Change your device to use this driver
 *  2. Click "Discover All" button
 *  3. Wake up your device (press a button or trigger motion)
 *  4. Check the logs for discovery results
 *
 *  For FP1E Presence Sensor:
 *  - Click "Initialize Aqara" FIRST to wake up the device
 *  - Then wave in front of the sensor during "Discover All"
 *  - If minimal response, try "FP1E Discovery" button
 */

import groovy.transform.Field

metadata {
    definition (name: "Zigbee Device Discovery Tool", namespace: "benberlin", author: "Ben Fayershtein") {
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "PresenceSensor"
        capability "MotionSensor"

        // Status attributes
        attribute "discoveryStatus", "string"
        attribute "lastMessage", "string"
        attribute "deviceModel", "string"
        attribute "deviceManufacturer", "string"
        attribute "messageCount", "number"
        attribute "lastResponse", "string"

        // Main discovery commands
        command "discoverAll"
        command "discoverEndpoints"
        command "readAqaraCluster"
        command "initializeAqara"

        // Test commands
        command "testSwitchOn"
        command "testSwitchOff"
        command "testPresence"

        // Device-specific discovery
        command "discoverFP1E"

        // Utility
        command "clearResults"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "testEndpoint", type: "enum", title: "Test Endpoint",
              options: ["01", "02", "03", "15", "F2"], defaultValue: "01"
        input name: "mfgCode", type: "enum", title: "Manufacturer Code",
              options: [
                  ["115F": "Aqara/Lumi (0x115F)"],
                  ["1037": "Philips (0x1037)"],
                  ["1021": "Legrand (0x1021)"],
                  ["NONE": "None"]
              ], defaultValue: "115F"
    }
}

// ==================== Cluster Names ====================
@Field static final Map CLUSTER_NAMES = [
    "0000": "Basic",
    "0001": "Power Configuration",
    "0003": "Identify",
    "0004": "Groups",
    "0005": "Scenes",
    "0006": "On/Off",
    "0008": "Level Control",
    "000F": "Binary Input",
    "0012": "Multistate Input",
    "0019": "OTA Upgrade",
    "0020": "Poll Control",
    "0101": "Door Lock",
    "0102": "Window Covering",
    "0201": "Thermostat",
    "0300": "Color Control",
    "0400": "Illuminance",
    "0402": "Temperature",
    "0403": "Pressure",
    "0405": "Humidity",
    "0406": "Occupancy",
    "0500": "IAS Zone",
    "0702": "Metering",
    "0B04": "Electrical Measurement",
    "FC11": "Sonoff Custom",
    "FCC0": "Aqara/Lumi Custom"
]

@Field static final Map DATA_TYPES = [
    "10": "Boolean", "20": "Uint8", "21": "Uint16", "23": "Uint32",
    "28": "Int8", "29": "Int16", "30": "Enum8", "39": "Float", "42": "String"
]

// ==================== Lifecycle ====================

def installed() {
    log.info "Zigbee Device Discovery Tool installed"
    sendEvent(name: "discoveryStatus", value: "Ready - Click 'Discover All'")
    sendEvent(name: "messageCount", value: 0)
}

def updated() {
    log.info "Settings updated"
}

def configure() {
    return discoverAll()
}

def refresh() {
    return discoverAll()
}

// ==================== Main Discovery Commands ====================

def clearResults() {
    logHeader("CLEARING RESULTS")
    state.clear()
    sendEvent(name: "discoveryStatus", value: "Cleared")
    sendEvent(name: "messageCount", value: 0)
    sendEvent(name: "lastMessage", value: "")
    sendEvent(name: "deviceModel", value: "")
    sendEvent(name: "deviceManufacturer", value: "")
    sendEvent(name: "lastResponse", value: "")
}

def discoverAll() {
    logHeader("FULL DEVICE DISCOVERY")
    log.info "Please wake up your device NOW (press a button)"

    sendEvent(name: "discoveryStatus", value: "Discovering...")
    sendEvent(name: "messageCount", value: 0)
    state.discoveryResults = [:]

    def cmds = []

    // Basic device info
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0000, 0x0007)  // Power Source
    cmds += "delay 300"

    // Common clusters
    cmds += zigbee.readAttribute(0x0001, 0x0020)  // Battery Voltage
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0001, 0x0021)  // Battery %
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x0000)  // On/Off state
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0402, 0x0000)  // Temperature
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0405, 0x0000)  // Humidity
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0406, 0x0000)  // Occupancy
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0400, 0x0000)  // Illuminance
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0B04, 0x050B)  // Active Power
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0702, 0x0000)  // Energy
    cmds += "delay 300"

    // Aqara/Lumi specific (FCC0)
    def mfg = getMfgCode()
    if (mfg) {
        cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: mfg])  // TLV data
        cmds += "delay 200"
        cmds += zigbee.readAttribute(0xFCC0, 0x0142, [mfgCode: mfg])  // Presence (FP1E)
        cmds += "delay 200"
        cmds += zigbee.readAttribute(0xFCC0, 0x0160, [mfgCode: mfg])  // Movement (FP1E)
        cmds += "delay 200"
        cmds += zigbee.readAttribute(0xFCC0, 0x015F, [mfgCode: mfg])  // Distance (FP1E)
        cmds += "delay 300"
    }

    // Get endpoint list
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    cmds += "delay 500"

    // Get cluster list for common endpoints
    ["01", "02", "15"].each { ep ->
        cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} ${ep}} {0x0000}"
        cmds += "delay 300"
    }

    return cmds
}

def discoverEndpoints() {
    logHeader("DISCOVERING ENDPOINTS & CLUSTERS")
    sendEvent(name: "discoveryStatus", value: "Discovering endpoints...")

    def cmds = []

    // Active endpoints request
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    cmds += "delay 500"

    // Simple descriptor for each endpoint
    ["01", "02", "03", "15", "F2"].each { ep ->
        cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} ${ep}} {0x0000}"
        cmds += "delay 300"
    }

    return cmds
}

def readAqaraCluster() {
    logHeader("READING AQARA FCC0 CLUSTER")

    def mfg = getMfgCode()
    if (!mfg) {
        log.warn "No manufacturer code set - using Aqara default"
        mfg = "0x115F"
    }

    def cmds = []

    // Common Aqara attributes
    def aqaraAttrs = [
        0x00F7: "TLV Data",
        0x0009: "Device Mode",
        0x0142: "Presence",
        0x015B: "Detection Range",
        0x015F: "Target Distance",
        0x0160: "Movement State",
        0x0200: "Operation Mode",
        0x0201: "Power Outage Memory",
        0x0202: "Startup On/Off",
        0x0203: "LED Indicator",
        0x020B: "Overload Protection"
    ]

    aqaraAttrs.each { attr, name ->
        log.info "Reading FCC0:0x${String.format('%04X', attr)} (${name})..."
        cmds += zigbee.readAttribute(0xFCC0, attr, [mfgCode: mfg])
        cmds += "delay 300"
    }

    return cmds
}

def initializeAqara() {
    logHeader("INITIALIZING AQARA DEVICE FOR THIRD-PARTY HUB")
    log.warn "!!! WAKE UP THE DEVICE NOW - press button or trigger motion !!!"

    def mfg = getMfgCode() ?: "0x115F"
    def cmds = []

    // Magic byte to enable device - send multiple times with delays
    log.info "1. Writing magic byte to FCC0:0x0009 (multiple attempts)..."
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, 0x01, [mfgCode: mfg])
    cmds += "delay 500"
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, 0x01, [mfgCode: mfg])
    cmds += "delay 500"

    // Bind FCC0 cluster FIRST (most important for Aqara)
    log.info "2. Binding FCC0 (Aqara) cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind On/Off cluster
    log.info "3. Binding On/Off cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    cmds += "delay 300"

    // Bind Occupancy cluster (for presence sensors)
    log.info "4. Binding Occupancy cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
    cmds += "delay 300"

    // Configure reporting
    log.info "5. Configuring reporting..."
    cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 3600, null)
    cmds += "delay 300"
    cmds += zigbee.configureReporting(0xFCC0, 0x0142, 0x20, 0, 3600, 1, [mfgCode: mfg])  // Presence
    cmds += "delay 300"

    // Read back state
    log.info "6. Reading device state..."
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x0000)  // On/Off
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0xFCC0, 0x0142, [mfgCode: mfg])  // Presence
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: mfg])  // F7 TLV

    return cmds
}

def discoverFP1E() {
    logHeader("FP1E PRESENCE SENSOR DISCOVERY")
    log.warn "!!! WAVE YOUR HAND IN FRONT OF THE SENSOR NOW !!!"

    def mfg = "0x115F"  // Aqara manufacturer code
    def cmds = []

    // Step 1: Write magic byte to enable third-party hub mode
    log.info "Step 1: Enabling third-party hub mode..."
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, 0x01, [mfgCode: mfg])
    cmds += "delay 1000"

    // Step 2: Bind FCC0 cluster for reports
    log.info "Step 2: Binding Aqara cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Step 3: Read basic device info
    log.info "Step 3: Reading device info..."
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model
    cmds += "delay 500"

    // Step 4: Read all FP1E-specific attributes
    log.info "Step 4: Reading FP1E attributes..."

    // Presence (0x0142)
    cmds += zigbee.readAttribute(0xFCC0, 0x0142, [mfgCode: mfg])
    cmds += "delay 300"

    // Motion sensitivity (0x010C)
    cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: mfg])
    cmds += "delay 300"

    // Approach distance (0x0144)
    cmds += zigbee.readAttribute(0xFCC0, 0x0144, [mfgCode: mfg])
    cmds += "delay 300"

    // Exit entrance state (0x0146)
    cmds += zigbee.readAttribute(0xFCC0, 0x0146, [mfgCode: mfg])
    cmds += "delay 300"

    // Monitoring mode (0x0148)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: mfg])
    cmds += "delay 300"

    // Target distance (0x015F)
    cmds += zigbee.readAttribute(0xFCC0, 0x015F, [mfgCode: mfg])
    cmds += "delay 300"

    // Movement state (0x0160)
    cmds += zigbee.readAttribute(0xFCC0, 0x0160, [mfgCode: mfg])
    cmds += "delay 300"

    // Detection range/distance (0x015B)
    cmds += zigbee.readAttribute(0xFCC0, 0x015B, [mfgCode: mfg])
    cmds += "delay 300"

    // F7 TLV data
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: mfg])
    cmds += "delay 500"

    // Step 5: Configure reporting for presence changes
    log.info "Step 5: Configuring presence reporting..."
    cmds += zigbee.configureReporting(0xFCC0, 0x0142, 0x20, 0, 3600, 1, [mfgCode: mfg])
    cmds += "delay 300"
    cmds += zigbee.configureReporting(0xFCC0, 0x0160, 0x20, 0, 3600, 1, [mfgCode: mfg])
    cmds += "delay 500"

    // Step 6: Get endpoint info
    log.info "Step 6: Getting endpoint list..."
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    cmds += "delay 500"
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"

    log.warn "Waiting for device responses... Keep moving in front of sensor!"

    return cmds
}

// ==================== Test Commands ====================

def on() { testSwitchOn() }
def off() { testSwitchOff() }

def testSwitchOn() {
    def ep = settings?.testEndpoint ?: "01"
    logHeader("TEST SWITCH ON - Endpoint ${ep}")

    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x01 {}"
    cmds += "delay 500"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x0000 {}"

    return cmds
}

def testSwitchOff() {
    def ep = settings?.testEndpoint ?: "01"
    logHeader("TEST SWITCH OFF - Endpoint ${ep}")

    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x00 {}"
    cmds += "delay 500"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x0000 {}"

    return cmds
}

def testPresence() {
    def ep = settings?.testEndpoint ?: "01"
    logHeader("READING PRESENCE SENSOR ATTRIBUTES")

    def mfg = getMfgCode()
    def cmds = []

    // Standard occupancy cluster
    cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0406 0x0000 {}"
    cmds += "delay 300"

    // Aqara FP1E specific
    if (mfg) {
        // Presence (0x0142)
        cmds += zigbee.readAttribute(0xFCC0, 0x0142, [mfgCode: mfg])
        cmds += "delay 200"
        // Movement (0x0160)
        cmds += zigbee.readAttribute(0xFCC0, 0x0160, [mfgCode: mfg])
        cmds += "delay 200"
        // Distance (0x015F)
        cmds += zigbee.readAttribute(0xFCC0, 0x015F, [mfgCode: mfg])
        cmds += "delay 200"
        // F7 TLV
        cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: mfg])
    }

    return cmds
}

// ==================== Helper Functions ====================

private String getMfgCode() {
    def code = settings?.mfgCode ?: "115F"
    if (code == "NONE") return null
    return "0x${code}"
}

private void logHeader(String title) {
    log.info "=" * 60
    log.info title
    log.info "=" * 60
}

// ==================== Parse ====================

def parse(String description) {
    def count = (device.currentValue("messageCount") ?: 0) + 1
    sendEvent(name: "messageCount", value: count)
    sendEvent(name: "lastMessage", value: new Date().format("HH:mm:ss"))

    logHeader("MESSAGE #${count}")
    log.info "Raw: ${description?.take(120)}..."

    try {
        def descMap = zigbee.parseDescriptionAsMap(description)

        if (description.startsWith("read attr -")) {
            parseReadAttr(descMap)
        } else if (description.startsWith("catchall:")) {
            parseCatchall(descMap)
        } else {
            parseGeneric(descMap)
        }
    } catch (e) {
        log.error "Parse error: ${e.message}"
    }

    sendEvent(name: "discoveryStatus", value: "Last msg: ${new Date().format('HH:mm:ss')}")
    return []
}

private void parseReadAttr(Map descMap) {
    def cluster = descMap.cluster?.toUpperCase()
    def attrId = descMap.attrId?.toUpperCase()
    def value = descMap.value
    def encoding = descMap.encoding
    def endpoint = descMap.endpoint ?: "01"

    def clusterName = CLUSTER_NAMES[cluster] ?: "Unknown"
    def dataType = DATA_TYPES[encoding] ?: encoding

    log.info "-" * 50
    log.info "ATTRIBUTE: Cluster 0x${cluster} (${clusterName})"
    log.info "  Endpoint: ${endpoint}"
    log.info "  Attr: 0x${attrId}"
    log.info "  Type: ${dataType}"
    log.info "  Value: ${value}"

    // Decode common values
    def decoded = decodeValue(cluster, attrId, value, encoding)
    if (decoded) log.info "  Decoded: ${decoded}"

    // Special handling
    handleSpecialAttributes(cluster, attrId, value, endpoint, descMap)

    // Store results
    storeResult(cluster, attrId, value, decoded, endpoint)
    log.info "-" * 50
}

private void parseCatchall(Map descMap) {
    def clusterId = (descMap.clusterId ?: descMap.cluster)?.toUpperCase()
    def clusterName = CLUSTER_NAMES[clusterId] ?: "Unknown"
    def command = descMap.command
    def data = descMap.data
    def sourceEp = descMap.sourceEndpoint

    log.info "-" * 50
    log.info "CATCHALL: Cluster 0x${clusterId} (${clusterName})"
    log.info "  Command: ${command}"
    log.info "  Source EP: ${sourceEp}"
    log.info "  Data: ${data}"

    // Handle specific clusters
    switch (clusterId) {
        case "0006":  // On/Off
            handleOnOffCatchall(descMap)
            break
        case "FCC0":  // Aqara
            handleAqaraCatchall(descMap)
            break
        case "8004":  // Simple Descriptor Response
            parseSimpleDescriptor(data)
            break
        case "8005":  // Active Endpoints Response
            parseActiveEndpoints(data)
            break
    }

    log.info "-" * 50
}

private void parseGeneric(Map descMap) {
    def cluster = descMap.cluster ?: descMap.clusterId
    log.info "Generic message - Cluster: ${cluster}, Data: ${descMap.data}"

    if (cluster?.toUpperCase() == "0006") {
        handleOnOffCatchall(descMap)
    }
}

// ==================== Special Handlers ====================

private void handleSpecialAttributes(String cluster, String attrId, String value, String endpoint, Map descMap) {
    switch (cluster) {
        case "0006":  // On/Off
            if (attrId == "0000") {
                def state = (value == "01") ? "on" : "off"
                log.warn "*** SWITCH STATE: ${state.toUpperCase()} (EP ${endpoint}) ***"
                sendEvent(name: "switch", value: state)
                sendEvent(name: "lastResponse", value: "Switch: ${state} (EP${endpoint})")
            }
            break

        case "0000":  // Basic
            if (attrId == "0004") sendEvent(name: "deviceManufacturer", value: decodeString(value))
            if (attrId == "0005") sendEvent(name: "deviceModel", value: decodeString(value))
            break

        case "0406":  // Occupancy
            if (attrId == "0000") {
                def occupied = (value != "00")
                log.warn "*** OCCUPANCY: ${occupied ? 'OCCUPIED' : 'UNOCCUPIED'} ***"
                sendEvent(name: "motion", value: occupied ? "active" : "inactive")
                sendEvent(name: "presence", value: occupied ? "present" : "not present")
            }
            break

        case "FCC0":  // Aqara
            handleAqaraAttribute(attrId, value, descMap)
            break
    }
}

private void handleAqaraAttribute(String attrId, String value, Map descMap) {
    log.warn "*** AQARA FCC0 ATTRIBUTE 0x${attrId} ***"

    switch (attrId) {
        case "00F7":
        case "F7":
            if (value) parseF7TLVData(value)
            break

        case "0142":  // Presence (FP1E)
            def present = (value != "00")
            log.warn "*** PRESENCE: ${present ? 'DETECTED' : 'CLEAR'} ***"
            sendEvent(name: "presence", value: present ? "present" : "not present")
            sendEvent(name: "motion", value: present ? "active" : "inactive")
            break

        case "0160":  // Movement state (FP1E)
            def states = [0: "unknown", 2: "idle", 3: "large movement", 4: "small movement"]
            def stateVal = Integer.parseInt(value, 16)
            def stateName = states[stateVal] ?: "unknown(${stateVal})"
            log.warn "*** MOVEMENT: ${stateName} ***"
            sendEvent(name: "lastResponse", value: "Movement: ${stateName}")
            break

        case "015F":  // Target distance (FP1E)
            def distCm = Integer.parseInt(value, 16)
            def distM = (distCm == 0) ? 0 : new BigDecimal(String.format("%.2f", distCm / 100.0))
            log.warn "*** DISTANCE: ${distM}m (${distCm}cm) ***"
            sendEvent(name: "lastResponse", value: "Distance: ${distM}m")
            break

        case "010C":  // Motion sensitivity (FP1E)
            def sensLevels = [1: "low", 2: "medium", 3: "high"]
            def sensVal = Integer.parseInt(value, 16)
            log.warn "*** MOTION SENSITIVITY: ${sensLevels[sensVal] ?: sensVal} ***"
            break

        case "0144":  // Approach distance (FP1E)
            def distVal = Integer.parseInt(value, 16)
            log.warn "*** APPROACH DISTANCE: ${distVal}cm ***"
            break

        case "0146":  // Exit/Entrance state (FP1E)
            def stateMap = [0: "far away", 1: "away", 2: "approaching", 3: "enter", 4: "left enter", 5: "right enter"]
            def stVal = Integer.parseInt(value, 16)
            log.warn "*** EXIT/ENTRANCE: ${stateMap[stVal] ?: stVal} ***"
            break

        case "0148":  // Monitoring mode (FP1E)
            def modeMap = [0: "undirected", 1: "directed left", 2: "directed right"]
            def modeVal = Integer.parseInt(value, 16)
            log.warn "*** MONITORING MODE: ${modeMap[modeVal] ?: modeVal} ***"
            break

        case "015B":  // Detection range (FP1E)
            def rangeVal = Integer.parseInt(value, 16)
            log.warn "*** DETECTION RANGE: ${rangeVal}cm ***"
            break

        case "0009":  // Device mode
            log.warn "*** DEVICE MODE: ${value} ***"
            break

        default:
            log.info "FCC0 Attr 0x${attrId} = ${value}"
    }
}

private void handleOnOffCatchall(Map descMap) {
    def command = descMap.command
    def data = descMap.data
    def ep = descMap.sourceEndpoint

    log.warn "*** ON/OFF CATCHALL - CMD: ${command}, EP: ${ep} ***"

    if (command == "0B" && data?.size() > 1) {
        def status = data[1]
        log.warn "*** COMMAND ACK: ${status == '00' ? 'SUCCESS' : 'FAILED'} ***"
        sendEvent(name: "lastResponse", value: "ACK EP${ep}: ${status}")
    } else if (command == "0A" && data?.size() > 2) {
        def state = (data[2] == "01") ? "on" : "off"
        log.warn "*** SWITCH REPORT: ${state.toUpperCase()} ***"
        sendEvent(name: "switch", value: state)
    }
}

private void handleAqaraCatchall(Map descMap) {
    log.warn "*** AQARA FCC0 CATCHALL ***"
    def data = descMap.data

    if (descMap.command == "0A" && data?.size() >= 4) {
        // Check for F7 attribute report
        if (data[0] == "F7" && data[1] == "00") {
            def dataType = data[2]
            if (dataType == "41" && data.size() > 4) {
                def len = Integer.parseInt(data[3], 16)
                if (data.size() >= 4 + len) {
                    def hexValue = data[4..<(4 + len)].join("")
                    parseF7TLVData(hexValue)
                }
            }
        }
    }
}

// ==================== F7 TLV Parser ====================

private void parseF7TLVData(String hexData) {
    log.info "Parsing F7 TLV (${hexData.length() / 2} bytes)..."

    def bytes = []
    for (int i = 0; i < hexData.length(); i += 2) {
        bytes << Integer.parseInt(hexData.substring(i, i + 2), 16)
    }

    int idx = 0
    while (idx < bytes.size() - 2) {
        def tag = bytes[idx]
        def type = bytes[idx + 1]
        idx += 2

        def valueLen = getTypeLength(type)
        if (valueLen == 0 || idx + valueLen > bytes.size()) break

        def rawBytes = bytes[idx..<(idx + valueLen)]
        idx += valueLen

        def value = interpretValue(type, rawBytes)
        def tagHex = String.format('%02X', tag)

        // Log known tags
        switch (tag) {
            case 0x03:
                log.warn "*** F7 TEMPERATURE: ${value}°C ***"
                break
            case 0x05:
                log.warn "*** F7 POWER OUTAGE COUNT: ${value} ***"
                break
            case 0x64:
                log.warn "*** F7 SWITCH: ${value ? 'ON' : 'OFF'} ***"
                sendEvent(name: "switch", value: value ? "on" : "off")
                break
            case 0x95:
                log.warn "*** F7 ENERGY: ${value} Wh ***"
                break
            case 0x96:
                def volts = (value instanceof Number) ? value / 10.0 : value
                log.warn "*** F7 VOLTAGE: ${volts}V ***"
                break
            case 0x97:
                log.warn "*** F7 CURRENT: ${value}mA ***"
                break
            case 0x98:
                log.warn "*** F7 POWER: ${value}W ***"
                break
            case 0x66:  // FP1E presence from F7
                log.warn "*** F7 PRESENCE: ${value ? 'DETECTED' : 'CLEAR'} ***"
                sendEvent(name: "presence", value: value ? "present" : "not present")
                sendEvent(name: "motion", value: value ? "active" : "inactive")
                break
            default:
                log.info "  Tag 0x${tagHex}: ${value}"
        }
    }
}

private int getTypeLength(int type) {
    switch (type) {
        case 0x10: return 1  // Boolean
        case 0x20: return 1  // Uint8
        case 0x21: return 2  // Uint16
        case 0x23: return 4  // Uint32
        case 0x28: return 1  // Int8
        case 0x29: return 2  // Int16
        case 0x39: return 4  // Float
        default: return 0
    }
}

private Object interpretValue(int type, List<Integer> bytes) {
    switch (type) {
        case 0x10: return bytes[0] != 0
        case 0x20: return bytes[0]
        case 0x21: return bytes[0] + (bytes[1] << 8)
        case 0x23: return bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24)
        case 0x28:
            def v = bytes[0]
            return v > 127 ? v - 256 : v
        case 0x29:
            def v = bytes[0] + (bytes[1] << 8)
            return v > 32767 ? v - 65536 : v
        case 0x39:
            int bits = bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24)
            return Float.intBitsToFloat(bits)
        default:
            return bytes.collect { String.format('%02X', it) }.join('')
    }
}

// ==================== Descriptor Parsers ====================

private void parseSimpleDescriptor(List data) {
    if (!data || data.size() < 5 || data[0] != "00") return

    def endpoint = data[4]
    log.info "=" * 50
    log.info "SIMPLE DESCRIPTOR - Endpoint ${endpoint}"

    if (data.size() > 10) {
        def profileId = data[6] + data[5]
        def deviceId = data[8] + data[7]
        log.info "  Profile: 0x${profileId}, Device: 0x${deviceId}"

        def inCount = Integer.parseInt(data[10], 16)
        def idx = 11
        def inClusters = []

        log.info "  Input Clusters (${inCount}):"
        for (int i = 0; i < inCount && idx + 1 < data.size(); i++) {
            def cid = (data[idx + 1] + data[idx]).toUpperCase()
            inClusters << cid
            log.info "    - 0x${cid} (${CLUSTER_NAMES[cid] ?: 'Unknown'})"
            idx += 2
        }

        if (idx < data.size()) {
            def outCount = Integer.parseInt(data[idx], 16)
            idx++
            def outClusters = []

            log.info "  Output Clusters (${outCount}):"
            for (int i = 0; i < outCount && idx + 1 < data.size(); i++) {
                def cid = (data[idx + 1] + data[idx]).toUpperCase()
                outClusters << cid
                log.info "    - 0x${cid} (${CLUSTER_NAMES[cid] ?: 'Unknown'})"
                idx += 2
            }

            // Generate fingerprint
            log.info "=" * 50
            log.info "FINGERPRINT:"
            log.info "fingerprint profileId:\"${profileId}\", endpointId:\"${endpoint}\", " +
                     "inClusters:\"${inClusters.join(',')}\", outClusters:\"${outClusters.join(',')}\""
        }
    }
    log.info "=" * 50
}

private void parseActiveEndpoints(List data) {
    if (!data || data.size() < 4 || data[0] != "00") return

    def count = Integer.parseInt(data[3], 16)
    log.info "=" * 50
    log.info "ACTIVE ENDPOINTS (${count}):"

    for (int i = 0; i < count && (4 + i) < data.size(); i++) {
        log.info "  - Endpoint ${data[4 + i]}"
    }
    log.info "=" * 50
}

// ==================== Utility Functions ====================

private String decodeString(String hex) {
    if (!hex) return ""
    def result = ""
    for (int i = 0; i < hex.length(); i += 2) {
        def charCode = Integer.parseInt(hex.substring(i, Math.min(i + 2, hex.length())), 16)
        if (charCode >= 32 && charCode < 127) result += (char)charCode
    }
    return result
}

private String decodeValue(String cluster, String attrId, String value, String encoding) {
    if (!value) return null

    try {
        switch (encoding) {
            case "42": return decodeString(value)
            case "20": case "21": case "23": return Integer.parseInt(value, 16).toString()
            case "29":
                def v = Integer.parseInt(value, 16)
                if (v > 32767) v -= 65536
                if (cluster in ["0201", "0402"]) return "${v / 100.0}°C"
                return v.toString()
            case "10": return value == "01" ? "true" : "false"
            default: return null
        }
    } catch (e) {
        return null
    }
}

private void storeResult(String cluster, String attrId, String value, String decoded, String endpoint) {
    if (!state.discoveryResults) state.discoveryResults = [:]
    if (!state.discoveryResults[cluster]) state.discoveryResults[cluster] = [:]
    state.discoveryResults[cluster][attrId] = [
        value: value,
        decoded: decoded,
        endpoint: endpoint
    ]
}
