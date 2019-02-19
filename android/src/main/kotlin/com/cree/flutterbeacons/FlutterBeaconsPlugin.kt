package com.cree.flutterbeacons

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

import android.content.Context
import android.util.Log
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import org.json.JSONObject
import java.util.UUID
import java.nio.ByteBuffer
import android.bluetooth.le.ScanSettings.Builder

class IBeacon(val uuid: String,
              val major: Int,
              val minor: Int,
              val powerLevel: Int,
              val rssi: Int) {

    companion object {

        fun toHex(ba: ByteArray) : String {
            var s: String = ""
            for (b in ba) {
                val st = String.format("%02X ", b)
                s += st
            }
            return s
        }

        fun parse(ba: ByteArray, rssi: Int) : IBeacon? {

            if ( ba.size < 26 ) {
                Log.e(TAG, "incorrect size")
                Log.e(TAG, toHex(ba))
                return null
            }

            var buf: ByteBuffer = ByteBuffer.wrap(ba)
            // Android generated:
            // 1AFF4C000215ABABABABABABABABABABABABABABABAB00FF00FFBF0000000000000000000000000000000000000000000000000000000000000000000000
            // Tag generated:
            // 02 01 06 1A FF 4C 00 02 15 42 6C 75 65 43 68 61 72 6D 42 65 61 63 6F 6E 73 0E FE 13 55 C0 13 09 70 42 65 61 63 6F 6E 5F 72 6F 6E 31 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00


            var len_byte = buf.get(0).toInt()
            var type_byte = buf.get(1).toInt()
            var value_byte = buf.get(2).toInt()

            var offset: Int = 0

            if ( len_byte == 0x02 && type_byte == 0x01 && value_byte == 0x06 ) {
                offset = 3
            }

            var pa: Int = buf.getShort(offset+0).toInt()
            if ( pa != 0x1AFF ) {
                // Log.e(TAG, "incorrect preamble A||0x1AFF: " + pa.toString())
                // Log.e(TAG, toHex(ba))
                return null
            }
            pa = buf.getShort(offset+2).toInt()
            if ( pa != 0x4C00 ) {
                // Log.e(TAG, "incorrect preamble B||0x4C00: " + pa.toString())
                // Log.e(TAG, toHex(ba))
                return null
            }
            pa = buf.getShort(offset+4).toInt()
            if ( pa != 0x0215 ) {
                // Log.e(TAG, "incorrect preamble C||0x0215: " + pa.toString())
                // Log.e(TAG, toHex(ba))
                return null
            }

            var uuid: UUID = UUID(buf.getLong(offset+6),buf.getLong(offset+14))
            return IBeacon(uuid.toString(), buf.getShort(offset+22).toInt(), buf.getShort(offset+24).toInt(), buf.get(offset+26).toInt(), rssi)
        }
    }

    fun toJson(): JSONObject {
        var j = JSONObject()
        j.put("uuid", uuid)
        j.put("major", major)
        j.put("minor", minor)
        j.put("powerLevel", powerLevel)
        j.put("rssi", rssi)
        return j
    }
}

class IBeaconSetting(val uuid: String,
                     val major: Int,
                     val minor: Int,
                     val powerLevel: Int,
                     val beaconId: String) {

    fun toJson(): JSONObject {
        var j = JSONObject()
        j.put("uuid", uuid)
        j.put("major", major)
        j.put("minor", minor)
        j.put("powerLevel", powerLevel)
        j.put("beaconId", beaconId)
        return j
    }
}

const val TAG = "FlutterBeacons"

typealias ResultCallback = (Boolean) -> Unit

typealias ScanResultCallback = (ScanResult) -> Unit

class BLEEncap(private val context: Context) {

    fun getBleAdapter() : BluetoothAdapter? {
        var bluetoothManager : BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if ( bluetoothManager == null ) {
            Log.e(TAG, "No BLE Manager found")
            return null
        }

        var adapter: BluetoothAdapter? = bluetoothManager.getAdapter()

        if ( adapter == null ) {
            Log.e(TAG, "No BLE Adapter found")
            return null
        }

        return adapter;
    }

