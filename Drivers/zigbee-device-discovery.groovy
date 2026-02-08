/**
 *  Zigbee Device Discovery Tool
 *
 *  A diagnostic driver that discovers all endpoints, clusters, and attributes
 *  from any Zigbee device to help create custom drivers.
 *
 *  Version: 1.1.0 - Added Aqara switch testing capabilities
 *
 *  Instructions:
 *  1. Change your device to use this driver
 *  2. Click "Discover Device" button
 *  3. Wake up your device (press a button on it)
 *  4. Wait and check the logs
 *  5. Copy the discovery results to create a proper driver
 *
 *  For Aqara devices:
 *  - Use "Test Aqara Switch" to test On/Off on different endpoints
 *  - Use "Read Aqara FCC0" to read proprietary cluster
 *  - Use "Send Aqara Magic" to initialize device for third-party hub
 */

metadata {
    definition (name: "Zigbee Device Discovery Tool", namespace: "custom", author: "Custom") {
        capability "Configuration"
        capability "Refresh"
        capability "Switch"  // Add switch capability for testing

        attribute "discoveryStatus", "string"
        attribute "lastMessage", "string"
        attribute "deviceModel", "string"
        attribute "deviceManufacturer", "string"
        attribute "messageCount", "number"
        attribute "lastSwitchResponse", "string"

        command "discoverDevice"
        command "discoverBasicInfo"
        command "discoverEndpoints"
        command "discoverClusters"
        command "readThermostatCluster"
        command "readPowerCluster"
        command "readAllCommonClusters"
        command "clearResults"
        command "testCommunication"

        // Aqara-specific commands
        command "testAqaraSwitchOn"
        command "testAqaraSwitchOff"
        command "testSwitchEndpoint1"
        command "testSwitchEndpoint2"
        command "testSwitchEndpoint15"
        command "readAqaraFCC0"
        command "sendAqaraMagicBytes"
        command "discoverAllEndpointClusters"
        command "readOnOffCluster"
        command "testRawOnCommand"
        command "testRawOffCommand"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true
        input name: "testEndpoint", type: "enum", title: "Test Endpoint", options: ["01", "02", "03", "15", "F2"], defaultValue: "01"
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

// ==================== SWITCH CONTROL (for testing) ====================

def on() {
    log.info "=".multiply(60)
    log.info "SWITCH ON COMMAND"
    log.info "=".multiply(60)
    testAqaraSwitchOn()
}

def off() {
    log.info "=".multiply(60)
    log.info "SWITCH OFF COMMAND"
    log.info "=".multiply(60)
    testAqaraSwitchOff()
}

// ==================== AQARA SPECIFIC COMMANDS ====================

def testAqaraSwitchOn() {
    def ep = settings?.testEndpoint ?: "01"
    log.info "=".multiply(60)
    log.info "TESTING AQARA SWITCH ON - Endpoint ${ep}"
    log.info "=".multiply(60)
    log.info "Sending multiple ON command formats..."

    def cmds = []

    // Standard ZCL On command
    log.info "1. Standard zigbee.on() to endpoint ${ep}"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x01 {}"
    cmds += "delay 500"

    // Read back the state
    log.info "2. Reading back On/Off state..."
    cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x0000 {}"
    cmds += "delay 500"

    // Also try toggle command
    log.info "3. Trying toggle command..."
    cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x02 {}"

    return cmds
}

def testAqaraSwitchOff() {
    def ep = settings?.testEndpoint ?: "01"
    log.info "=".multiply(60)
    log.info "TESTING AQARA SWITCH OFF - Endpoint ${ep}"
    log.info "=".multiply(60)

    def cmds = []

    // Standard ZCL Off command
    log.info "1. Standard zigbee.off() to endpoint ${ep}"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x00 {}"
    cmds += "delay 500"

    // Read back the state
    log.info "2. Reading back On/Off state..."
    cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x0000 {}"

    return cmds
}

def testSwitchEndpoint1() {
    log.info "Testing ON/OFF on Endpoint 01"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"
    cmds += "delay 2000"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"
    return cmds
}

def testSwitchEndpoint2() {
    log.info "Testing ON/OFF on Endpoint 02"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x01 {}"
    cmds += "delay 2000"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x00 {}"
    return cmds
}

def testSwitchEndpoint15() {
    log.info "Testing ON/OFF on Endpoint 15 (0x15 = 21 decimal)"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x15 0x0006 0x01 {}"
    cmds += "delay 2000"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x15 0x0006 0x00 {}"
    return cmds
}

def testRawOnCommand() {
    log.info "=".multiply(60)
    log.info "SENDING RAW ON COMMANDS TO ALL LIKELY ENDPOINTS"
    log.info "=".multiply(60)

    def cmds = []

    // Try endpoint 1
    log.info "Endpoint 01: ON"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"
    cmds += "delay 1000"

    // Try endpoint 2
    log.info "Endpoint 02: ON"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x01 {}"
    cmds += "delay 1000"

    // Try endpoint 21 (0x15) - common for Aqara
    log.info "Endpoint 15 (0x15=21): ON"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x15 0x0006 0x01 {}"
    cmds += "delay 1000"

    // Try endpoint 242 (0xF2) - sometimes used
    log.info "Endpoint F2 (242): ON"
    cmds += "he cmd 0x${device.deviceNetworkId} 0xF2 0x0006 0x01 {}"

    return cmds
}

def testRawOffCommand() {
    log.info "=".multiply(60)
    log.info "SENDING RAW OFF COMMANDS TO ALL LIKELY ENDPOINTS"
    log.info "=".multiply(60)

    def cmds = []

    // Try endpoint 1
    log.info "Endpoint 01: OFF"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"
    cmds += "delay 1000"

    // Try endpoint 2
    log.info "Endpoint 02: OFF"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x00 {}"
    cmds += "delay 1000"

    // Try endpoint 21 (0x15)
    log.info "Endpoint 15 (0x15=21): OFF"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x15 0x0006 0x00 {}"
    cmds += "delay 1000"

    // Try endpoint 242 (0xF2)
    log.info "Endpoint F2 (242): OFF"
    cmds += "he cmd 0x${device.deviceNetworkId} 0xF2 0x0006 0x00 {}"

    return cmds
}

def readAqaraFCC0() {
    log.info "=".multiply(60)
    log.info "READING AQARA FCC0 CLUSTER ATTRIBUTES"
    log.info "=".multiply(60)

    def cmds = []

    // Read common Aqara FCC0 attributes
    log.info "Reading FCC0 attribute 0x00F7 (TLV data)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: "0x115F"])
    cmds += "delay 500"

    log.info "Reading FCC0 attribute 0x0009 (device mode)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: "0x115F"])
    cmds += "delay 500"

    log.info "Reading FCC0 attribute 0x0200 (operation mode)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x0200, [mfgCode: "0x115F"])
    cmds += "delay 500"

    log.info "Reading FCC0 attribute 0x0201 (power outage memory)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x0201, [mfgCode: "0x115F"])
    cmds += "delay 500"

    log.info "Reading FCC0 attribute 0x0202 (startup on/off)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x0202, [mfgCode: "0x115F"])
    cmds += "delay 500"

    log.info "Reading FCC0 attribute 0x0207 (switch type)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x0207, [mfgCode: "0x115F"])
    cmds += "delay 500"

    // Also try reading without mfgCode
    log.info "Reading FCC0 attribute 0x00F7 (without mfgCode)..."
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7)

    return cmds
}

def sendAqaraMagicBytes() {
    log.info "=".multiply(60)
    log.info "SENDING AQARA MAGIC BYTES FOR THIRD-PARTY HUB ACTIVATION"
    log.info "=".multiply(60)
    log.info "These commands help Aqara devices work with non-Aqara hubs"

    def cmds = []

    // Write to FCC0 cluster, attribute 0x0009 to enable device
    log.info "1. Writing magic byte to FCC0:0x0009..."
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, 0x01, [mfgCode: "0x115F"])
    cmds += "delay 500"

    // Aqara specific binding sometimes needed
    log.info "2. Binding On/Off cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind electrical measurement
    log.info "3. Binding Electrical Measurement cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0B04 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind metering
    log.info "4. Binding Metering cluster..."
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0702 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Configure reporting for On/Off
    log.info "5. Configuring On/Off reporting..."
    cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 3600, null)
    cmds += "delay 500"

    // Read back device state
    log.info "6. Reading device state..."
    cmds += zigbee.readAttribute(0x0006, 0x0000)

    return cmds
}

