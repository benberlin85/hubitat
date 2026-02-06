/**
 *  Aqara Wall Outlet H2 EU
 *  Model: lumi.plug.aeu001 / WP-P01D
 *
 *  A comprehensive driver for the Aqara Wall Outlet H2 EU with power monitoring.
 *
 *  Features:
 *  - On/Off control
 *  - Power monitoring (W)
 *  - Energy metering (kWh)
 *  - Voltage monitoring (V)
 *  - Current monitoring (A)
 *  - Device temperature monitoring
 *  - Overload protection (configurable 100-3840W)
 *  - Power outage memory (restore state after power loss)
 *  - LED indicator control
 *  - Button/child lock
 *  - Charging protection (auto-off when charging complete)
 *  - Power outage counter
 *  - Health check monitoring
 *
 *  Version: 1.0.0
 *
 *  References:
 *  - https://www.zigbee2mqtt.io/devices/WP-P01D.html
 *  - https://github.com/zigpy/zha-device-handlers/issues/3187
 *  - https://www.aqara.com/en/product/wall-outlet-h2-eu/
 */

import hubitat.helper.HexUtils
import groovy.transform.Field

// ==================== Constants ====================

@Field static final String DRIVER_VERSION = "1.0.0"

// Cluster IDs
@Field static final int CLUSTER_BASIC = 0x0000
@Field static final int CLUSTER_ONOFF = 0x0006
@Field static final int CLUSTER_MULTISTATE = 0x0012
@Field static final int CLUSTER_TEMPERATURE = 0x0402
@Field static final int CLUSTER_METERING = 0x0702
@Field static final int CLUSTER_ELECTRICAL = 0x0B04
@Field static final int CLUSTER_LUMI = 0xFCC0

// Lumi manufacturer code
@Field static final int LUMI_MFG_CODE = 0x115F

// On/Off Cluster Attributes
@Field static final int ATTR_ONOFF = 0x0000

// Temperature Cluster Attributes
@Field static final int ATTR_MEASURED_VALUE = 0x0000

// Electrical Measurement Attributes (0x0B04)
@Field static final int ATTR_RMS_VOLTAGE = 0x0505
@Field static final int ATTR_RMS_CURRENT = 0x0508
@Field static final int ATTR_ACTIVE_POWER = 0x050B
@Field static final int ATTR_AC_VOLTAGE_DIVISOR = 0x0601
@Field static final int ATTR_AC_CURRENT_DIVISOR = 0x0603
@Field static final int ATTR_AC_POWER_DIVISOR = 0x0605

// Metering Attributes (0x0702)
@Field static final int ATTR_CURRENT_SUMMATION = 0x0000
@Field static final int ATTR_METERING_MULTIPLIER = 0x0301
@Field static final int ATTR_METERING_DIVISOR = 0x0302

// Lumi-specific attributes (0xFCC0)
@Field static final int ATTR_LUMI_POWER_OUTAGE_COUNT = 0x0002
@Field static final int ATTR_LUMI_OVERLOAD_PROTECTION = 0x020B
@Field static final int ATTR_LUMI_LED_INDICATOR = 0x0203
@Field static final int ATTR_LUMI_BUTTON_LOCK = 0x0200
@Field static final int ATTR_LUMI_POWER_ON_BEHAVIOR = 0x0201
@Field static final int ATTR_LUMI_CHARGING_PROTECTION = 0x0202
@Field static final int ATTR_LUMI_CHARGING_LIMIT = 0x0266

// Power-on behavior values
@Field static final Map POWER_ON_BEHAVIOR = [
    0: "off",
    1: "on",
    2: "previous",
    3: "inverted"
]
@Field static final Map POWER_ON_BEHAVIOR_REVERSE = [
    "off": 0,
    "on": 1,
    "previous": 2,
    "inverted": 3
]

// ==================== Metadata ====================

