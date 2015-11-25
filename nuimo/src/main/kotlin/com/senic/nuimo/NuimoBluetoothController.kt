/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.*

// TODO: Queue write requests to the device. Both characteristics writes as well as descriptor writes
public class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController(bluetoothDevice.address) {
    private val device = bluetoothDevice
    private val context = context
    private var gatt: BluetoothGatt? = null
    private var matrixCharacteristic: BluetoothGattCharacteristic? = null
    // At least some devices such as Samsung S3, S4, all BLE calls must occur from the main thread, see http://stackoverflow.com/questions/20069507/gatt-callback-fails-to-register
    private val mainHandler = Handler(Looper.getMainLooper())
    private var writeQueue = WriteQueue()

    override fun connect() {
        mainHandler.post {
            device.connectGatt(context, true, GattCallback())
        }
    }

    override fun disconnect() {
        mainHandler.post {
            gatt?.disconnect()
        }
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix) {
        if (gatt == null || matrixCharacteristic == null) { return }
        writeQueue.push {
            //TODO: Synchronize access to matrixCharacteristic, writeQueue executes lambda on different thread
            matrixCharacteristic?.setValue(matrix.gattBytes())
            gatt?.writeCharacteristic(matrixCharacteristic)
        }
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            println("Connection state changed " + newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@NuimoBluetoothController.gatt = gatt
                    mainHandler.post {
                        gatt.discoverServices()
                    }
                    listeners.forEach { it.onConnect() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    this@NuimoBluetoothController.gatt = null
                    listeners.forEach { it.onDisconnect() }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services?.flatMap { it.characteristics }?.forEach {
                if (LED_MATRIX_CHARACTERISTIC_UUID == it.uuid) {
                    matrixCharacteristic = it
                } else if (CHARACTERISTIC_NOTIFICATION_UUIDS.contains(it.uuid)) {
                    writeQueue.push { gatt.setCharacteristicNotification2(it, true) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeQueue.next()
            listeners.forEach { it.onLedMatrixWrite() }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                else -> {
                    val event = characteristic.toNuimoGestureEvent()
                    if (event != null) {
                        listeners.forEach { it.onGestureEvent(event) }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (!writeQueue.next()) {
                listeners.forEach { it.onReady() }
            }
        }
    }

    private inner class WriteQueue {
        private var queue = LinkedList<() -> Unit>()
        private var idle = true

        //TODO: Synchronize access
        fun push(request: () -> Unit) {
            when (idle) {
                true  -> { idle = false; performWriteRequest(request) }
                false -> queue.addLast(request)
            }
        }

        fun next(): Boolean {
            when (queue.size) {
                0    -> idle = true
                else -> performWriteRequest(queue.removeFirst())
            }
            return !idle
        }

        private fun performWriteRequest(request: () -> Unit) {
            mainHandler.post { request() }
        }
    }
}

/*
 * Nuimo BLE GATT service and characteristic UUIDs
 */

private val BATTERY_SERVICE_UUID                   = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_CHARACTERISTIC_UUID            = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_SERVICE_UUID        = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
private val LED_MATRIX_SERVICE_UUID                = UUID.fromString("f29b1523-cb19-40f3-be5c-7241ecb82fd1")
private val LED_MATRIX_CHARACTERISTIC_UUID         = UUID.fromString("f29b1524-cb19-40f3-be5c-7241ecb82fd1")
private val SENSOR_SERVICE_UUID                    = UUID.fromString("f29b1525-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_FLY_CHARACTERISTIC_UUID         = UUID.fromString("f29b1526-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_TOUCH_CHARACTERISTIC_UUID       = UUID.fromString("f29b1527-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_ROTATION_CHARACTERISTIC_UUID    = UUID.fromString("f29b1528-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_BUTTON_CHARACTERISTIC_UUID      = UUID.fromString("f29b1529-cb19-40f3-be5c-7241ecb82fd2")

val NUIMO_SERVICE_UUIDS = arrayOf(
        BATTERY_SERVICE_UUID,
        DEVICE_INFORMATION_SERVICE_UUID,
        LED_MATRIX_SERVICE_UUID,
        SENSOR_SERVICE_UUID
)

private val CHARACTERISTIC_NOTIFICATION_UUIDS = arrayOf(
        //BATTERY_CHARACTERISTIC_UUID,
        //SENSOR_FLY_CHARACTERISTIC_UUID,
        //SENSOR_TOUCH_CHARACTERISTIC_UUID,
        SENSOR_ROTATION_CHARACTERISTIC_UUID,
        SENSOR_BUTTON_CHARACTERISTIC_UUID
)

/*
 * Private extensions
 */

//TODO: Should be only visible in this module but then it's not seen by the test
fun NuimoLedMatrix.gattBytes(): ByteArray {
    return bits
            .chunk(8)
            .map { it
                    .mapIndexed { i, b -> if (b) { 1 shl i } else { 0 } }
                    .reduce { n, i -> n + i }
            }
            .map { it.toByte() }
            .toByteArray()
}

//TODO: Convert into generic function
private fun List<Boolean>.chunk(n: Int): List<List<Boolean>> {
    var chunks = java.util.ArrayList<List<Boolean>>(size / n + 1)
    var chunk = ArrayList<Boolean>(n)
    var i = n
    forEach {
        chunk.add(it)
        if (--i == 0) {
            chunks.add(ArrayList<Boolean>(chunk))
            chunk.clear()
            i = n
        }
    }
    if (chunk.isNotEmpty()) { chunks.add(chunk) }
    return chunks
}

private fun BluetoothGattCharacteristic.toNuimoGestureEvent(): NuimoGestureEvent? {
    return when (uuid) {
        SENSOR_BUTTON_CHARACTERISTIC_UUID -> {
            //TODO: BUTTON_PRESS should be encoded with 1 and BUTTON_RELEASE with 0. Strangely we need to swap values here.
            val value = 1 - (getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0)
            return NuimoGestureEvent(if (value == 1) NuimoGesture.BUTTON_PRESS else NuimoGesture.BUTTON_RELEASE, value)
        }
        SENSOR_ROTATION_CHARACTERISTIC_UUID -> {
            val value = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
            return NuimoGestureEvent(if (value >= 0) NuimoGesture.ROTATE_RIGHT else NuimoGesture.ROTATE_LEFT, Math.abs(value))
        }
        else -> null
    }
}

private val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private fun BluetoothGatt.setCharacteristicNotification2(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
    setCharacteristicNotification(characteristic, enable)
    // http://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
    val descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
    descriptor.setValue(if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    return writeDescriptor(descriptor);
}