    fun getAdvertiser() : BluetoothLeAdvertiser? {

        val adapter : BluetoothAdapter? = getBleAdapter()
        if ( adapter == null ) {
            return null
        }

        if ( !adapter.isEnabled ) {
            Log.e(TAG, "Bluetooth Adapter not enabled.")
            return null;
        }

        var adv: BluetoothLeAdvertiser? = adapter.getBluetoothLeAdvertiser()

        if ( adv ==  null ) {
            Log.e(TAG, "No BLE Advertiser found")
            return null
        }

        return adv
    }


    fun getScanner() : BluetoothLeScanner? {

        val adapter : BluetoothAdapter? = getBleAdapter()
        if ( adapter == null ) {
            return null
        }

        if ( !adapter.isEnabled ) {
            Log.e(TAG, "Bluetooth Adapter not enabled.")
            return null;
        }

        var scanner: BluetoothLeScanner? = adapter.getBluetoothLeScanner()

        if ( scanner ==  null ) {
            Log.e(TAG, "No BLE Scanner found")
            return null
        }

        return scanner
    }

}

class IBeacons(private val ble: BLEEncap, private val settings: IBeaconSetting){

    class BeaconCallback(private val cbFun: ResultCallback) : AdvertiseCallback () {

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            cbFun(true)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "onStartFailure $errorCode")
            cbFun(false)
        }
    }

    var beaconCallback: BeaconCallback? = null

    private fun asBytes(uuid: UUID): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }

    fun start(cb: ResultCallback) {

        if ( beaconCallback != null ) {
            stop()
        }

        beaconCallback = BeaconCallback(cb)

        var advertisementBuilder: AdvertiseSettings.Builder = AdvertiseSettings.Builder()
                .setAdvertiseMode( AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY )
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable( false )
                .setTimeout(0)


        var mManufacturerData : ByteBuffer = ByteBuffer.allocate(23)
        mManufacturerData.put(0, 0x02.toByte()) // Beacon Identifier
        mManufacturerData.put(1, 0x15.toByte()) // Beacon Identifier

        try {
            val uuid: UUID = UUID.fromString(settings.uuid)
            val uuidData = asBytes(uuid)
            for (i in 0..15) {
                mManufacturerData.put(i+2, uuidData[i]) // adding the UUID
            }
        } catch ( e: Exception ) {
            Log.e(TAG, "UUID format invalid. " + e.toString())
        }

        mManufacturerData.put(18, settings.major.shr(8).toByte()) // first byte of Major
        mManufacturerData.put(19, settings.major.and(0xff).toByte()) // second byte of Major
        mManufacturerData.put(20, settings.minor.shr(8).toByte()) // first minor
        mManufacturerData.put(21, settings.minor.and(0xff).toByte()) // second minor
        mManufacturerData.put(22, settings.powerLevel.toByte()) // txPower

        var dataBuilder = AdvertiseData.Builder()

                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel( false )
                .addManufacturerData(0x004c, mManufacturerData.array())


        Log.d(TAG, mManufacturerData.toString())

        var bluetoothLeAdvertiser : BluetoothLeAdvertiser? = ble.getAdvertiser()
        if (bluetoothLeAdvertiser == null ) {
            cb(false)
            return
        }

        bluetoothLeAdvertiser.startAdvertising( advertisementBuilder.build(), dataBuilder.build(), beaconCallback)
        // don't call cb, this will happen in beaconCallback.
    }

    fun stop() : Boolean {
        var bluetoothLeAdvertiser : BluetoothLeAdvertiser? = ble.getAdvertiser()
        if (bluetoothLeAdvertiser == null ) {
            return false
        }
        bluetoothLeAdvertiser.stopAdvertising(beaconCallback)
        beaconCallback = null
        return true
    }
}