metadata {
    definition (name: "Aqara Wall Outlet H2 EU", namespace: "benberlin", author: "Ben Fayershtein") {
        capability "Actuator"
        capability "Switch"
        capability "Outlet"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "CurrentMeter"
        capability "TemperatureMeasurement"
        capability "Refresh"
        capability "Configuration"
        capability "HealthCheck"

        // Custom attributes
        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "lastActivity", "string"
        attribute "powerOnBehavior", "enum", ["off", "on", "previous", "inverted"]
        attribute "overloadProtection", "number"
        attribute "ledIndicator", "enum", ["on", "off"]
        attribute "buttonLock", "enum", ["locked", "unlocked"]
        attribute "chargingProtection", "enum", ["on", "off"]
        attribute "chargingLimit", "number"
        attribute "powerOutageCount", "number"
        attribute "driverVersion", "string"

        // Commands
        command "setPowerOnBehavior", [[name: "behavior*", type: "ENUM", constraints: ["off", "on", "previous", "inverted"],
                                        description: "off=always off, on=always on, previous=restore last state, inverted=opposite of last state"]]
        command "setOverloadProtection", [[name: "maxPower*", type: "NUMBER", description: "Max power before auto-off (100-3840W)"]]
        command "setLedIndicator", [[name: "enabled*", type: "ENUM", constraints: ["on", "off"],
                                     description: "Enable/disable LED indicator"]]
        command "setButtonLock", [[name: "locked*", type: "ENUM", constraints: ["locked", "unlocked"],
                                   description: "Lock/unlock physical button"]]
        command "setChargingProtection", [[name: "enabled*", type: "ENUM", constraints: ["on", "off"],
                                           description: "Auto-off when charging complete"]]
        command "setChargingLimit", [[name: "power*", type: "NUMBER", description: "Charging complete threshold (0.1-2W)"]]
        command "resetEnergy"

        // Fingerprints
        fingerprint profileId: "0104", endpointId: "01",
                    inClusters: "0000,0003,0004,0005,0006,0012,0402,0702,0B04,FCC0",
                    outClusters: "000A,0019",
                    manufacturer: "Aqara", model: "lumi.plug.aeu001",
                    deviceJoinName: "Aqara Wall Outlet H2 EU"

        fingerprint manufacturer: "Aqara", model: "lumi.plug.aeu001",
                    deviceJoinName: "Aqara Wall Outlet H2 EU"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable info logging", defaultValue: true

        input name: "tempUnit", type: "enum", title: "Temperature unit",
              options: [["C": "Celsius"], ["F": "Fahrenheit"]],
              defaultValue: "C"

        input name: "pollInterval", type: "enum", title: "Power polling interval",
              options: [
                  ["0": "Disabled (rely on reports)"],
                  ["60": "Every 1 minute"],
                  ["300": "Every 5 minutes"],
                  ["600": "Every 10 minutes"]
              ],
              defaultValue: "300"

        input name: "healthCheckInterval", type: "enum", title: "Health check interval",
              options: [
                  ["0": "Disabled"],
                  ["60": "Every 1 hour"],
                  ["120": "Every 2 hours"]
              ],
              defaultValue: "60"

        // Divisor settings (auto-detected but can be overridden)
        input name: "powerDivisor", type: "number", title: "Power divisor",
              description: "Divisor for power (default: 10)", defaultValue: 10

        input name: "voltageDivisor", type: "number", title: "Voltage divisor",
              description: "Divisor for voltage (default: 10)", defaultValue: 10

        input name: "currentDivisor", type: "number", title: "Current divisor",
              description: "Divisor for current (default: 1000)", defaultValue: 1000

        input name: "energyDivisor", type: "number", title: "Energy divisor",
              description: "Divisor for energy (default: 1000)", defaultValue: 1000
    }
}

// ==================== Lifecycle ====================

def installed() {
    log.info "Aqara Wall Outlet H2 EU installed - Version ${DRIVER_VERSION}"
    initialize()
}

def updated() {
    log.info "Aqara Wall Outlet H2 EU updated"
    unschedule()

    if (logEnable) runIn(1800, logsOff)

    // Store divisors
    state.powerDivisor = powerDivisor ?: 10
    state.voltageDivisor = voltageDivisor ?: 10
    state.currentDivisor = currentDivisor ?: 1000
    state.energyDivisor = energyDivisor ?: 1000

    schedulePoll()
    scheduleHealthCheck()

    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
}

def initialize() {
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "healthStatus", value: "online")

    state.lastSuccessfulComm = now()
    state.powerDivisor = powerDivisor ?: 10
    state.voltageDivisor = voltageDivisor ?: 10
    state.currentDivisor = currentDivisor ?: 1000
    state.energyDivisor = energyDivisor ?: 1000

    schedulePoll()
    scheduleHealthCheck()

    runIn(5, configure)
}

