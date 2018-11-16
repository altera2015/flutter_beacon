package com.cree.flutterbeacons

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

import android.content.Context
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseCallback
import android.util.Log
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import java.util.UUID
import java.nio.ByteBuffer


class IBeaconSetting(val uuid: String,
                     val major: Int,
                     val minor: Int,
                     val powerLevel: Int,
                     val beaconId: String)

const val TAG = "FlutterBeacons"

typealias ResultCallback = (Boolean) -> Unit


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

class FlutterBeaconsPlugin(private val context: Context) : MethodCallHandler {

  private var beacons : MutableMap<String, IBeacons> = mutableMapOf<String, IBeacons>()
  private var ble: BLEEncap = BLEEncap(context)

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "flutter_beacons")
      channel.setMethodCallHandler(FlutterBeaconsPlugin(registrar.context()))
    }
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
          return false;
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

    } else {
      result.notImplemented()
    }
  }
}
