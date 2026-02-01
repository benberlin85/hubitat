/**
 *  Zigbee Device Discovery Tool
 *
 *  A diagnostic driver that discovers all endpoints, clusters, and attributes
 *  from any Zigbee device to help create custom drivers.
 *
 *  Version: 1.0.0
 *
 *  Instructions:
 *  1. Change your device to use this driver
 *  2. Click "Discover Device" button
 *  3. Wake up your device (press a button on it)
 *  4. Wait and check the logs
 *  5. Copy the discovery results to create a proper driver
 */

metadata {
    definition (name: "Zigbee Device Discovery Tool", namespace: "custom", author: "Custom") {
        capability "Configuration"
        capability "Refresh"

        attribute "discoveryStatus", "string"
        attribute "lastMessage", "string"
        attribute "deviceModel", "string"
        attribute "deviceManufacturer", "string"
        attribute "messageCount", "number"

        command "discoverDevice"
        command "discoverBasicInfo"
        command "discoverEndpoints"
        command "discoverClusters"
        command "readThermostatCluster"
        command "readPowerCluster"
        command "readAllCommonClusters"
        command "clearResults"
        command "testCommunication"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true
    }
}

// Known cluster names for readability
@groovy.transform.Field static final Map CLUSTER_NAMES = [
    "0000": "Basic",
    "0001": "Power Configuration",
    "0002": "Device Temperature",
    "0003": "Identify",
    "0004": "Groups",
    "0005": "Scenes",
    "0006": "On/Off",
    "0008": "Level Control",
    "0009": "Alarms",
    "000A": "Time",
    "000F": "Binary Input",
    "0010": "Binary Output",
    "0012": "Multistate Input",
    "0019": "OTA Upgrade",
    "0020": "Poll Control",
    "0100": "Shade Configuration",
    "0101": "Door Lock",
    "0102": "Window Covering",
    "0201": "Thermostat",
    "0202": "Fan Control",
    "0204": "Thermostat UI Config",
    "0300": "Color Control",
    "0400": "Illuminance Measurement",
    "0402": "Temperature Measurement",
    "0403": "Pressure Measurement",
    "0405": "Humidity Measurement",
    "0406": "Occupancy Sensing",
    "0500": "IAS Zone",
    "0702": "Metering",
    "0B04": "Electrical Measurement",
    "FC11": "Sonoff Custom",
    "FC57": "Sonoff Custom 2",
    "FCC0": "Xiaomi/Aqara Custom"
]

@groovy.transform.Field static final Map DATA_TYPES = [
    "00": "No Data",
    "08": "8-bit Data",
    "10": "Boolean",
    "18": "8-bit Bitmap",
    "19": "16-bit Bitmap",
    "20": "Uint8",
    "21": "Uint16",
    "22": "Uint24",
    "23": "Uint32",
    "28": "Int8",
    "29": "Int16",
    "2A": "Int24",
    "2B": "Int32",
    "30": "Enum8",
    "31": "Enum16",
    "39": "Float",
    "42": "String",
    "43": "Long String"
]

def installed() {
    log.info "Zigbee Device Discovery Tool installed"
    sendEvent(name: "discoveryStatus", value: "Ready")
    sendEvent(name: "messageCount", value: 0)
}

def updated() {
    log.info "Updated"
}

def configure() {
    log.info "Configure - use discoverDevice instead"
    return discoverBasicInfo()
}

def refresh() {
    log.info "Refresh"
    return discoverBasicInfo()
}

// ==================== Discovery Commands ====================

def clearResults() {
    log.info "=".multiply(60)
    log.info "CLEARING DISCOVERY RESULTS"
    log.info "=".multiply(60)
    state.clear()
    sendEvent(name: "discoveryStatus", value: "Cleared")
    sendEvent(name: "messageCount", value: 0)
    sendEvent(name: "lastMessage", value: "")
    sendEvent(name: "deviceModel", value: "")
    sendEvent(name: "deviceManufacturer", value: "")
}

def testCommunication() {
    log.info "=".multiply(60)
    log.info "TESTING BASIC COMMUNICATION"
    log.info "=".multiply(60)
    log.info "Sending simple read request to Basic cluster..."
    log.info "If you see a Parse message after this, communication works!"

    sendEvent(name: "discoveryStatus", value: "Testing...")

    return zigbee.readAttribute(0x0000, 0x0000)  // ZCL Version
}