// ==================== Configuration ====================

def configure() {
    log.info "Configuring Aqara Wall Outlet H2 EU..."

    def cmds = []

    // Bind On/Off cluster
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind Electrical Measurement cluster
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0B04 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind Metering cluster
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0702 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Bind Temperature cluster
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}"
    cmds += "delay 500"

    // Configure On/Off reporting
    cmds += zigbee.configureReporting(CLUSTER_ONOFF, ATTR_ONOFF, 0x10, 0, 3600, null)
    cmds += "delay 300"

    // Configure power reporting
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL, ATTR_ACTIVE_POWER, 0x29, 5, 300, 5)
    cmds += "delay 300"

    // Configure voltage reporting
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL, ATTR_RMS_VOLTAGE, 0x21, 60, 600, 10)
    cmds += "delay 300"

    // Configure current reporting
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL, ATTR_RMS_CURRENT, 0x21, 5, 300, 10)
    cmds += "delay 300"

    // Configure energy reporting
    cmds += zigbee.configureReporting(CLUSTER_METERING, ATTR_CURRENT_SUMMATION, 0x25, 60, 3600, 1)
    cmds += "delay 300"

    // Configure temperature reporting
    cmds += zigbee.configureReporting(CLUSTER_TEMPERATURE, ATTR_MEASURED_VALUE, 0x29, 60, 3600, 10)
    cmds += "delay 500"

    // Read divisors
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_AC_POWER_DIVISOR)
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_AC_VOLTAGE_DIVISOR)
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_AC_CURRENT_DIVISOR)
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_METERING, ATTR_METERING_DIVISOR)
    cmds += "delay 500"

    // Initial refresh
    cmds += refresh()

    return cmds
}

// ==================== Scheduling ====================

private void schedulePoll() {
    def pollSecs = (pollInterval ?: "300") as int

    if (pollSecs > 0) {
        switch(pollSecs) {
            case 60: runEvery1Minute(pollPower); break
            case 300: runEvery5Minutes(pollPower); break
            case 600: runEvery10Minutes(pollPower); break
        }
        logInfo "Polling scheduled every ${pollSecs} seconds"
    }
}

private void scheduleHealthCheck() {
    def interval = (healthCheckInterval ?: "60") as int
    if (interval > 0) {
        switch(interval) {
            case 60: runEvery1Hour(healthCheck); break
            case 120: runEvery3Hours(healthCheck); break
        }
    }
}

def healthCheck() {
    def lastActivity = state.lastSuccessfulComm ?: 0
    def healthInterval = ((healthCheckInterval ?: "60") as int) * 60 * 1000
    def threshold = healthInterval * 2

    if (now() - lastActivity > threshold) {
        if (device.currentValue("healthStatus") != "offline") {
            sendEvent(name: "healthStatus", value: "offline")
            logWarn "Device appears offline"
        }
        refresh()
    } else {
        if (device.currentValue("healthStatus") != "online") {
            sendEvent(name: "healthStatus", value: "online")
        }
    }
}

// ==================== Polling ====================

def pollPower() {
    logDebug "Polling power..."
    def cmds = []
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_ACTIVE_POWER)
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_RMS_VOLTAGE)
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_RMS_CURRENT)
    cmds += zigbee.readAttribute(CLUSTER_METERING, ATTR_CURRENT_SUMMATION)
    cmds += zigbee.readAttribute(CLUSTER_TEMPERATURE, ATTR_MEASURED_VALUE)
    sendZigbeeCommands(cmds)
}

def refresh() {
    logDebug "Refresh"

    def cmds = []

    // On/Off state
    cmds += zigbee.readAttribute(CLUSTER_ONOFF, ATTR_ONOFF)
    cmds += "delay 100"

    // Power
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_ACTIVE_POWER)
    cmds += "delay 100"

    // Voltage
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_RMS_VOLTAGE)
    cmds += "delay 100"

    // Current
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL, ATTR_RMS_CURRENT)
    cmds += "delay 100"

    // Energy
    cmds += zigbee.readAttribute(CLUSTER_METERING, ATTR_CURRENT_SUMMATION)
    cmds += "delay 100"

    // Temperature
    cmds += zigbee.readAttribute(CLUSTER_TEMPERATURE, ATTR_MEASURED_VALUE)

    return cmds
}

