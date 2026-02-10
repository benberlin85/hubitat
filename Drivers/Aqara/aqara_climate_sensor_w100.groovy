/**
 *  Aqara Climate Sensor W100
 *
 *  Model: lumi.sensor_ht.agl001
 *
 *  A driver for the Aqara Climate Sensor W100 with temperature, humidity,
 *  3 buttons (plus/center/minus), and optional external sensor support.
 *
 *  Version: 1.0.0
 *
 *  Clusters:
 *    0x0000 - Basic
 *    0x0001 - Power Configuration (battery)
 *    0x0003 - Identify
 *    0x0012 - Multistate Input (buttons)
 *    0x0402 - Temperature Measurement
 *    0x0405 - Relative Humidity
 *    0xFCC0 - Aqara Manufacturer Specific
 *
 *  Buttons:
 *    Button 1 - Plus (+) button - Endpoint 01
 *    Button 2 - Center button - Endpoint 02
 *    Button 3 - Minus (-) button - Endpoint 03
 *
 *  Actions: pushed, held, doubleTapped, released
 *
 *  References:
 *    - kkossev's W100 driver: https://github.com/kkossev/Hubitat
 *    - zigbee2mqtt: https://github.com/Koenkk/zigbee2mqtt/issues/27262
 *
 *  Author: Ben Fayershtein
 */

import groovy.transform.Field

metadata {
    definition (name: "Aqara Climate Sensor W100", namespace: "benberlin", author: "Ben Fayershtein") {
        capability "Configuration"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "PushableButton"
        capability "HoldableButton"
        capability "DoubleTapableButton"
        capability "ReleasableButton"
        capability "Sensor"

        // Additional attributes
        attribute "batteryVoltage", "number"
        attribute "externalTemperature", "number"
        attribute "externalHumidity", "number"
        attribute "powerOutageCount", "number"

        // Fingerprints
        fingerprint profileId: "0104", endpointId: "01",
                    inClusters: "0000,0001,0003,0012,0402,0405,FCC0",
                    outClusters: "0019",
                    model: "lumi.sensor_ht.agl001",
                    manufacturer: "Aqara",
                    deviceJoinName: "Aqara Climate Sensor W100"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
        input name: "tempOffset", type: "decimal", title: "Temperature offset", defaultValue: 0, range: "-10..10"
        input name: "humidityOffset", type: "decimal", title: "Humidity offset", defaultValue: 0, range: "-20..20"
    }
}

// ==================== Constants ====================

@Field static final int AQARA_MFG_CODE = 0x115F

// Button action values from cluster 0x0012, attribute 0x0055
@Field static final Map BUTTON_ACTIONS = [
    0: "held",
    1: "pushed",
    2: "doubleTapped",
    255: "released"
]

// Button mapping: endpoint -> button number
@Field static final Map ENDPOINT_TO_BUTTON = [
    "01": 1,  // Plus button
    "02": 2,  // Center button
    "03": 3   // Minus button
]

// ==================== Lifecycle ====================

def installed() {
    log.info "Aqara Climate Sensor W100 installed"
    initialize()
}

def updated() {
    log.info "Settings updated"
    if (logEnable) runIn(1800, "logsOff")
}

def initialize() {
    sendEvent(name: "numberOfButtons", value: 3, descriptionText: "W100 has 3 buttons")
    sendEvent(name: "temperature", value: 0, unit: "°C")
    sendEvent(name: "humidity", value: 0, unit: "%")
}

def configure() {
    logInfo "Configuring W100..."

    def cmds = []

    // Write magic byte to enable third-party hub
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, 0x01, [mfgCode: AQARA_MFG_CODE])
    cmds += "delay 500"

    // Bind clusters
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}"  // Temperature
    cmds += "delay 300"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0405 {${device.zigbeeId}} {}"  // Humidity
    cmds += "delay 300"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0012 {${device.zigbeeId}} {}"  // Multistate (buttons)
    cmds += "delay 300"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0012 {${device.zigbeeId}} {}"  // Button EP2
    cmds += "delay 300"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0012 {${device.zigbeeId}} {}"  // Button EP3
    cmds += "delay 300"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"  // Aqara
    cmds += "delay 500"

    // Configure temperature reporting
    cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 10, 3600, 10)  // min 10s, max 1hr, 0.1°C change
    cmds += "delay 300"

    // Configure humidity reporting
    cmds += zigbee.configureReporting(0x0405, 0x0000, 0x21, 10, 3600, 100)  // min 10s, max 1hr, 1% change
    cmds += "delay 300"

    // Read current state
    cmds += refresh()

    return cmds
}

