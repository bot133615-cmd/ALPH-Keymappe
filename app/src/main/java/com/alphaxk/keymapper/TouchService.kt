package com.alphaxk.keymapper

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import java.lang.reflect.Method

class TouchService : ITouchService.Stub() {
    private val inputManager: Any by lazy {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }
    private val injectMethod: Method by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }

    override fun injectTap(x: Int, y: Int) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(createPointerProperties(0))
        val coords = arrayOf(createPointerCoords(x.toFloat(), y.toFloat()))
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        inject(down); inject(up)
    }

    override fun injectSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(createPointerProperties(0))
        var eventTime = downTime
        val steps = duration / 16
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1, props, arrayOf(createPointerCoords(x1.toFloat(), y1.toFloat())), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        inject(down)
        for (i in 1..steps) {
            eventTime = downTime + (i * 16L)
            val fraction = i.toFloat() / steps
            val x = x1 + (x2 - x1) * fraction
            val y = y1 + (y2 - y1) * fraction
            val move = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, 1, props, arrayOf(createPointerCoords(x, y)), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            inject(move)
        }
        val up = MotionEvent.obtain(downTime, eventTime + 16, MotionEvent.ACTION_UP, 1, props, arrayOf(createPointerCoords(x2.toFloat(), y2.toFloat())), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        inject(up)
    }

    override fun injectMultiTouch(xs: IntArray, ys: IntArray, actions: IntArray) {
        val count = xs.size
        val props = Array(count) { createPointerProperties(it) }
        val coords = Array(count) { i -> createPointerCoords(xs[i].toFloat(), ys[i].toFloat()) }
        val downTime = SystemClock.uptimeMillis()
        for (i in 0 until count) {
            val action = actions[i]
            val mask = (action shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or MotionEvent.ACTION_POINTER_DOWN
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), mask, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            inject(event)
        }
    }

    private fun inject(event: MotionEvent) {
        try {
            injectMethod.invoke(inputManager, event, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }

    private fun createPointerProperties(id: Int): PointerProperties {
        return PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
    }

    private fun createPointerCoords(x: Float, y: Float): PointerCoords {
        return PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }
    }
}