def discoverDevice() {
    log.info "=".multiply(60)
    log.info "STARTING FULL DEVICE DISCOVERY"
    log.info "=".multiply(60)
    log.info "Please wake up your device NOW (press a button on it)"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Discovering...")
    sendEvent(name: "messageCount", value: 0)
    state.discoveryResults = [:]

    def cmds = []

    // Basic cluster - device info
    cmds += zigbee.readAttribute(0x0000, 0x0000)  // ZCL Version
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0001)  // Application Version
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0002)  // Stack Version
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0003)  // HW Version
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer Name
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model Identifier
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0006)  // Date Code
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0007)  // Power Source
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x4000) // SW Build ID
    cmds += "delay 500"

    // Power cluster
    cmds += zigbee.readAttribute(0x0001, 0x0020)  // Battery Voltage
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0001, 0x0021)  // Battery %
    cmds += "delay 500"

    // Try common clusters to see what exists
    cmds += zigbee.readAttribute(0x0006, 0x0000)  // On/Off state
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0008, 0x0000)  // Level
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0000)  // Thermostat - Local Temp
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0402, 0x0000)  // Temperature Measurement
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0405, 0x0000)  // Humidity
    cmds += "delay 500"

    // Request simple descriptor to get cluster list
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"

    return cmds
}

def discoverBasicInfo() {
    log.info "=".multiply(60)
    log.info "READING BASIC DEVICE INFO"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Reading basic info...")

    def cmds = []
    cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0000, 0x0007)  // Power Source

    return cmds
}

def discoverEndpoints() {
    log.info "=".multiply(60)
    log.info "DISCOVERING ENDPOINTS"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Discovering endpoints...")

    // Active endpoints request
    return ["he raw 0x${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"]
}

def discoverClusters() {
    log.info "=".multiply(60)
    log.info "DISCOVERING CLUSTERS ON ENDPOINT 1"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Discovering clusters...")

    // Simple descriptor request for endpoint 1
    return ["he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} 01} {0x0000}"]
}

def readThermostatCluster() {
    log.info "=".multiply(60)
    log.info "READING THERMOSTAT CLUSTER (0x0201)"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Reading thermostat...")

    def cmds = []

    // Standard thermostat attributes
    cmds += zigbee.readAttribute(0x0201, 0x0000)  // Local Temperature
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0003)  // Abs Min Heat Setpoint
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0004)  // Abs Max Heat Setpoint
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0010)  // Local Temp Calibration
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0011)  // Occupied Cooling Setpoint
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0012)  // Occupied Heating Setpoint
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0015)  // Min Heat Setpoint Limit
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0016)  // Max Heat Setpoint Limit
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x001B)  // Control Sequence
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x001C)  // System Mode
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x001E)  // Running Mode
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0201, 0x0029)  // Running State

    return cmds
}

def readPowerCluster() {
    log.info "=".multiply(60)
    log.info "READING POWER CLUSTER (0x0001)"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Reading power...")

    def cmds = []
    cmds += zigbee.readAttribute(0x0001, 0x0020)  // Battery Voltage
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0001, 0x0021)  // Battery Percentage
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0001, 0x0031)  // Battery Size
    cmds += "delay 300"
    cmds += zigbee.readAttribute(0x0001, 0x0033)  // Battery Quantity

    return cmds
}

def readAllCommonClusters() {
    log.info "=".multiply(60)
    log.info "READING ALL COMMON CLUSTERS"
    log.info "=".multiply(60)

    sendEvent(name: "discoveryStatus", value: "Reading all clusters...")

    def cmds = []

    // On/Off
    cmds += zigbee.readAttribute(0x0006, 0x0000)
    cmds += "delay 200"

    // Level
    cmds += zigbee.readAttribute(0x0008, 0x0000)
    cmds += "delay 200"

    // Thermostat
    cmds += zigbee.readAttribute(0x0201, 0x0000)
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0201, 0x0012)
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0201, 0x001C)
    cmds += "delay 200"

    // Temperature
    cmds += zigbee.readAttribute(0x0402, 0x0000)
    cmds += "delay 200"

    // Humidity
    cmds += zigbee.readAttribute(0x0405, 0x0000)
    cmds += "delay 200"

    // Pressure
    cmds += zigbee.readAttribute(0x0403, 0x0000)
    cmds += "delay 200"

    // Occupancy
    cmds += zigbee.readAttribute(0x0406, 0x0000)
    cmds += "delay 200"

    // Illuminance
    cmds += zigbee.readAttribute(0x0400, 0x0000)
    cmds += "delay 200"

    // Electrical Measurement
    cmds += zigbee.readAttribute(0x0B04, 0x050B)  // Active Power
    cmds += "delay 200"

    // Metering
    cmds += zigbee.readAttribute(0x0702, 0x0000)  // Current Summation

    return cmds
}

// ==================== Parse ====================