def readOnOffCluster() {
    log.info "=".multiply(60)
    log.info "READING ON/OFF CLUSTER FROM ALL ENDPOINTS"
    log.info "=".multiply(60)

    def cmds = []

    // Read from multiple endpoints
    ["01", "02", "15", "F2"].each { ep ->
        log.info "Reading On/Off from endpoint ${ep}..."
        cmds += "he rattr 0x${device.deviceNetworkId} 0x${ep} 0x0006 0x0000 {}"
        cmds += "delay 300"
    }

    // Also read cluster attributes list
    log.info "Discovering On/Off cluster attributes..."
    cmds += zigbee.readAttribute(0x0006, 0x0000)  // OnOff
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x4000)  // GlobalSceneControl
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x4001)  // OnTime
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x4002)  // OffWaitTime
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x4003)  // StartUpOnOff
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x8000)  // Aqara custom?
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x8001)  // Aqara custom?
    cmds += "delay 200"
    cmds += zigbee.readAttribute(0x0006, 0x8002)  // Aqara custom?

    return cmds
}

def discoverAllEndpointClusters() {
    log.info "=".multiply(60)
    log.info "DISCOVERING CLUSTERS ON ALL ENDPOINTS (1, 2, 21, 242)"
    log.info "=".multiply(60)

    def cmds = []

    // Request simple descriptor for common Aqara endpoints
    ["01", "02", "15", "F2"].each { ep ->
        log.info "Requesting Simple Descriptor for endpoint ${ep}..."
        cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} ${ep}} {0x0000}"
        cmds += "delay 500"
    }

    // Also request active endpoints
    log.info "Requesting Active Endpoints list..."
    cmds += "he raw 0x${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"

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
        // Parse the description to get map
        def descMap = zigbee.parseDescriptionAsMap(description)
        log.info "Parsed Map: ${descMap}"

        if (description.startsWith("read attr -")) {
            result = parseReadAttr(description)
        }
        else if (description.startsWith("catchall:")) {
            result = parseCatchall(description)
        }
        else {
            log.info "Other message type detected"
            // Still try to parse it
            if (descMap) {
                def cluster = descMap.cluster ?: descMap.clusterId
                def command = descMap.command
                def data = descMap.data
                def endpoint = descMap.endpoint ?: descMap.sourceEndpoint

                log.info "-".multiply(50)
                log.info "Generic Message Details:"
                log.info "  Endpoint: ${endpoint}"
                log.info "  Cluster: ${cluster} (${CLUSTER_NAMES[cluster?.toUpperCase()] ?: 'Unknown'})"
                log.info "  Command: ${command}"
                log.info "  Data: ${data}"
                log.info "-".multiply(50)

                // Check for On/Off cluster responses
                if (cluster == "0006" || cluster == "6") {
                    log.warn "*** ON/OFF CLUSTER RESPONSE DETECTED ***"
                    if (descMap.attrId == "0000" || data?.size() > 0) {
                        def onOff = descMap.value ?: (data ? data[0] : "unknown")
                        log.warn "*** SWITCH STATE: ${onOff == "01" || onOff == "1" ? "ON" : "OFF"} (raw: ${onOff}) ***"
                        sendEvent(name: "lastSwitchResponse", value: "Cluster 0006, State: ${onOff}")
                        sendEvent(name: "switch", value: (onOff == "01" || onOff == "1") ? "on" : "off")
                    }
                }

                // Check for FCC0 cluster (Aqara)
                if (cluster?.toUpperCase() == "FCC0") {
                    log.warn "*** AQARA FCC0 CLUSTER MESSAGE ***"
                    parseAqaraFCC0(descMap)
                }
            }
        }
    } catch (e) {
        log.error "Parse error: ${e.message}"
        log.error "Stack: ${e.getStackTrace()}"
    }

    sendEvent(name: "discoveryStatus", value: "Receiving data...")

    return result
}

