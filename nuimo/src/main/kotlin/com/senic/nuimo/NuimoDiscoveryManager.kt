/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import java.util.ArrayList

//TODO: Android requires the search to be stopped before connecting to any device. That requirement should be handled transparently by this library!
class NuimoDiscoveryManager(context: Context){
    private val context = context
    private val bluetoothManager: BluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private val scanCallback = ScanCallback()
    private val discoveryListeners = ArrayList<NuimoDiscoveryListener>()

    public fun addDiscoveryListener(discoveryListener: NuimoDiscoveryListener) {
        discoveryListeners.add(discoveryListener)
    }

    public fun removeDiscoveryListener(discoveryListener: NuimoDiscoveryListener) {
        discoveryListeners.remove(discoveryListener)
    }

    public fun startDiscovery() {
        //TODO: We should pass a service UUID filter to only search devices with Nuimo's service UUIDs but then no devices are found on Samsung S3.
        bluetoothAdapter.startLeScan(/*NUIMO_SERVICE_UUIDS,*/ scanCallback)
        println("Nuimo discovery started")
    }

    public fun stopDiscovery() {
        bluetoothAdapter.stopLeScan(scanCallback)
        println("Nuimo discovery stopped")
    }

    private inner class ScanCallback: BluetoothAdapter.LeScanCallback {
        //TODO: This might help: https://github.com/movisens/SmartGattLib/tree/master/src/main/java/com/movisens/smartgattlib

        override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
            println("Device found " + device.address + ", " + device.name)
            if (device.name != "Nuimo") { return }
            discoveryListeners.forEach { it.onDiscoverNuimoController(NuimoBluetoothController(device, context)) }
        }
    }
}

public interface NuimoDiscoveryListener {
    fun onDiscoverNuimoController(nuimoController: NuimoController)
}
