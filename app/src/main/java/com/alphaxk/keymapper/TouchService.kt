package com.alphaxk.keymapper

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import java.lang.reflect.Method
import kotlin.random.Random

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

    // دالة الحقن المتزامن (Multi-touch)
    override fun injectMultiTouch(actions: IntArray, ids: IntArray, xs: IntArray, ys: IntArray) {
        val pointerCount = actions.size
        if (pointerCount == 0) return

        // إضافة اهتزاز بشري (Jitter) بسيط عشان نتخفى عن الـ Anti-Cheat
        val jitterX = Random.nextFloat() * 2f - 1f
        val jitterY = Random.nextFloat() * 2f - 1f
        
        val props = Array(pointerCount) { i ->
            MotionEvent.PointerProperties().apply {
                id = ids[i]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointerCount) { i ->
            MotionEvent.PointerCoords().apply {
                x = xs[i] + jitterX
                y = ys[i] + jitterY
                pressure = 1f
                size = 1f
            }
        }

        val downTime = SystemClock.uptimeMillis()
        
        // بنبعت الحدث الأساسي
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), 
            actions[0], pointerCount, props, coords, 
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        
        try {
            injectMethod.invoke(inputManager, event, 0) // 0 = ASYNC
        } catch (e: Exception) {
            e.printStackTrace()
        }
        event.recycle()
    }

    // دالة السحب (عشان الماوس)
    override fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Int) {
        val downTime = SystemClock.uptimeMillis()
        val steps = duration / 16
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        
        var eventTime = downTime
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1, props, arrayOf(createCoords(x1, y1)), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        inject(down)

        for (i in 1..steps) {
            eventTime = downTime + (i * 16L)
            val fraction = i.toFloat() / steps
            // منحنى بشري بدل الخط المستقيم
            val x = x1 + (x2 - x1) * fraction + (Random.nextFloat() - 0.5f) * 2f
            val y = y1 + (y2 - y1) * fraction + (Random.nextFloat() - 0.5f) * 2f
            val move = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, 1, props, arrayOf(createCoords(x, y)), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            inject(move)
        }
        val up = MotionEvent.obtain(downTime, eventTime + 16, MotionEvent.ACTION_UP, 1, props, arrayOf(createCoords(x2, y2)), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        inject(up)
    }

    private fun createCoords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
        this.x = x; this.y = y; pressure = 1f; size = 1f
    }

    private fun inject(event: MotionEvent) {
        try { injectMethod.invoke(inputManager, event, 0) } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }
}