def ping() {
    return refresh()
}

// ==================== Switch Commands ====================

def on() {
    logInfo "Turning on"
    return zigbee.on()
}

def off() {
    logInfo "Turning off"
    return zigbee.off()
}

// ==================== Custom Commands ====================

def setPowerOnBehavior(String behavior) {
    logInfo "Setting power-on behavior to: ${behavior}"

    def value = POWER_ON_BEHAVIOR_REVERSE[behavior]
    if (value == null) {
        logWarn "Invalid power-on behavior: ${behavior}"
        return
    }

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_POWER_ON_BEHAVIOR, 0x20, value, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_POWER_ON_BEHAVIOR, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "powerOnBehavior", value: behavior)
    sendZigbeeCommands(cmds)
}

def setOverloadProtection(maxPower) {
    Integer maxPowerInt = maxPower as Integer
    maxPowerInt = [[maxPowerInt, 3840].min(), 100].max()
    logInfo "Setting overload protection to ${maxPowerInt}W"

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_OVERLOAD_PROTECTION, 0x39, floatToHex(maxPowerInt), [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_OVERLOAD_PROTECTION, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "overloadProtection", value: maxPowerInt, unit: "W")
    sendZigbeeCommands(cmds)
}

def setLedIndicator(String enabled) {
    logInfo "Setting LED indicator to: ${enabled}"

    def value = (enabled == "on") ? 0x01 : 0x00

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_LED_INDICATOR, 0x20, value, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_LED_INDICATOR, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "ledIndicator", value: enabled)
    sendZigbeeCommands(cmds)
}

def setButtonLock(String locked) {
    logInfo "Setting button lock to: ${locked}"

    def value = (locked == "locked") ? 0x01 : 0x00

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_BUTTON_LOCK, 0x20, value, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_BUTTON_LOCK, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "buttonLock", value: locked)
    sendZigbeeCommands(cmds)
}

def setChargingProtection(String enabled) {
    logInfo "Setting charging protection to: ${enabled}"

    def value = (enabled == "on") ? 0x01 : 0x00

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_CHARGING_PROTECTION, 0x20, value, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_CHARGING_PROTECTION, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "chargingProtection", value: enabled)
    sendZigbeeCommands(cmds)
}

def setChargingLimit(power) {
    BigDecimal powerVal = power as BigDecimal
    powerVal = [[powerVal, 2.0].min(), 0.1].max()
    logInfo "Setting charging limit to ${powerVal}W"

    // Convert to integer representation (multiply by 10 for 0.1W resolution)
    Integer powerInt = (powerVal * 10).toInteger()

    def cmds = []
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, ATTR_LUMI_CHARGING_LIMIT, 0x21, powerInt, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_CHARGING_LIMIT, [mfgCode: LUMI_MFG_CODE])

    sendEvent(name: "chargingLimit", value: powerVal, unit: "W")
    sendZigbeeCommands(cmds)
}

def resetEnergy() {
    logInfo "Resetting energy meter"
    // Note: Not all devices support this. The actual reset command may vary.
    sendEvent(name: "energy", value: 0, unit: "kWh")
}

// ==================== Parse ====================

def parse(String description) {
    logDebug "Parse: $description"

    updateLastActivity()

    def events = []

    try {
        if (description.startsWith("read attr -")) {
            def descMap = zigbee.parseDescriptionAsMap(description)
            logDebug "Read attr: $descMap"
            events += handleReadAttr(descMap)
        }
        else if (description.startsWith("catchall:")) {
            def descMap = zigbee.parseDescriptionAsMap(description)
            logDebug "Catchall: $descMap"
            events += handleCatchall(descMap)
        }
    } catch (e) {
        logWarn "Parse error: ${e.message}"
    }

    return events
}

