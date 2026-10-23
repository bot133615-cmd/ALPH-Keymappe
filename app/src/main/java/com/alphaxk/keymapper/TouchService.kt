package com.alphaxk.keymapper

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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

    private var downTime = 0L

    override fun injectTouch(action: Int, pointerId: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = pointerId; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { injectMethod.invoke(inputManager, event, 0) } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }

    override fun injectMultiTouch(pointerIds: IntArray, xs: FloatArray, ys: FloatArray) {
        val count = pointerIds.size
        if (count == 0) return
        
        // عشان نعمل ضغطات متزامنة، لازم نبعت حدث من نوع POINTER_DOWN مع الـ IDs
        if (downTime == 0L) downTime = SystemClock.uptimeMillis()
        
        val props = Array(count) { i -> MotionEvent.PointerProperties().apply { id = pointerIds[i]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(count) { i -> MotionEvent.PointerCoords().apply { this.x = xs[i]; this.y = ys[i]; pressure = 1f; size = 1f } }

        // نبعت الحدث الأساسي
        val action = if (count == 1) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_MOVE or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { injectMethod.invoke(inputManager, event, 0) } catch (e: Exception) { e.printStackTrace() }
        event.recycle()
    }
}
