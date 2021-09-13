package au.com.saphi.plugins.crosstrace

import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.util.Log
import io.runtime.mcumgr.McuManager
import io.runtime.mcumgr.McuMgrCallback
import io.runtime.mcumgr.ble.McuMgrBleTransport
import io.runtime.mcumgr.managers.DefaultManager
import io.runtime.mcumgr.response.dflt.McuMgrEchoResponse

enum class LightPattern {
    WhiteGreen, // 1, three white+green flashes x 3 periods or
    WhiteRed,   // 2, white flash + longer red flash x 3 periods
    Green,      // 3, green flashes 3 x 3 periods
    Red,        // 4, red flashes (-.-.) x 3
}

enum class Device {
    Info,
    CurrentDateTime,
    ClearKeys,
    CompressFlash,
    DownloadTeks,
    DownloadRpis,
    DownloadEncounters
}

enum class KeyType {
    Tek,
    Rpi,
    Encounter
}

class Crosstrace {
    val SMP_SERVICE = McuMgrBleTransport.SMP_SERVICE_UUID // To be used as a filter before commencing connection
    private lateinit var bleTransport: McuMgrBleTransport
    private lateinit var defaultManager: DefaultManager
    private lateinit var packet: ByteArray

    /**
     * Initiatiates connection to a xt-device/tag necessary for communication. */
    fun initializeConnection(deviceName: String, activity: Activity, context: Context) {
        val bluetoothManager: BluetoothManager? = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val connectedDevices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) as List<BluetoothDevice>

        connectedDevices?.forEach { device ->
            if (device.name == deviceName) {
                this.bleTransport = McuMgrBleTransport(context, device)
                this.defaultManager = DefaultManager(this.bleTransport)
                Log.d("Android - Connected to:", device.name)
            }
        }
    }

    /**
     * Builds a specific packet depending on the command */
    fun set(command: Device) { // set command
        when (command) {
            Device.ClearKeys ->
                this.buildPacket(88, 3, 2)

            Device.CompressFlash ->
                this.buildPacket(88, 7, 2)

            Device.CurrentDateTime ->
                this.buildPacket(0, 4, 0)

            Device.DownloadTeks ->
                this.buildPacket(88, 8, 0)

            Device.DownloadRpis ->
                this.buildPacket(88, 9, 0)

            Device.DownloadEncounters ->
                this.buildPacket(88, 2, 0)

            Device.Info ->
                this.buildPacket(88, 0, 0)
        }
    }

    /**
     * Sets the type of keys to retrieve. Tek, Rpi, or Encounter keys.
     * startTime: <unix timestamp>
     * maxDownload: <Integer> */
    fun set(type: KeyType, startTime: Int, maxDownload: Int) { // set key type to download
        var id: Int = when (type) {
            KeyType.Tek -> 8
            KeyType.Rpi -> 9
            KeyType.Encounter -> 2
        }

        this.buildPacket(88, id, 0, mapOf("sts" to startTime, "mx" to maxDownload))
    }

    /**
     * Sets the time of the xt-device/tag.
     * date: <string> eg. "2021-09-08T06:50:40.737715" */
    fun set(date: String) { // set time
        this.buildPacket(0, 4, 2, mapOf("datetime" to date))
    }

    /**
     * Requests Real-time tag data to be retrieved
     * secondsUpdatedAgo: <max age, in seconds. Only return tags updated in the last x seconds. Default 30s>
     * role: <1 or 2. role filter, bitfield, only return roles included in mask> */
    fun setRealtimeTagData(secondsUpdatedAgo: Int = 30, role: Int = 1) { // set real time
        this.buildPacket(88, 4, 0, mapOf("mx_age" to secondsUpdatedAgo, "role_filt" to role))
    }

    /**
     * Sets LED flash patterns to xt-tag/device currently connected to */
    fun setLocalLED(pattern: LightPattern = LightPattern.Red) { // set real time
        this.buildPacket(88, 5, 2, mapOf("pattern" to getPatternNum(pattern)))
    }

    /**
     * Sets LED flash patterns to a xt-tag/device in which the tagId is specified */
    fun setRemoteLED(tagId: Long, pattern: LightPattern = LightPattern.WhiteGreen) { // set ...
        this.buildPacket(88, 6, 2, mapOf("euid" to tagId, "pattern" to getPatternNum(pattern)))
    }

    /**
     * Retrieve device responses after sending a built packet. buildPacket() function needs to be called before using this. */
    fun retrieve(callback: McuMgrCallback<McuMgrEchoResponse>) {
        if (this::defaultManager.isInitialized && this::packet.isInitialized) {
            this.defaultManager.send(this.packet, McuMgrEchoResponse::class.java, callback)
        }
    }

    /**
     * Echoes a response from a device without the need for a built packet. */
    fun echo(message: String, callback: McuMgrCallback<McuMgrEchoResponse>) {
        if (this::defaultManager.isInitialized) {
            this.defaultManager.echo(message, callback)
        }
    }

    /**
     * Builds header packet that is sent to device for communication necessary for receiving a response */
    private fun buildPacket(nh_group: Int, nh_id: Int, nh_op_val: Int, cborMap: Map<String, Any>? = null) {
        Log.d("PACKET -- ", "UPDATING")
        if (this::bleTransport.isInitialized) {
            this.packet = McuManager.buildPacket(this.bleTransport.scheme, nh_op_val, 0, nh_group, 0, nh_id, cborMap) as ByteArray
        }
    }

    /**
     * Returns numbers 1 -> 4 depending on the type of LightPattern specified */
    private fun getPatternNum(pattern: LightPattern): Int {
        return when (pattern) {
            LightPattern.WhiteGreen -> 1
            LightPattern.WhiteRed -> 2
            LightPattern.Green -> 3
            else -> 4
        }
    }
}