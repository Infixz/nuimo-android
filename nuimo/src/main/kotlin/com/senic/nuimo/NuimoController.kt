/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import java.util.*

abstract class NuimoController(address: String) {
    val address: String = address
    var defaultMatrixDisplayInterval = 2.0

    protected val listeners = ArrayList<NuimoControllerListener>()

    abstract fun connect()

    abstract fun disconnect()

    abstract fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double = defaultMatrixDisplayInterval)

    fun addControllerListener(controllerListener: NuimoControllerListener) {
        listeners.add(controllerListener)
    }

    fun removeControllerListener(controllerListener: NuimoControllerListener) {
        listeners.remove(controllerListener)
    }
}

public interface NuimoControllerListener {
    fun onConnect() { println("") }
    fun onDisconnect() {}
    fun onReady() {}
    fun onLedMatrixWrite() {}
    fun onGestureEvent(event: NuimoGestureEvent) {}
}

public abstract class BaseNuimoControllerListener: NuimoControllerListener {
    override fun onConnect() {}
    override fun onDisconnect() {}
    override fun onReady() {}
    override fun onLedMatrixWrite() {}
    override fun onGestureEvent(event: NuimoGestureEvent) {}
}