class FlutterBeaconsPlugin(private val context: Context) : MethodCallHandler, EventChannel.StreamHandler {

    private var beacons : MutableMap<String, IBeacons> = mutableMapOf<String, IBeacons>()
    private var ble: BLEEncap = BLEEncap(context)
    private var eventSink: EventChannel.EventSink? = null

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_beacons")
            val eventChannel = EventChannel(registrar.messenger(), "flutter_beacons/listen")
            val instance = FlutterBeaconsPlugin(registrar.context())
            channel.setMethodCallHandler(instance)
            eventChannel.setStreamHandler(instance)
        }
    }

    override fun onListen(arguments: Any?, sink: EventChannel.EventSink ) {
        eventSink = sink
    }
    override fun onCancel(arguments: Any? ) {
        eventSink = null
    }

    class PScanCallback(private val cbFun: ScanResultCallback) : ScanCallback () {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            cbFun(result)
        }

        // override fun onScanFailed(errorCode: Int) {
        // }

    }
    private var scanCallback: PScanCallback? = null

    private fun startListen() : Boolean {
        // Log.e(TAG, "startListen")
        var scanner = ble.getScanner()
        if (scanner == null) {
            return false
        }
        if (scanCallback != null ) {
            return false
        }
        scanCallback = PScanCallback{

            var ib = IBeacon.parse(it.scanRecord.bytes, it.rssi)
            if ( ib != null ) {
                // Log.e(TAG, "received and passed along")
                eventSink?.success(ib.toJson().toString())
            }
        }
        var builder: ScanSettings.Builder = ScanSettings.Builder()
        builder.setReportDelay(0)
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        scanner.startScan(null, builder.build(), scanCallback)
        return true
    }

    private fun stopListen() : Boolean {
        // Log.e(TAG, "stopListen")
        if ( scanCallback == null ) {
            return false
        }
        var scanner = ble.getScanner()
        if (scanner == null) {
            return false
        }
        scanner.stopScan(scanCallback)
        scanCallback = null
        return true
    }

    private fun startBeacon( settings: IBeaconSetting, cb: ResultCallback ) {
    if ( beacons[settings.beaconId] != null ) {
      beacons[settings.beaconId]!!.stop()
    }
    val beacon = IBeacons(ble, settings)
    beacons[settings.beaconId] = beacon
    beacon.start(cb)
  }

  private fun stopBeacon( beaconId: String ) : Boolean {
    if ( beacons[beaconId] != null ) {
        beacons[beaconId]!!.stop()
        beacons.remove(beaconId)
    }
    return true
  }

  private fun adapterEnabled() : Boolean {
      val adapter : BluetoothAdapter? = ble.getBleAdapter()
      if ( adapter == null ) {
          return false
      }
      return adapter.isEnabled
  }


  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else if (call.method == "startBeacon") {

      try {
          val map: HashMap<String, Any> = call.arguments<HashMap<String, Any>>()
          val uuid: String = map["uuid"] as String
          val major: Int = map["major"] as Int
          val minor: Int = map["minor"] as Int
          val powerLevel: Int = map["powerLevel"] as Int
          val beaconId: String = map["beaconId"] as String
          val settings = IBeaconSetting(uuid, major, minor, powerLevel, beaconId)
          startBeacon(settings){
              result.success(it)
          }
          return
      } catch (e: Exception) {
          result.error(e.toString(), null, null)
      }

    } else if (call.method == "stopBeacon") {

      val beaconId : String? = call.argument<String>("beaconId")
      if ( beaconId == null ) {
        result.error("beaconId may not be null.", null, null)
        return
      }
      result.success(stopBeacon(beaconId) )

    } else if (call.method == "adapterEnabled") {

        result.success(adapterEnabled())

    } else if (call.method == "startListen") {

        result.success(startListen())

    } else if (call.method == "stopListen") {

        result.success(stopListen())

    } else {

      result.notImplemented()
    }
  }
}
