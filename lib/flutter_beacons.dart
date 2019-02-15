import 'dart:async';
import 'dart:convert';
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
  IBeaconSetting.fromMap(String json) {
    Map<String, dynamic> o = jsonDecode(json);
    beaconId = o["beaconId"];
    uuid = o["uuid"];
    major = o["major"];
    minor = o["minor"];
    powerLevel = o["powerLevel"];
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

  String toString() {
    return "IBeaconSetting [uuid=$uuid, major=$major, minor=$minor, powerLevel=$powerLevel, beaconId=$beaconId]";
  }
}

class FlutterBeacons {
  static const MethodChannel _channel = const MethodChannel('flutter_beacons');
  static const EventChannel _eventChannel =
      const EventChannel('flutter_beacons/listen');
  static Stream<IBeaconSetting> _eventStream;

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> startListen() async {
    return await _channel.invokeMethod("startListen");
  }

  static Future<bool> stopListen() async {
    return await _channel.invokeMethod("stopListen");
  }

  static Stream<IBeaconSetting> get getBeaconStream {
    if (_eventStream == null) {
      _eventStream =
          _eventChannel.receiveBroadcastStream().map<IBeaconSetting>((json) {
        return IBeaconSetting.fromMap(json);
      });
    }
    return _eventStream;
  }

  static Future<bool> startBeacon(IBeaconSetting settings) async {
    final Map<String, dynamic> params = settings.map();
    return await _channel.invokeMethod('startBeacon', params);
  }

  static Future<bool> stopBeacon(String beaconId) async {
    final Map<String, dynamic> params = <String, dynamic>{'beaconId': beaconId};
    return await _channel.invokeMethod('stopBeacon', params);
  }

  static Future<bool> adapterEnabled() async {
    return await _channel.invokeMethod('adapterEnabled');
  }
}