// Parse Aqara FCC0 cluster data
private void parseAqaraFCC0(Map descMap) {
    log.info "=".multiply(50)
    log.info "AQARA FCC0 CLUSTER DATA"
    log.info "=".multiply(50)

    def attrId = descMap.attrId
    def value = descMap.value
    def data = descMap.data

    log.info "Attribute ID: ${attrId}"
    log.info "Value: ${value}"
    log.info "Data array: ${data}"

    // If this is the F7 TLV attribute
    if (attrId == "00F7" && value) {
        log.info "Parsing F7 TLV structure..."
        parseF7TLVData(value)
    }

    log.info "=".multiply(50)
}

// Parse the Aqara F7 TLV data format
private void parseF7TLVData(String hexData) {
    log.info "F7 TLV Raw Data: ${hexData}"
    log.info "F7 TLV Length: ${hexData.length() / 2} bytes"

    // Convert hex string to byte array
    def bytes = []
    for (int i = 0; i < hexData.length(); i += 2) {
        bytes << Integer.parseInt(hexData.substring(i, i + 2), 16)
    }

    log.info "Bytes: ${bytes.collect { String.format('%02X', it) }.join(' ')}"

    // Parse TLV structure
    int idx = 0
    while (idx < bytes.size() - 2) {
        def tag = bytes[idx]
        def type = bytes[idx + 1]
        idx += 2

        def tagHex = String.format('%02X', tag)
        def typeHex = String.format('%02X', type)

        log.info "Tag: 0x${tagHex}, Type: 0x${typeHex}"

        // Determine value length based on type
        def valueLen = 0
        switch (type) {
            case 0x10: valueLen = 1; break  // Boolean
            case 0x20: valueLen = 1; break  // Uint8
            case 0x21: valueLen = 2; break  // Uint16
            case 0x23: valueLen = 4; break  // Uint32
            case 0x28: valueLen = 1; break  // Int8
            case 0x29: valueLen = 2; break  // Int16
            case 0x39: valueLen = 4; break  // Float
            default:
                log.warn "Unknown type 0x${typeHex} at index ${idx}"
                return
        }

        if (idx + valueLen > bytes.size()) {
            log.warn "Not enough data for value"
            return
        }

        // Read value (little-endian)
        def rawBytes = bytes[idx..<(idx + valueLen)]
        idx += valueLen

        // Interpret value based on tag
        def value = interpretF7Value(tag, type, rawBytes)
        log.info "  Tag 0x${tagHex}: ${value}"

        // Store specific values
        switch (tag) {
            case 0x03:  // Temperature
                log.warn "*** TEMPERATURE: ${value}°C ***"
                break
            case 0x64:  // On/Off state
                log.warn "*** SWITCH STATE (from F7): ${value ? 'ON' : 'OFF'} ***"
                break
            case 0x95:  // Energy
                log.warn "*** ENERGY: ${value} kWh ***"
                break
            case 0x96:  // Voltage
                log.warn "*** VOLTAGE: ${value} V ***"
                break
            case 0x97:  // Current
                log.warn "*** CURRENT: ${value} A ***"
                break
            case 0x98:  // Power
                log.warn "*** POWER: ${value} W ***"
                break
        }
    }
}