def parse(String description) {
    // Count messages
    def count = (device.currentValue("messageCount") ?: 0) + 1
    sendEvent(name: "messageCount", value: count)
    sendEvent(name: "lastMessage", value: new Date().format("HH:mm:ss"))

    log.info "=".multiply(60)
    log.info "MESSAGE RECEIVED (#${count})"
    log.info "=".multiply(60)
    log.info "Raw: ${description}"

    def result = []

    try {
        if (description.startsWith("read attr -")) {
            result = parseReadAttr(description)
        }
        else if (description.startsWith("catchall:")) {
            result = parseCatchall(description)
        }
        else {
            log.info "Other message type: ${description}"
        }
    } catch (e) {
        log.error "Parse error: ${e.message}"
    }

    sendEvent(name: "discoveryStatus", value: "Receiving data...")

    return result
}

private List parseReadAttr(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)

    def cluster = descMap.cluster
    def attrId = descMap.attrId
    def value = descMap.value
    def encoding = descMap.encoding

    def clusterName = CLUSTER_NAMES[cluster] ?: "Unknown (${cluster})"
    def dataType = DATA_TYPES[encoding] ?: encoding

    log.info "-".multiply(50)
    log.info "ATTRIBUTE READ RESPONSE"
    log.info "-".multiply(50)
    log.info "Cluster: 0x${cluster} (${clusterName})"
    log.info "Attribute: 0x${attrId}"
    log.info "Data Type: ${dataType}"
    log.info "Raw Value: ${value}"

    // Decode value based on cluster and attribute
    def decodedValue = decodeValue(cluster, attrId, value, encoding)
    if (decodedValue != null) {
        log.info "Decoded Value: ${decodedValue}"
    }

    log.info "-".multiply(50)

    // Store in state
    if (!state.discoveryResults) state.discoveryResults = [:]
    if (!state.discoveryResults[cluster]) state.discoveryResults[cluster] = [:]
    state.discoveryResults[cluster][attrId] = [
        value: value,
        decoded: decodedValue,
        encoding: encoding
    ]

    // Update device info attributes
    if (cluster == "0000") {
        if (attrId == "0004") {
            sendEvent(name: "deviceManufacturer", value: decodedValue ?: value)
        } else if (attrId == "0005") {
            sendEvent(name: "deviceModel", value: decodedValue ?: value)
        }
    }

    return []
}

private List parseCatchall(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)

    log.info "-".multiply(50)
    log.info "CATCHALL MESSAGE"
    log.info "-".multiply(50)
    log.info "Profile: ${descMap.profileId}"
    log.info "Cluster: ${descMap.clusterId} (${CLUSTER_NAMES[descMap.clusterId] ?: 'Unknown'})"
    log.info "Command: ${descMap.command}"
    log.info "Source Endpoint: ${descMap.sourceEndpoint}"
    log.info "Dest Endpoint: ${descMap.destinationEndpoint}"
    log.info "Data: ${descMap.data}"

    // Parse simple descriptor response
    if (descMap.clusterId == "8004" && descMap.data) {
        parseSimpleDescriptor(descMap.data)
    }

    // Parse active endpoints response
    if (descMap.clusterId == "8005" && descMap.data) {
        parseActiveEndpoints(descMap.data)
    }

    // Parse report attributes
    if (descMap.command == "0A" && descMap.data) {
        parseReportAttributes(descMap.clusterId, descMap.data)
    }

    log.info "-".multiply(50)

    return []
}

private void parseSimpleDescriptor(List data) {
    log.info "=".multiply(50)
    log.info "SIMPLE DESCRIPTOR (Cluster List)"
    log.info "=".multiply(50)

    if (data.size() < 5) {
        log.warn "Invalid simple descriptor data"
        return
    }

    def status = data[0]
    if (status != "00") {
        log.warn "Simple descriptor request failed with status: ${status}"
        return
    }

    def endpoint = data[4]
    log.info "Endpoint: ${endpoint}"

    if (data.size() > 5) {
        def profileId = data[6] + data[5]
        def deviceId = data[8] + data[7]
        log.info "Profile ID: 0x${profileId}"
        log.info "Device ID: 0x${deviceId}"

        if (data.size() > 10) {
            def inClusterCount = Integer.parseInt(data[10], 16)
            log.info "Input Clusters (${inClusterCount}):"

            def idx = 11
            def inClusters = []
            for (int i = 0; i < inClusterCount && idx + 1 < data.size(); i++) {
                def clusterId = data[idx + 1] + data[idx]
                def clusterName = CLUSTER_NAMES[clusterId.toUpperCase()] ?: "Unknown"
                log.info "  - 0x${clusterId} (${clusterName})"
                inClusters << clusterId
                idx += 2
            }

            if (idx < data.size()) {
                def outClusterCount = Integer.parseInt(data[idx], 16)
                log.info "Output Clusters (${outClusterCount}):"
                idx++

                def outClusters = []
                for (int i = 0; i < outClusterCount && idx + 1 < data.size(); i++) {
                    def clusterId = data[idx + 1] + data[idx]
                    def clusterName = CLUSTER_NAMES[clusterId.toUpperCase()] ?: "Unknown"
                    log.info "  - 0x${clusterId} (${clusterName})"
                    outClusters << clusterId
                    idx += 2
                }

                // Store cluster info
                state.inClusters = inClusters.join(",")
                state.outClusters = outClusters.join(",")

                // Generate fingerprint
                log.info "=".multiply(50)
                log.info "SUGGESTED FINGERPRINT:"
                log.info "=".multiply(50)
                log.info "fingerprint profileId: \"${profileId}\", endpointId: \"${endpoint}\", " +
                         "inClusters: \"${inClusters.join(',').toUpperCase()}\", " +
                         "outClusters: \"${outClusters.join(',').toUpperCase()}\", " +
                         "manufacturer: \"${device.currentValue('deviceManufacturer') ?: 'UNKNOWN'}\", " +
                         "model: \"${device.currentValue('deviceModel') ?: 'UNKNOWN'}\""
            }
        }
    }

    log.info "=".multiply(50)
}

