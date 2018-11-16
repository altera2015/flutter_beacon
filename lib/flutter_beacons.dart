import 'dart:async';

import 'package:flutter/services.dart';

class IBeaconSetting {
  String uuid;
  int major;
  int minor;
  int powerLevel;
  String beaconId;

  IBeaconSetting(this.beaconId) {
    major = 0x0001;
    minor = 0x0001;
    powerLevel = 0xbe;
    uuid = "eb6349c9-8911-4e75-8330-e7ef960b261f";
  }

  Map<String, dynamic> map() {
    return <String, dynamic>{
      'uuid': uuid,
      'major': major,
      'minor': minor,
      'powerLevel': powerLevel,
      'beaconId': beaconId
    };
  }
}

class FlutterBeacons {
  static const MethodChannel _channel =
      const MethodChannel('flutter_beacons');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> startBeacon(IBeaconSetting settings) async {

    final Map<String, dynamic> params = settings.map();
    return await _channel.invokeMethod('startBeacon', params);
  }

  static Future<bool> stopBeacon(String beaconId) async {

    final Map<String, dynamic> params =  <String, dynamic>{
      'beaconId': beaconId
    };
    return await _channel.invokeMethod('stopBeacon', params);
  }

  static Future<bool> adapterEnabled() async {
    return await _channel.invokeMethod('adapterEnabled');
  }
}