private List handleReadAttr(Map descMap) {
    def events = []
    def cluster = descMap.cluster?.toUpperCase()
    def attrId = descMap.attrId?.toUpperCase()
    def value = descMap.value

    if (!value || value == "null") return events

    logDebug "Handling: cluster=${cluster}, attr=${attrId}, value=${value}"

    switch(cluster) {
        case "0006":  // On/Off
            events += handleOnOffCluster(attrId, value)
            break

        case "0402":  // Temperature
            events += handleTemperatureCluster(attrId, value)
            break

        case "0702":  // Metering
            events += handleMeteringCluster(attrId, value)
            break

        case "0B04":  // Electrical Measurement
            events += handleElectricalCluster(attrId, value)
            break

        case "FCC0":  // Lumi
            events += handleLumiCluster(attrId, value, descMap)
            break
    }

    return events
}

private List handleOnOffCluster(String attrId, String value) {
    def events = []

    if (attrId == "0000") {
        def switchState = value == "01" ? "on" : "off"
        events << createEvent(name: "switch", value: switchState)
        logInfo "Switch: ${switchState}"
    }

    return events
}

private List handleTemperatureCluster(String attrId, String value) {
    def events = []

    if (attrId == "0000") {
        Integer rawTemp = hexToSignedInt(value)
        BigDecimal tempC = rawTemp / 100.0

        // Convert to Fahrenheit if needed
        BigDecimal temp = tempC
        String unit = "°C"
        if (tempUnit == "F") {
            temp = (tempC * 9 / 5) + 32
            unit = "°F"
        }

        temp = ((temp * 10).toLong()) / 10.0

        events << createEvent(name: "temperature", value: temp, unit: unit)
        logInfo "Temperature: ${temp}${unit}"
    }

    return events
}

private List handleMeteringCluster(String attrId, String value) {
    def events = []

    switch(attrId) {
        case "0000":  // Current Summation Delivered (Energy)
            Long rawValue = Long.parseLong(value, 16)
            BigDecimal divisor = state.energyDivisor ?: (energyDivisor ?: 1000)
            BigDecimal energy = rawValue / divisor
            BigDecimal energyFormatted = ((energy * 1000).toLong()) / 1000.0

            events << createEvent(name: "energy", value: energyFormatted, unit: "kWh")
            logInfo "Energy: ${energyFormatted} kWh"
            break

        case "0301":  // Multiplier
            state.energyMultiplier = Integer.parseInt(value, 16)
            logDebug "Energy multiplier: ${state.energyMultiplier}"
            break

        case "0302":  // Divisor
            state.energyDivisor = Integer.parseInt(value, 16)
            logDebug "Energy divisor: ${state.energyDivisor}"
            break
    }

    return events
}

private List handleElectricalCluster(String attrId, String value) {
    def events = []

    switch(attrId) {
        case "050B":  // Active Power
            Integer rawPower = Integer.parseInt(value, 16)
            BigDecimal divisor = state.powerDivisor ?: (powerDivisor ?: 10)
            BigDecimal power = rawPower / divisor
            BigDecimal powerFormatted = ((power * 10).toLong()) / 10.0

            events << createEvent(name: "power", value: powerFormatted, unit: "W")
            logInfo "Power: ${powerFormatted} W"
            break

        case "0505":  // RMS Voltage
            Integer rawVoltage = Integer.parseInt(value, 16)
            BigDecimal vDivisor = state.voltageDivisor ?: (voltageDivisor ?: 10)
            BigDecimal voltage = rawVoltage / vDivisor
            BigDecimal voltageFormatted = ((voltage * 10).toLong()) / 10.0

            events << createEvent(name: "voltage", value: voltageFormatted, unit: "V")
            logInfo "Voltage: ${voltageFormatted} V"
            break

        case "0508":  // RMS Current
            Integer rawCurrent = Integer.parseInt(value, 16)
            BigDecimal cDivisor = state.currentDivisor ?: (currentDivisor ?: 1000)
            BigDecimal current = rawCurrent / cDivisor
            BigDecimal currentFormatted = ((current * 1000).toLong()) / 1000.0

            events << createEvent(name: "amperage", value: currentFormatted, unit: "A")
            logInfo "Current: ${currentFormatted} A"
            break

        case "0601":  // Voltage Divisor
            state.voltageDivisor = Integer.parseInt(value, 16)
            logDebug "Voltage divisor: ${state.voltageDivisor}"
            break

        case "0603":  // Current Divisor
            state.currentDivisor = Integer.parseInt(value, 16)
            logDebug "Current divisor: ${state.currentDivisor}"
            break

        case "0605":  // Power Divisor
            state.powerDivisor = Integer.parseInt(value, 16)
            logDebug "Power divisor: ${state.powerDivisor}"
            break
    }

    return events
}