def refresh() {
    logInfo "Refreshing W100..."

    def cmds = []

    // Basic info
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model
    cmds += "delay 200"

    // Temperature
    cmds += zigbee.readAttribute(0x0402, 0x0000)
    cmds += "delay 200"

    // Humidity
    cmds += zigbee.readAttribute(0x0405, 0x0000)
    cmds += "delay 200"

    // Battery
    cmds += zigbee.readAttribute(0x0001, 0x0020)  // Voltage
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0001, 0x0021)  // Percentage
    cmds += "delay 200"

    // Aqara F7 TLV data
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: AQARA_MFG_CODE])

    return cmds
}

// ==================== Parse ====================

def parse(String description) {
    logDebug "Parsing: ${description?.take(100)}..."

    try {
        def descMap = zigbee.parseDescriptionAsMap(description)

        if (description.startsWith("read attr -")) {
            parseReadAttr(descMap)
        } else if (description.startsWith("catchall:")) {
            parseCatchall(descMap)
        }
    } catch (e) {
        log.error "Parse error: ${e.message}"
    }

    return []
}

private void parseReadAttr(Map descMap) {
    def cluster = descMap.cluster?.toUpperCase()
    def attrId = descMap.attrId?.toUpperCase()
    def value = descMap.value
    def endpoint = descMap.endpoint ?: "01"

    logDebug "Attribute: cluster=0x${cluster}, attr=0x${attrId}, value=${value}, ep=${endpoint}"

    switch (cluster) {
        case "0000":  // Basic
            parseBasicCluster(attrId, value, descMap)
            break
        case "0001":  // Power Configuration
            parsePowerCluster(attrId, value)
            break
        case "0012":  // Multistate Input (buttons)
            parseMultistateCluster(attrId, value, endpoint)
            break
        case "0402":  // Temperature
            parseTemperatureCluster(attrId, value)
            break
        case "0405":  // Humidity
            parseHumidityCluster(attrId, value)
            break
        case "FCC0":  // Aqara
            parseAqaraCluster(attrId, value, descMap)
            break
    }
}