private void parseActiveEndpoints(List data) {
    log.info "=".multiply(50)
    log.info "ACTIVE ENDPOINTS"
    log.info "=".multiply(50)

    if (data.size() < 3) return

    def status = data[0]
    if (status != "00") {
        log.warn "Active endpoints request failed"
        return
    }

    def endpointCount = Integer.parseInt(data[3], 16)
    log.info "Number of endpoints: ${endpointCount}"

    for (int i = 0; i < endpointCount && (4 + i) < data.size(); i++) {
        log.info "  - Endpoint ${data[4 + i]}"
    }

    log.info "=".multiply(50)
}

private void parseReportAttributes(String clusterId, List data) {
    log.info "REPORT ATTRIBUTES from cluster 0x${clusterId}"

    def idx = 0
    while (idx + 2 < data.size()) {
        def attrId = data[idx + 1] + data[idx]
        def dataType = data[idx + 2]
        idx += 3

        def value = ""
        def valueLen = getDataTypeLength(dataType)

        if (valueLen > 0 && idx + valueLen <= data.size()) {
            for (int i = valueLen - 1; i >= 0; i--) {
                value += data[idx + i]
            }
            idx += valueLen
        } else if (dataType == "42" && idx < data.size()) {
            // String type
            def strLen = Integer.parseInt(data[idx], 16)
            idx++
            for (int i = 0; i < strLen && idx < data.size(); i++) {
                value += (char)Integer.parseInt(data[idx], 16)
                idx++
            }
        }

        log.info "  Attr 0x${attrId} = ${value} (type: ${DATA_TYPES[dataType] ?: dataType})"
    }
}

private int getDataTypeLength(String dataType) {
    switch(dataType) {
        case "10": return 1  // Boolean
        case "20": return 1  // Uint8
        case "21": return 2  // Uint16
        case "22": return 3  // Uint24
        case "23": return 4  // Uint32
        case "28": return 1  // Int8
        case "29": return 2  // Int16
        case "2B": return 4  // Int32
        case "30": return 1  // Enum8
        case "31": return 2  // Enum16
        case "39": return 4  // Float
        default: return 0
    }
}

private String decodeValue(String cluster, String attrId, String value, String encoding) {
    if (!value) return null

    try {
        switch(encoding) {
            case "42":  // String
                def result = ""
                for (int i = 0; i < value.length(); i += 2) {
                    def hex = value.substring(i, Math.min(i + 2, value.length()))
                    def charCode = Integer.parseInt(hex, 16)
                    if (charCode >= 32 && charCode < 127) {
                        result += (char)charCode
                    }
                }
                return result

            case "20":  // Uint8
                return Integer.parseInt(value, 16).toString()

            case "21":  // Uint16
                return Integer.parseInt(value, 16).toString()

            case "29":  // Int16
                def intVal = Integer.parseInt(value, 16)
                if (intVal > 32767) intVal -= 65536
                // Check if temperature (cluster 0201/0402, divide by 100)
                if ((cluster == "0201" && attrId in ["0000", "0011", "0012"]) ||
                    cluster == "0402") {
                    return "${intVal / 100.0}°C"
                }
                return intVal.toString()

            case "30":  // Enum8
                def enumVal = Integer.parseInt(value, 16)
                // Decode known enums
                if (cluster == "0201" && attrId == "001C") {
                    def modes = [0: "off", 1: "auto", 3: "cool", 4: "heat"]
                    return modes[enumVal] ?: "unknown(${enumVal})"
                }
                if (cluster == "0201" && attrId == "0029") {
                    return (enumVal & 0x01) ? "heating" : "idle"
                }
                return enumVal.toString()

            case "10":  // Boolean
                return value == "01" ? "true" : "false"

            default:
                return null
        }
    } catch (e) {
        return null
    }
}