private interpretF7Value(int tag, int type, List<Integer> bytes) {
    switch (type) {
        case 0x10:  // Boolean
            return bytes[0] != 0

        case 0x20:  // Uint8
            return bytes[0]

        case 0x21:  // Uint16 LE
            return bytes[0] + (bytes[1] << 8)

        case 0x23:  // Uint32 LE
            long val = bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24)
            return val

        case 0x28:  // Int8
            def v = bytes[0]
            return v > 127 ? v - 256 : v

        case 0x29:  // Int16 LE
            def v = bytes[0] + (bytes[1] << 8)
            return v > 32767 ? v - 65536 : v

        case 0x39:  // Float LE
            int bits = bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24)
            return Float.intBitsToFloat(bits)

        default:
            return "raw: ${bytes.collect { String.format('%02X', it) }.join('')}"
    }
}

private List parseReadAttr(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)

    def cluster = descMap.cluster
    def attrId = descMap.attrId
    def value = descMap.value
    def encoding = descMap.encoding
    def endpoint = descMap.endpoint ?: descMap.sourceEndpoint ?: "01"

    def clusterName = CLUSTER_NAMES[cluster?.toUpperCase()] ?: "Unknown (${cluster})"
    def dataType = DATA_TYPES[encoding] ?: encoding

    log.info "-".multiply(50)
    log.info "ATTRIBUTE READ RESPONSE"
    log.info "-".multiply(50)
    log.info "Endpoint: ${endpoint}"
    log.info "Cluster: 0x${cluster} (${clusterName})"
    log.info "Attribute: 0x${attrId}"
    log.info "Data Type: ${dataType}"
    log.info "Raw Value: ${value}"

    // Decode value based on cluster and attribute
    def decodedValue = decodeValue(cluster, attrId, value, encoding)
    if (decodedValue != null) {
        log.info "Decoded Value: ${decodedValue}"
    }

    // Special handling for On/Off cluster
    if (cluster?.toUpperCase() == "0006" && attrId == "0000") {
        def onOff = (value == "01" || value == "1") ? "on" : "off"
        log.warn "=".multiply(50)
        log.warn "*** ON/OFF STATE FROM ENDPOINT ${endpoint}: ${onOff.toUpperCase()} ***"
        log.warn "=".multiply(50)
        sendEvent(name: "switch", value: onOff)
        sendEvent(name: "lastSwitchResponse", value: "EP${endpoint}: ${onOff}")
    }

    // Special handling for FCC0 cluster (Aqara)
    if (cluster?.toUpperCase() == "FCC0") {
        log.warn "=".multiply(50)
        log.warn "*** AQARA FCC0 ATTRIBUTE RESPONSE ***"
        log.warn "=".multiply(50)

        if (attrId == "00F7" && value) {
            parseF7TLVData(value)
        } else {
            log.info "FCC0 Attribute 0x${attrId} = ${value}"
        }
    }

    log.info "-".multiply(50)

    // Store in state
    if (!state.discoveryResults) state.discoveryResults = [:]
    if (!state.discoveryResults[cluster]) state.discoveryResults[cluster] = [:]
    state.discoveryResults[cluster][attrId] = [
        value: value,
        decoded: decodedValue,
        encoding: encoding,
        endpoint: endpoint
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

    def clusterId = descMap.clusterId ?: descMap.cluster
    def clusterName = CLUSTER_NAMES[clusterId?.toUpperCase()] ?: "Unknown"

    log.info "-".multiply(50)
    log.info "CATCHALL MESSAGE"
    log.info "-".multiply(50)
    log.info "Profile: ${descMap.profileId}"
    log.info "Cluster: ${clusterId} (${clusterName})"
    log.info "Command: ${descMap.command}"
    log.info "Source Endpoint: ${descMap.sourceEndpoint}"
    log.info "Dest Endpoint: ${descMap.destinationEndpoint}"
    log.info "Data: ${descMap.data}"
    log.info "Direction: ${descMap.direction}"
    log.info "Is Cluster Specific: ${descMap.clusterInt}"

    // Check for On/Off cluster command response
    if (clusterId?.toUpperCase() == "0006") {
        log.warn "=".multiply(50)
        log.warn "*** ON/OFF CLUSTER CATCHALL ***"
        log.warn "=".multiply(50)
        log.warn "Command: ${descMap.command}"
        log.warn "From Endpoint: ${descMap.sourceEndpoint}"

        if (descMap.command == "0B") {
            // Default response - indicates command was received
            def status = descMap.data?.getAt(1) ?: "unknown"
            log.warn "*** COMMAND ACKNOWLEDGMENT - Status: ${status == "00" ? "SUCCESS" : "FAILED (${status})"} ***"
            sendEvent(name: "lastSwitchResponse", value: "ACK from EP${descMap.sourceEndpoint}: ${status}")
        } else if (descMap.command == "01") {
            log.warn "*** ON COMMAND DETECTED ***"
        } else if (descMap.command == "00") {
            log.warn "*** OFF COMMAND DETECTED ***"
        } else if (descMap.command == "0A") {
            // Report attributes
            if (descMap.data?.size() > 2) {
                def onOff = descMap.data[2]
                log.warn "*** SWITCH STATE REPORT: ${onOff == "01" ? "ON" : "OFF"} ***"
                sendEvent(name: "switch", value: onOff == "01" ? "on" : "off")
            }
        }
        log.warn "=".multiply(50)
    }

    // Check for FCC0 cluster (Aqara)
    if (clusterId?.toUpperCase() == "FCC0") {
        log.warn "=".multiply(50)
        log.warn "*** AQARA FCC0 CATCHALL ***"
        log.warn "=".multiply(50)
        log.warn "Command: ${descMap.command}"
        log.warn "Data: ${descMap.data}"

        // If it's a report attribute command (0A)
        if (descMap.command == "0A" && descMap.data) {
            parseFCC0ReportAttributes(descMap.data)
        }
        log.warn "=".multiply(50)
    }

    // Parse simple descriptor response
    if (clusterId == "8004" && descMap.data) {
        parseSimpleDescriptor(descMap.data)
    }

    // Parse active endpoints response
    if (clusterId == "8005" && descMap.data) {
        parseActiveEndpoints(descMap.data)
    }

    // Parse report attributes
    if (descMap.command == "0A" && descMap.data && clusterId != "FCC0") {
        parseReportAttributes(clusterId, descMap.data)
    }

    log.info "-".multiply(50)

    return []
}

// Parse FCC0 report attributes (Aqara specific)
private void parseFCC0ReportAttributes(List data) {
    log.info "Parsing FCC0 Report Attributes..."
    log.info "Data: ${data}"

    // Check if this contains F7 attribute (00F7)
    if (data.size() >= 4 && data[0] == "F7" && data[1] == "00") {
        log.info "Detected F7 attribute in report"
        // Extract the value portion
        def dataType = data[2]
        def valueStart = 3

        if (dataType == "41" || dataType == "42") {
            // String type - next byte is length
            def len = Integer.parseInt(data[3], 16)
            valueStart = 4
            if (data.size() > valueStart + len) {
                def hexValue = data[valueStart..<(valueStart + len)].join("")
                parseF7TLVData(hexValue)
            }
        }
    }
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
