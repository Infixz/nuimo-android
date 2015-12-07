/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import java.util.ArrayList

//TODO: Android requires the search to be stopped before connecting to any device. That requirement should be handled transparently by this library!
class NuimoDiscoveryManager(context: Context){
    companion object {
        const val PERMISSIONS_REQUEST_CODE = 235
    }

    private val context = context
    private val bluetoothManager: BluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private val scanCallback = ScanCallback()
    private val discoveryListeners = ArrayList<NuimoDiscoveryListener>()
    private var shouldStartDiscoveryWhenPermissionsGranted = false

    public fun addDiscoveryListener(discoveryListener: NuimoDiscoveryListener) {
        discoveryListeners.add(discoveryListener)
    }

    public fun removeDiscoveryListener(discoveryListener: NuimoDiscoveryListener) {
        discoveryListeners.remove(discoveryListener)
    }

    public fun startDiscovery(): Boolean {
        shouldStartDiscoveryWhenPermissionsGranted = true
        if (!checkPermissions(context as? Activity)) { return false }
        if (!checkBluetoothEnabled()) { return false }

        //TODO: We should pass a service UUID filter to only search devices with Nuimo's service UUIDs but then no devices are found on Samsung S3.
        bluetoothAdapter.startLeScan(/*NUIMO_SERVICE_UUIDS,*/ scanCallback)
        println("Nuimo discovery started")

        return true
    }

    public fun stopDiscovery() {
        bluetoothAdapter.stopLeScan(scanCallback)
        shouldStartDiscoveryWhenPermissionsGranted = false
        println("Nuimo discovery stopped")
    }

    /**
     * This method is needed only if you target Android SDK >= 23. It checks if the user has granted
     * required permissions. If not granted, the user will be asked to grant them. If you already
     * provided an Activity instance to the constructor of NuimoDiscoveryManager for the argument
     * "context" there's no need for you to call this method. In this case it will be called
     * automatically by {@see NuimoDiscoveryManager#startDiscovery()} to ask the user to grant
     * permissions. If you didn't provide an Activity instance to the constructor you'll need to
     * call this method manually and pass an Activity instance before calling startDiscovery().
     * Otherwise startDiscover() will not be able to discover Nuimo controllers on Android 6.0 and
     * above.
     * If targeting Android SDK >= 23 you always need to your Activity class (whose instance is
     * either passed to the constructor of NuimoDiscoveryManager or as an argument tho this method)
     * to override {@see Activity#onRequestPermissionsResult(Int,String[],int[])} and forward the
     * call to {@see NuimoDiscoveryManager#onRequestPermissionsResult(Int,String[],int[])}.
     *
     * @return Returns true if all necessary permissions are already granted. I.e. if your app is
     *         running on Android <= 5.1 or targets an Android SDK version <= 22.
     *         Returns false if the user has not yet granted the necessary permissions.
     */
    public fun checkPermissions(activity: Activity?): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) { return true }
        if (activity != null) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                //TODO: Show an explanation to the user *asynchronously* why this permission is needed. We should invoke a listener delegate here.
                print("shouldShowRequestPermissionRationale")
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSIONS_REQUEST_CODE)
            }
        }
        return false
    }

    /**
     * To be called from your activity that received the {@see Activity#onRequestPermissionsResult(Int,String[],int[])}
     * callback.
     *
     * The controller discovery will automatically start if a previous attempt in calling
     * startDiscovery() failed due to still then missing permissions and if the permissions are now
     * granted.
     *
     * @return True if necessary permissions have been granted by the user otherwise false.
     */
    public fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode != PERMISSIONS_REQUEST_CODE) { return false }

        val permissionIndex = permissions.indexOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissionIndex < 0 || grantResults.size < permissionIndex) { return false }

        val permissionGranted = grantResults[permissionIndex] == PackageManager.PERMISSION_GRANTED
        if (permissionGranted && shouldStartDiscoveryWhenPermissionsGranted) {
            startDiscovery()
        }
        return permissionGranted
    }

    /**
     * @return true if the user has enabled Bluetooth, otherwise false.
     */
    public fun checkBluetoothEnabled(): Boolean {
        return BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
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
