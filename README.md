# flutter_beacons

Flutter BLE Beacon generator

## Getting Started

Android backend only implemented at this point.

## Usage

### Check if BLE adapter is enabled.

```dart
var enabled = await FlutterBeacons.adapterEnabled()
print("Adapter is $enabled")
```

### Start iBeacon broadcast

```dart

// keep the ID unique, this is not transmitted but used
// to stop the beacon when requested.

IBeaconSetting beaconSettings = IBeaconSetting("com.cree.example");
beaconSettings.major = 0x0001;
beaconSettings.minor = 0x0001;
beaconSettings.powerLevel = 0xbe;
beaconSettings.uuid = "eb6349c9-8911-4e75-8330-e7ef960b261f";

bool result = await FlutterBeacons.startBeacon(_beaconSettings);
print("BLE Advertisement start: $result");
```

### Stop BLE advertisement
```dart
bool result = await FlutterBeacons.stopBeacon(_beaconSettings.beaconId);
print("BLE Advertisement stop: $result");
```
