# Hubitat Drivers and Apps

Custom Hubitat Elevation drivers and apps for smart home automation.

## Contents

### Drivers

#### Sonoff TRVZB
**Location:** `Drivers/Sonoff/zigbee-sonoff-trvzb.groovy`

A comprehensive driver for the SONOFF TRVZB Thermostatic Radiator Valve.

**Features:**
- Full thermostat control (temperature, setpoint, mode)
- Child lock control
- Window open detection
- Frost protection
- Valve position monitoring and control
- Temperature calibration
- External temperature sensor support
- Battery monitoring
- Robust error handling and auto-recovery

**Supported Models:** SONOFF TRVZB

---

#### Aqara H1 EU Single Switch
**Location:** `Drivers/Aqara/aqara_h1_eu_single_switch.groovy`

Simple on/off driver for Aqara H1 EU Single Switch.

**Features:**
- On/Off/Toggle control
- Health check monitoring
- State refresh and verification

**Supported Models:** lumi.switch.l1aeu1 / WS-EUK01

---

#### Zigbee Device Discovery Tool
**Location:** `Drivers/zigbee-device-discovery.groovy`

A diagnostic driver to discover Zigbee device capabilities.

**Features:**
- Discovers all endpoints, clusters, and attributes
- Helps create custom drivers for unsupported devices
- Generates fingerprints for device matching

---

### Apps

#### Master Thermostat Controller
**Location:** `Master Thermostat App/`

Unified heating control system for multiple TRVs.

**Components:**
- `hubitat-master-thermostat-parent.groovy` - Parent app for centralized control
- `hubitat-master-thermostat-child.groovy` - Room Zone child app
- `hubitat-virtual-master-thermostat.groovy` - Virtual thermostat driver

**Features:**
- Master virtual thermostat for dashboard/voice control
- Per-room temperature offsets
- Weekday/Weekend scheduling with multiple time slots
- Manual override with auto-revert on schedule change
- Window detection integration
- Child lock control across all TRVs
- Temperature range monitoring from multiple sensors

**Installation Order:**
1. Install "Virtual Master Thermostat" driver
2. Install "Master Thermostat Controller" parent app
3. Install "Room Zone" child app
4. Add app instance and configure

---

## Project Structure

```
hubitat/
├── Drivers/
│   ├── Aqara/
│   │   └── aqara_h1_eu_single_switch.groovy
│   ├── Sonoff/
│   │   └── zigbee-sonoff-trvzb.groovy
│   └── zigbee-device-discovery.groovy
├── Master Thermostat App/
│   ├── hubitat-master-thermostat-parent.groovy
│   ├── hubitat-master-thermostat-child.groovy
│   └── hubitat-virtual-master-thermostat.groovy
├── .gitignore
├── LICENSE
└── README.md
```

## Installation

### Drivers
1. In Hubitat, go to **Drivers Code**
2. Click **New Driver**
3. Paste the driver code
4. Click **Save**
5. Assign the driver to your device

### Apps
1. In Hubitat, go to **Apps Code**
2. Click **New App**
3. Paste the app code
4. Click **Save**
5. Go to **Apps** > **Add User App**

## Requirements

- Hubitat Elevation hub
- Compatible Zigbee devices

## Credits

**Author:** Ben Fayershtain
**Namespace:** benberlin

### References & Resources

The following resources were used as references during development:

#### Zigbee Protocol & Clusters
- [Zigbee Cluster Library (ZCL) Specification](https://zigbeealliance.org/developer_resources/zigbee-cluster-library/) - Official Zigbee cluster definitions
- [Zigbee2MQTT Device Documentation](https://www.zigbee2mqtt.io/devices/) - Device-specific cluster and attribute information

#### Sonoff TRVZB
- [Zigbee2MQTT - Sonoff TRVZB](https://www.zigbee2mqtt.io/devices/TRVZB.html) - Device specifications and custom cluster (FC11) documentation
- [Koenkk/zigbee-herdsman-converters](https://github.com/Koenkk/zigbee-herdsman-converters) - Converter implementations and attribute mappings

#### Aqara Devices
- [Zigbee2MQTT - Aqara Switches](https://www.zigbee2mqtt.io/devices/WS-EUK01.html) - Aqara H1 EU switch documentation
- Hubitat Generic Zigbee Switch driver - Base implementation reference

#### Hubitat Development
- [Hubitat Developer Documentation](https://docs2.hubitat.com/en/developer) - Official Hubitat driver and app development guides
- [Hubitat Community Forums](https://community.hubitat.com/) - Community driver examples and troubleshooting

#### AI Assistance
- Development assisted by [Claude](https://claude.ai) (Anthropic) - Code generation and documentation

## License

MIT License

Copyright (c) 2024 Ben Fayershtain

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and feature requests, please use the GitHub Issues page.