private void parseCatchall(Map descMap) {
    def clusterId = (descMap.clusterId ?: descMap.cluster)?.toUpperCase()
    def command = descMap.command
    def data = descMap.data
    def endpoint = descMap.sourceEndpoint ?: "01"

    logDebug "Catchall: cluster=0x${clusterId}, cmd=${command}, ep=${endpoint}, data=${data}"

    // Handle button reports from multistate cluster
    if (clusterId == "0012" && command == "0A" && data?.size() >= 4) {
        // Attribute report: [attrLow, attrHigh, type, value]
        if (data[0] == "55" && data[1] == "00") {
            def actionValue = Integer.parseInt(data[3], 16)
            handleButtonAction(endpoint, actionValue)
        }
    }

    // Handle Aqara FCC0 reports
    if (clusterId == "FCC0" && command == "0A" && data?.size() >= 4) {
        // Check for F7 attribute
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

// ==================== Cluster Parsers ====================

private void parseBasicCluster(String attrId, String value, Map descMap) {
    def encoding = descMap.encoding

    switch (attrId) {
        case "0004":  // Manufacturer
            def mfg = (encoding == "42") ? value : decodeString(value)
            if (mfg) {
                logInfo "Manufacturer: ${mfg}"
                updateDataValue("manufacturer", mfg)
            }
            break
        case "0005":  // Model
            def model = (encoding == "42") ? value : decodeString(value)
            if (model) {
                logInfo "Model: ${model}"
                updateDataValue("model", model)
            }
            break
    }
}

private void parsePowerCluster(String attrId, String value) {
    switch (attrId) {
        case "0020":  // Battery voltage (in 100mV units)
            def voltage = Integer.parseInt(value, 16)
            def volts = voltage / 10.0
            logInfo "Battery voltage: ${volts}V"
            sendEvent(name: "batteryVoltage", value: volts, unit: "V")
            // Calculate percentage from voltage (3.0V = 100%, 2.7V = 0%)
            def percent = Math.min(100, Math.max(0, ((volts - 2.7) / 0.3 * 100).toInteger()))
            sendEvent(name: "battery", value: percent, unit: "%", descriptionText: "Battery is ${percent}%")
            break
        case "0021":  // Battery percentage
            def percent = Integer.parseInt(value, 16)
            if (percent > 100) percent = percent / 2  // Some devices report 0-200
            logInfo "Battery: ${percent}%"
            sendEvent(name: "battery", value: percent, unit: "%", descriptionText: "Battery is ${percent}%")
            break
    }
}

private void parseMultistateCluster(String attrId, String value, String endpoint) {
    if (attrId == "0055") {
        def actionValue = Integer.parseInt(value, 16)
        handleButtonAction(endpoint, actionValue)
    }
}

private void parseTemperatureCluster(String attrId, String value) {
    if (attrId == "0000") {
        def rawTemp = Integer.parseInt(value, 16)
        // Handle signed value
        if (rawTemp > 32767) rawTemp -= 65536
        def tempC = rawTemp / 100.0
        def offset = settings?.tempOffset ?: 0
        tempC = tempC + offset
        tempC = formatDecimal(tempC, 1)

        logInfo "Temperature: ${tempC}°C"
        sendEvent(name: "temperature", value: tempC, unit: "°C", descriptionText: "Temperature is ${tempC}°C")
    }
}

private void parseHumidityCluster(String attrId, String value) {
    if (attrId == "0000") {
        def rawHumidity = Integer.parseInt(value, 16)
        def humidity = rawHumidity / 100.0
        def offset = settings?.humidityOffset ?: 0
        humidity = humidity + offset
        humidity = formatDecimal(Math.min(100, Math.max(0, humidity)), 1)

        logInfo "Humidity: ${humidity}%"
        sendEvent(name: "humidity", value: humidity, unit: "%", descriptionText: "Humidity is ${humidity}%")
    }
}

private void parseAqaraCluster(String attrId, String value, Map descMap) {
    logDebug "Aqara FCC0 attr 0x${attrId} = ${value}"

    switch (attrId) {
        case "00F7":
        case "F7":
            parseF7TLVData(value)
            break
        default:
            logDebug "Unknown FCC0 attribute 0x${attrId}"
    }
}

// ==================== Button Handling ====================

private void handleButtonAction(String endpoint, int actionValue) {
    def buttonNum = ENDPOINT_TO_BUTTON[endpoint] ?: 1
    def action = BUTTON_ACTIONS[actionValue] ?: "unknown"

    def buttonNames = [1: "Plus (+)", 2: "Center", 3: "Minus (-)"]
    def buttonName = buttonNames[buttonNum] ?: "Button ${buttonNum}"

    logInfo "Button ${buttonNum} (${buttonName}): ${action}"

    switch (action) {
        case "pushed":
            sendEvent(name: "pushed", value: buttonNum, descriptionText: "${buttonName} button pushed", isStateChange: true)
            break
        case "held":
            sendEvent(name: "held", value: buttonNum, descriptionText: "${buttonName} button held", isStateChange: true)
            break
        case "doubleTapped":
            sendEvent(name: "doubleTapped", value: buttonNum, descriptionText: "${buttonName} button double-tapped", isStateChange: true)
            break
        case "released":
            sendEvent(name: "released", value: buttonNum, descriptionText: "${buttonName} button released", isStateChange: true)
            break
    }
}

// ==================== F7 TLV Parser ====================

private void parseF7TLVData(String hexData) {
    if (!hexData || hexData.length() < 4) return

    logDebug "Parsing F7 TLV (${hexData.length() / 2} bytes)..."

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

        switch (tag) {
            case 0x01:  // Battery voltage (mV)
                def volts = value / 1000.0
                logDebug "F7 Battery voltage: ${volts}V"
                sendEvent(name: "batteryVoltage", value: formatDecimal(volts, 2), unit: "V")
                break
            case 0x03:  // Temperature
                logDebug "F7 Temperature: ${value}°C"
                break
            case 0x05:  // Power outage count
                logInfo "F7 Power outage count: ${value}"
                sendEvent(name: "powerOutageCount", value: value)
                break
            case 0x66:  // External temperature (if connected)
                def extTemp = value / 100.0
                logInfo "F7 External temperature: ${extTemp}°C"
                sendEvent(name: "externalTemperature", value: formatDecimal(extTemp, 1), unit: "°C")
                break
            case 0x67:  // External humidity (if connected)
                def extHum = value / 100.0
                logInfo "F7 External humidity: ${extHum}%"
                sendEvent(name: "externalHumidity", value: formatDecimal(extHum, 1), unit: "%")
                break
            case 0x69:  // Battery percentage
                logInfo "F7 Battery: ${value}%"
                sendEvent(name: "battery", value: value, unit: "%", descriptionText: "Battery is ${value}%")
                break
            default:
                logDebug "F7 Tag 0x${String.format('%02X', tag)}: ${value}"
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

// ==================== Utility Functions ====================

private String decodeString(String hex) {
    if (!hex) return ""
    if (hex =~ /[g-zG-Z.]/) return hex  // Already decoded
    def result = ""
    try {
        for (int i = 0; i < hex.length(); i += 2) {
            def charCode = Integer.parseInt(hex.substring(i, Math.min(i + 2, hex.length())), 16)
            if (charCode >= 32 && charCode < 127) result += (char)charCode
        }
    } catch (e) {
        return hex
    }
    return result
}

private BigDecimal formatDecimal(Number value, int places) {
    if (value == 0) return BigDecimal.ZERO
    return new BigDecimal(String.format("%.${places}f", value))
}

private void logDebug(String msg) {
    if (settings?.logEnable) log.debug msg
}

private void logInfo(String msg) {
    if (settings?.txtEnable) log.info msg
}

def logsOff() {
    log.info "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
