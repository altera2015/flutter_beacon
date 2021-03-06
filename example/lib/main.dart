import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_beacons/flutter_beacons.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  bool _beaconRunning = false;
  IBeaconSetting _beaconSettings = IBeaconSetting("com.cree.example");

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion = await FlutterBeacons.platformVersion;
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });

    FlutterBeacons.getBeaconStream.listen((IBeacon d) {
      print(d);
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(children: [
          Text('Running on: $_platformVersion\n'),
          RaisedButton(
              onPressed: () async {
                if (!(await FlutterBeacons.adapterEnabled())) {
                  print("Adapter not enabled.");
                  return;
                }

                if (_beaconRunning) {
                  FlutterBeacons.stopBeacon(_beaconSettings.beaconId);
                  setState(() {
                    _beaconRunning = false;
                  });
                } else {
                  bool result =
                      await FlutterBeacons.startBeacon(_beaconSettings);
                  print("Enabled $result");
                  setState(() {
                    _beaconRunning = result;
                  });
                }
              },
              child: Text(_beaconRunning ? "Stop Beacon" : "Start Beacon")),
          RaisedButton(
              onPressed: () async {
                FlutterBeacons.startListen();
              },
              child: Text("Start Listen")),
          RaisedButton(
              onPressed: () async {
                FlutterBeacons.stopListen();
              },
              child: Text("Stop Listen"))
        ]),
      ),
    );
  }
}
