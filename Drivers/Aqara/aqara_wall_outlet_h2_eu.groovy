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

@Field static final String DRIVER_VERSION = "1.0.4"

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

        fingerprint manufacturer: "LUMI", model: "lumi.plug.aeu001",
                    deviceJoinName: "Aqara Wall Outlet H2 EU"

        fingerprint manufacturer: "aqara", model: "lumi.plug.aeu001",
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

    // Write Aqara "magic bytes" to enable device functionality
    // This is required for Aqara devices to work properly with third-party hubs
    // Write 0x01 to attribute 0xFFF0 on Lumi cluster to enable operation mode
    cmds += zigbee.writeAttribute(CLUSTER_LUMI, 0xFFF0, 0x41, "00", [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 500"

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

    // Bind Lumi cluster for special reports
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
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

    // Read Lumi-specific attributes
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_POWER_ON_BEHAVIOR, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_OVERLOAD_PROTECTION, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_LED_INDICATOR, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_BUTTON_LOCK, [mfgCode: LUMI_MFG_CODE])
    cmds += "delay 200"
    cmds += zigbee.readAttribute(CLUSTER_LUMI, ATTR_LUMI_POWER_OUTAGE_COUNT, [mfgCode: LUMI_MFG_CODE])
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
    cmds += "delay 100"

    // Read Lumi special report attribute (may contain power data)
    cmds += zigbee.readAttribute(CLUSTER_LUMI, 0x00F7, [mfgCode: LUMI_MFG_CODE])

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
    // Always log to info for debugging - can see even if debug is off
    log.info "${device.displayName}: Parse received: ${description?.take(100)}..."

    updateLastActivity()

    def events = []

    if (!description) {
        logWarn "Empty description received"
        return events
    }

    try {
        def descMap = null

        // Handle different description formats
        if (description.startsWith("read attr -")) {
            logDebug "Parsing read attr message"
            descMap = zigbee.parseDescriptionAsMap(description)
        } else if (description.startsWith("catchall:")) {
            logDebug "Parsing catchall message"
            descMap = zigbee.parseDescriptionAsMap(description)
        } else {
            // Try to parse anyway - some messages have different formats
            logDebug "Trying to parse unknown format: ${description.take(50)}"
            try {
                descMap = zigbee.parseDescriptionAsMap(description)
            } catch (e2) {
                logDebug "Could not parse: ${e2.message}"
            }
        }

        if (descMap) {
            // Normalize cluster field - catchall uses clusterId, read attr uses cluster
            String cluster = descMap.cluster ?: descMap.clusterId
            log.info "${device.displayName}: Parsed - cluster=${cluster}, attr=${descMap.attrId}, enc=${descMap.encoding}, cmd=${descMap.command}"

            if (cluster) {
                // Normalize the cluster field for handlers
                descMap.cluster = cluster

                // Handle catchall messages differently
                if (description.startsWith("catchall:")) {
                    events += handleCatchall(descMap)
                } else {
                    events += handleReadAttr(descMap)
                }
            }

            // Check for additional attributes (some Zigbee messages contain multiple attrs)
            if (descMap.additionalAttrs) {
                descMap.additionalAttrs.each { addAttr ->
                    logDebug "Additional attr: ${addAttr}"
                    def additionalMap = [:]
                    additionalMap.putAll(descMap)
                    additionalMap.attrId = addAttr.attrId
                    additionalMap.value = addAttr.value
                    additionalMap.encoding = addAttr.encoding
                    events += handleReadAttr(additionalMap)
                }
            }
        } else {
            logWarn "Could not parse description into map"
        }
    } catch (e) {
        log.error "${device.displayName}: Parse error: ${e.message}", e
    }

    if (events) {
        log.info "${device.displayName}: Generated ${events.size()} events"
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

        case "00F7":  // Aqara structured data - contains power, voltage, temperature, etc.
        case "F7":    // Sometimes attrId comes without leading zeros
            logDebug "Processing F7 struct data (encoding=${descMap?.encoding}): ${value?.take(60)}..."
            if (value && value.length() >= 4) {
                events += parseAqaraF7Struct(value)
            }
            break

        case "FFF2":  // Aqara time sync / heartbeat data
            logDebug "Aqara heartbeat/time sync data received"
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

/**
 * Parse Aqara F7 structured data (TLV format)
 * Based on zigbee-herdsman-converters buffer2DataObject
 *
 * Data format: [tag][type][value...] repeating
 * Types: 0x10=bool, 0x20=uint8, 0x21=uint16LE, 0x22=uint24LE, 0x23=uint32LE,
 *        0x28=int8, 0x29=int16LE, 0x39=floatLE, 0x3A=doubleLE
 *
 * Tags: 0x01=battery, 0x03=temperature, 0x05=RSSI, 0x06=LQI, 0x09=?, 0x0A=parent,
 *       0x0B=?, 0x0C=?, 0x0D=?, 0x11=?, 0x64=on/off, 0x65=on/off2
 *       0x95=energy(float), 0x96=voltage, 0x97=current, 0x98=power(float)
 */
private List parseAqaraF7Struct(String hexString) {
    def events = []

    if (!hexString || hexString.length() < 4) {
        log.warn "${device.displayName}: F7 struct too short: ${hexString}"
        return events
    }

    log.info "${device.displayName}: Parsing Aqara F7 struct (${hexString.length()} chars)"
    logDebug "F7 data: ${hexString}"

    try {
        // Convert hex string to list of 2-char hex values
        List<String> bytes = []
        for (int i = 0; i < hexString.length() - 1; i += 2) {
            bytes.add(hexString.substring(i, i + 2))
        }

        int idx = 0
        while (idx < bytes.size() - 1) {
            // Get tag (1 byte)
            String tagHex = bytes[idx]
            int tag = Integer.parseInt(tagHex, 16)
            idx++

            if (idx >= bytes.size()) break

            // Get data type (1 byte)
            String typeHex = bytes[idx]
            int dataType = Integer.parseInt(typeHex, 16)
            idx++

            // Determine value length based on data type (in bytes)
            int valueBytes = 0
            switch(dataType) {
                case 0x10:  // Boolean - 1 byte
                    valueBytes = 1; break
                case 0x20:  // Uint8 - 1 byte
                    valueBytes = 1; break
                case 0x21:  // Uint16LE - 2 bytes
                    valueBytes = 2; break
                case 0x22:  // Uint24LE - 3 bytes
                    valueBytes = 3; break
                case 0x23:  // Uint32LE - 4 bytes
                    valueBytes = 4; break
                case 0x25:  // Uint48LE - 6 bytes
                    valueBytes = 6; break
                case 0x28:  // Int8 - 1 byte
                    valueBytes = 1; break
                case 0x29:  // Int16LE - 2 bytes
                    valueBytes = 2; break
                case 0x2B:  // Int32LE - 4 bytes
                    valueBytes = 4; break
                case 0x39:  // FloatLE - 4 bytes
                    valueBytes = 4; break
                case 0x3A:  // DoubleLE - 8 bytes
                    valueBytes = 8; break
                default:
                    logDebug "Unknown data type: 0x${typeHex} at tag 0x${tagHex}"
                    valueBytes = 1; break
            }

            if (idx + valueBytes > bytes.size()) {
                logDebug "Not enough data for tag 0x${tagHex} type 0x${typeHex}"
                break
            }

            // Extract value bytes and join to hex string
            String valueHex = bytes[idx..<(idx + valueBytes)].join("")
            idx += valueBytes

            // Parse value based on data type
            def value = null
            switch(dataType) {
                case 0x10:  // Boolean
                    value = (bytes[idx - 1] == "01")
                    break
                case 0x20:  // Uint8
                    value = Integer.parseInt(valueHex, 16)
                    break
                case 0x21:  // Uint16LE - reverse bytes
                    value = Integer.parseInt(bytes[idx - 1] + bytes[idx - 2], 16)
                    break
                case 0x22:  // Uint24LE - reverse bytes
                    value = Integer.parseInt(bytes[idx - 1] + bytes[idx - 2] + bytes[idx - 3], 16)
                    break
                case 0x23:  // Uint32LE - reverse bytes
                    value = Long.parseLong(bytes[idx - 1] + bytes[idx - 2] + bytes[idx - 3] + bytes[idx - 4], 16)
                    break
                case 0x25:  // Uint48LE - reverse bytes
                    String reversed = ""
                    for (int b = valueBytes - 1; b >= 0; b--) {
                        reversed += bytes[idx - valueBytes + b]
                    }
                    value = Long.parseLong(reversed, 16)
                    break
                case 0x28:  // Int8
                    int v = Integer.parseInt(valueHex, 16)
                    value = (v > 127) ? (v - 256) : v
                    break
                case 0x29:  // Int16LE - reverse bytes then signed
                    int raw = Integer.parseInt(bytes[idx - 1] + bytes[idx - 2], 16)
                    value = (raw > 32767) ? (raw - 65536) : raw
                    break
                case 0x2B:  // Int32LE - reverse bytes
                    long rawL = Long.parseLong(bytes[idx - 1] + bytes[idx - 2] + bytes[idx - 3] + bytes[idx - 4], 16)
                    value = (rawL > 2147483647L) ? (rawL - 4294967296L) : rawL
                    break
                case 0x39:  // FloatLE - reverse bytes then parse IEEE 754
                    String floatBE = bytes[idx - 1] + bytes[idx - 2] + bytes[idx - 3] + bytes[idx - 4]
                    value = Float.intBitsToFloat(Integer.parseUnsignedInt(floatBE, 16))
                    break
                case 0x3A:  // DoubleLE - reverse bytes
                    String doubleBE = ""
                    for (int b = valueBytes - 1; b >= 0; b--) {
                        doubleBE += bytes[idx - valueBytes + b]
                    }
                    value = Double.longBitsToDouble(Long.parseUnsignedLong(doubleBE, 16))
                    break
                default:
                    value = Integer.parseInt(valueHex, 16)
                    break
            }

            logDebug "Tag 0x${tagHex} (${tag}), type 0x${typeHex}, raw=${valueHex}, value=${value}"

            // Process known tags
            switch(tag) {
                case 0x01:  // Battery voltage (mV)
                    logDebug "Battery voltage: ${value} mV"
                    break

                case 0x03:  // Device temperature (°C)
                    if (value != null) {
                        BigDecimal tempC = value as BigDecimal
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
                    break

                case 0x05:  // Power outage count
                    if (value != null) {
                        int count = (value as Integer)
                        if (count > 0) count -= 1
                        events << createEvent(name: "powerOutageCount", value: count)
                        logDebug "Power outage count: ${count}"
                    }
                    break

                case 0x06:  // LQI
                    logDebug "LQI: ${value}"
                    break

                case 0x09: case 0x0A: case 0x0B: case 0x0C: case 0x0D: case 0x11:
                    logDebug "Tag 0x${tagHex}: ${value}"
                    break

                case 0x64:  // On/Off state (100 decimal)
                    if (value != null) {
                        def switchState = (value == 1 || value == true) ? "on" : "off"
                        events << createEvent(name: "switch", value: switchState)
                        logInfo "Switch: ${switchState}"
                    }
                    break

                case 0x65:  // On/Off state 2 (101 decimal)
                    logDebug "Switch 2: ${value == 1 || value == true ? 'on' : 'off'}"
                    break

                case 0x95:  // Energy consumption (float, Wh)
                    if (value != null) {
                        BigDecimal energy = (value as BigDecimal) / 1000.0  // Wh to kWh
                        energy = ((energy * 1000).toLong()) / 1000.0
                        events << createEvent(name: "energy", value: energy, unit: "kWh")
                        logInfo "Energy: ${energy} kWh"
                    }
                    break

                case 0x96:  // Voltage (float in decivolts)
                    if (value != null) {
                        BigDecimal voltage = (value as BigDecimal) / 10.0
                        voltage = ((voltage * 10).toLong()) / 10.0
                        events << createEvent(name: "voltage", value: voltage, unit: "V")
                        logInfo "Voltage: ${voltage} V"
                    }
                    break

                case 0x97:  // Current (float in mA or A)
                    if (value != null) {
                        BigDecimal current = value as BigDecimal
                        if (dataType != 0x39) current = current / 1000.0  // mA to A for integers
                        current = ((current * 1000).toLong()) / 1000.0
                        events << createEvent(name: "amperage", value: current, unit: "A")
                        logInfo "Current: ${current} A"
                    }
                    break

                case 0x98:  // Power (float, watts)
                    if (value != null) {
                        BigDecimal power = value as BigDecimal
                        power = ((power * 10).toLong()) / 10.0
                        events << createEvent(name: "power", value: power, unit: "W")
                        logInfo "Power: ${power} W"
                    }
                    break

                case 0x9A:  // Status byte
                    logDebug "Status 0x9A: ${value}"
                    break

                default:
                    logDebug "Unknown tag: 0x${String.format('%02X', tag)} = ${value}"
                    break
            }
        }
    } catch (e) {
        log.error "${device.displayName}: Error parsing F7 struct: ${e.message}", e
    }

    return events
}

private List handleCatchall(Map descMap) {
    def events = []

    String clusterId = (descMap.cluster ?: descMap.clusterId)?.toUpperCase()
    String command = descMap.command

    logDebug "Catchall: cluster=${clusterId}, cmd=${command}, data=${descMap.data}"

    // Handle On/Off cluster responses
    if (clusterId == "0006") {
        if (command == "0B" && descMap.data?.size() > 0) {
            // Default response - check the command that was responded to
            def cmd = descMap.data[0]
            if (cmd == "01") {
                events << createEvent(name: "switch", value: "on")
                logInfo "Switch: on (from response)"
            } else if (cmd == "00") {
                events << createEvent(name: "switch", value: "off")
                logInfo "Switch: off (from response)"
            }
        } else if (command == "01" && descMap.data?.size() >= 3) {
            // Read attribute response
            // Data format: [attrIdLo, attrIdHi, status, dataType, value...]
            try {
                if (descMap.data.size() >= 5 && descMap.data[2] == "00") {
                    // Success status
                    def switchValue = descMap.data[4] == "01" ? "on" : "off"
                    events << createEvent(name: "switch", value: switchValue)
                    logInfo "Switch: ${switchValue}"
                }
            } catch (e) {
                logDebug "Error parsing on/off catchall: ${e.message}"
            }
        }
    }

    // Handle Electrical Measurement cluster
    if (clusterId == "0B04") {
        logDebug "Electrical measurement catchall: cmd=${command}, data=${descMap.data}"
        // These are typically responses to read requests
    }

    // Handle Metering cluster
    if (clusterId == "0702") {
        logDebug "Metering catchall: cmd=${command}, data=${descMap.data}"
    }

    // Handle Lumi cluster catchall with attribute data
    if (clusterId == "FCC0") {
        logDebug "Lumi catchall: command=${command}, data=${descMap.data}"

        // Check if this is an attribute report with data
        if (descMap.data && descMap.data.size() >= 4) {
            // Try to extract attribute ID and value from catchall data
            // Format may be: [attrIdLo, attrIdHi, dataType, ...value...]
            try {
                int attrIdLo = Integer.parseInt(descMap.data[0], 16)
                int attrIdHi = Integer.parseInt(descMap.data[1], 16)
                int attrId = (attrIdHi << 8) | attrIdLo

                logDebug "Lumi catchall attr: 0x${String.format('%04X', attrId)}"

                if (attrId == 0x00F7) {
                    // Build the hex string from remaining data bytes
                    String hexData = descMap.data.drop(3).join("")
                    if (hexData) {
                        logDebug "Catchall F7 data: ${hexData}"
                        events += parseAqaraF7Struct(hexData)
                    }
                }
            } catch (e) {
                logDebug "Error parsing Lumi catchall: ${e.message}"
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
    } else if (hex.length() == 2 && value > 127) {
        value -= 256
    }
    return value
}

private int hexToSignedInt16LE(String hex) {
    if (!hex || hex.length() != 4) return 0
    // Little-endian: swap bytes
    int low = Integer.parseInt(hex.substring(0, 2), 16)
    int high = Integer.parseInt(hex.substring(2, 4), 16)
    int value = (high << 8) | low
    if (value > 32767) value -= 65536
    return value
}

private int littleEndianHexToInt(String hex) {
    if (!hex || hex.length() < 2) return 0
    int result = 0
    int byteIndex = 0
    for (int i = 0; i < hex.length(); i += 2) {
        int byteVal = Integer.parseInt(hex.substring(i, i + 2), 16)
        result |= (byteVal << (byteIndex * 8))
        byteIndex++
    }
    return result
}

private long littleEndianHexToLong(String hex) {
    if (!hex || hex.length() < 2) return 0L
    long result = 0L
    int byteIndex = 0
    for (int i = 0; i < hex.length(); i += 2) {
        long byteVal = Long.parseLong(hex.substring(i, i + 2), 16)
        result |= (byteVal << (byteIndex * 8))
        byteIndex++
    }
    return result
}

private float littleEndianHexToFloat(String hex) {
    if (!hex || hex.length() != 8) return 0.0f
    // Swap to big-endian
    String be = hex.substring(6, 8) + hex.substring(4, 6) + hex.substring(2, 4) + hex.substring(0, 2)
    return Float.intBitsToFloat(Integer.parseUnsignedInt(be, 16))
}

private double littleEndianHexToDouble(String hex) {
    if (!hex || hex.length() != 16) return 0.0d
    // Swap to big-endian (8 bytes = 16 hex chars)
    String be = hex.substring(14, 16) + hex.substring(12, 14) + hex.substring(10, 12) + hex.substring(8, 10) +
                hex.substring(6, 8) + hex.substring(4, 6) + hex.substring(2, 4) + hex.substring(0, 2)
    return Double.longBitsToDouble(Long.parseUnsignedLong(be, 16))
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