private List handleLumiCluster(String attrId, String value, Map descMap) {
    def events = []

    switch(attrId) {
        case "0002":  // Power outage count
            Integer count = Integer.parseInt(value, 16)
            events << createEvent(name: "powerOutageCount", value: count)
            logDebug "Power outage count: ${count}"
            break

        case "0200":  // Button lock
            def locked = (value == "01") ? "locked" : "unlocked"
            events << createEvent(name: "buttonLock", value: locked)
            logDebug "Button lock: ${locked}"
            break

        case "0201":  // Power-on behavior
            Integer behaviorValue = Integer.parseInt(value, 16)
            def behavior = POWER_ON_BEHAVIOR[behaviorValue] ?: "unknown"
            events << createEvent(name: "powerOnBehavior", value: behavior)
            logDebug "Power-on behavior: ${behavior}"
            break

        case "0202":  // Charging protection
            def enabled = (value == "01") ? "on" : "off"
            events << createEvent(name: "chargingProtection", value: enabled)
            logDebug "Charging protection: ${enabled}"
            break

        case "0203":  // LED indicator
            def ledState = (value == "01") ? "on" : "off"
            events << createEvent(name: "ledIndicator", value: ledState)
            logDebug "LED indicator: ${ledState}"
            break

        case "020B":  // Overload protection
            // This is a float value
            def floatValue = hexToFloat(value)
            events << createEvent(name: "overloadProtection", value: floatValue.toInteger(), unit: "W")
            logDebug "Overload protection: ${floatValue}W"
            break

        case "0266":  // Charging limit
            Integer rawLimit = Integer.parseInt(value, 16)
            BigDecimal limit = rawLimit / 10.0
            events << createEvent(name: "chargingLimit", value: limit, unit: "W")
            logDebug "Charging limit: ${limit}W"
            break
    }

    return events
}

private List handleCatchall(Map descMap) {
    def events = []

    if (descMap.clusterId == "0006" && descMap.command == "0B") {
        if (descMap.data?.size() > 0) {
            def cmd = descMap.data[0]
            if (cmd == "01") {
                events << createEvent(name: "switch", value: "on")
            } else if (cmd == "00") {
                events << createEvent(name: "switch", value: "off")
            }
        }
    }

    return events
}

// ==================== Helper Methods ====================

private void updateLastActivity() {
    def now = new Date()
    sendEvent(name: "lastActivity", value: now.format("yyyy-MM-dd HH:mm:ss"))
    state.lastSuccessfulComm = now.getTime()

    if (device.currentValue("healthStatus") != "online") {
        sendEvent(name: "healthStatus", value: "online")
    }
}

private int hexToSignedInt(String hex) {
    if (!hex) return 0
    def value = Integer.parseInt(hex, 16)
    if (hex.length() == 4 && value > 32767) {
        value -= 65536
    }
    return value
}

private String floatToHex(float value) {
    return HexUtils.integerToHexString(Float.floatToIntBits(value), 4)
}

private float hexToFloat(String hex) {
    if (!hex || hex.length() != 8) return 0.0
    return Float.intBitsToFloat(Integer.parseInt(hex, 16))
}

void sendZigbeeCommands(def cmds) {
    if (cmds == null) return

    List cmdList = (cmds instanceof List) ? cmds.flatten() : [cmds]
    cmdList = cmdList.findAll { it != null && it != "" && !it.toString().startsWith("delay") }

    if (cmdList.isEmpty()) return

    logDebug "Sending ${cmdList.size()} command(s)"
    cmdList.each { cmd ->
        def hubAction = new hubitat.device.HubAction(cmd.toString(), hubitat.device.Protocol.ZIGBEE)
        sendHubCommand(hubAction)
    }
}

// ==================== Logging ====================

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}

private void logInfo(String msg) {
    if (txtEnable) log.info "${device.displayName}: ${msg}"
}

private void logWarn(String msg) {
    log.warn "${device.displayName}: ${msg}"
}

def logsOff() {
    log.warn "${device.displayName}: Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
