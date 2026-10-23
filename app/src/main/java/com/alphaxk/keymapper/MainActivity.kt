package com.alphaxk.keymapper

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private var touchService: ITouchService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            touchService = ITouchService.Stub.asInterface(service)
            KeyMapperService.touchService = touchService
            Toast.makeText(this@MainActivity, "المحرك جاهز للانطلاق", Toast.LENGTH_SHORT).show()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
            KeyMapperService.touchService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        val btn = Button(this).apply {
            text = "بدء الأداة (يتطلب Shizuku)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                } else if (checkShizukuPermission()) {
                    bindUserService()
                    startService(Intent(this@MainActivity, KeyMapperService::class.java))
                }
            }
        }
        layout.addView(btn)
        setContentView(layout)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bindUserService()
                startService(Intent(this@MainActivity, KeyMapperService::class.java))
            }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) return true
        Shizuku.requestPermission(1001)
        return false
    }

    private fun bindUserService() {
        if (touchService != null) return
        val args = Shizuku.UserServiceArgs(ComponentName(packageName, TouchService::class.java.name))
            .daemon(false).processNameSuffix("touch_service").debuggable(false).version(1)
        Shizuku.bindUserService(args, connection)
    }
}

class KeyMapperService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var mouseView: MouseInterceptorView? = null
    private val buttons = mutableListOf<KeyButton>()
    private var isEditMode = true

    companion object {
        var touchService: ITouchService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val rootLayout = FrameLayout(this)
        
        // 1. طبقة الماوس (شفافة - بتقرا حركة الماوس وتعدي اللمس)
        mouseView = MouseInterceptorView(this) { dx, dy ->
            // تحريك الكاميرا في نص الشاشة
            val centerX = 600f 
            val centerY = 400f
            if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
                touchService?.injectSwipe(centerX, centerY, centerX + (dx * 15), centerY + (dy * 15), 50)
            }
        }
        rootLayout.addView(mouseView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 2. زرار التعديل/اللعب
        val toggleBtn = TextView(this).apply {
            text = "تعديل"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(30, 30, 30, 30)
            setOnClickListener {
                isEditMode = !isEditMode
                text = if (isEditMode) "تعديل" else "لعب"
                updateButtonsMode()
            }
        }
        val toggleParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { 
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = 20 
        }
        rootLayout.addView(toggleBtn, toggleParams)

        // 3. إضافة أزرار اللعب
        addKeyButton(rootLayout, KeyEvent.KEYCODE_W, 200f, 200f, "W")
        addKeyButton(rootLayout, KeyEvent.KEYCODE_A, 100f, 300f, "A")
        addKeyButton(rootLayout, KeyEvent.KEYCODE_S, 200f, 400f, "S")
        addKeyButton(rootLayout, KeyEvent.KEYCODE_D, 300f, 300f, "D")
        addKeyButton(rootLayout, KeyEvent.KEYCODE_SPACE, 800f, 400f, "Jump")
        // زرار كليك يمين (Aim) وكليك شمال (Fire)
        addKeyButton(rootLayout, KeyEvent.KEYCODE_BUTTON_R1, 800f, 200f, "Aim")
        addKeyButton(rootLayout, KeyEvent.KEYCODE_BUTTON_L1, 800f, 250f, "Fire")

        overlayView = rootLayout
        windowManager.addView(overlayView, params)
    }

    private fun addKeyButton(parent: FrameLayout, keyCode: Int, x: Float, y: Float, label: String) {
        val btn = KeyButton(this, keyCode, label)
        btn.x = x
        btn.y = y
        btn.setOnTouchListener { v, event ->
            if (isEditMode) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { btn.startX = v.x; btn.startY = v.y; btn.touchX = event.rawX; btn.touchY = event.rawY }
                    MotionEvent.ACTION_MOVE -> { v.x = btn.startX + (event.rawX - btn.touchX); v.y = btn.startY + (event.rawY - btn.touchY) }
                }
            }
            true
        }
        parent.addView(btn)
        buttons.add(btn)
    }

    private fun updateButtonsMode() { 
        buttons.forEach { it.isEditMode = isEditMode; it.invalidate() }
        mouseView?.visibility = if (isEditMode) View.GONE else View.VISIBLE
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) return false
        val btn = buttons.find { it.keyCode == event.keyCode }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN && !it.isPressed) {
                it.isPressed = true
                sendTouch(it, true)
            } else if (event.action == KeyEvent.ACTION_UP && it.isPressed) {
                it.isPressed = false
                sendTouch(it, false)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun sendTouch(btn: KeyButton, isDown: Boolean) {
        val pressedButtons = buttons.filter { it.isPressed }
        if (pressedButtons.isEmpty()) return

        val actions = IntArray(pressedButtons.size)
        val ids = IntArray(pressedButtons.size)
        val xs = IntArray(pressedButtons.size)
        val ys = IntArray(pressedButtons.size)

        for (i in pressedButtons.indices) {
            val b = pressedButtons[i]
            ids[i] = b.keyCode
            xs[i] = (b.x + b.width / 2).toInt()
            ys[i] = (b.y + b.height / 2).toInt()
            
            actions[i] = if (pressedButtons.size == 1) {
                if (isDown) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP
            } else {
                val index = pressedButtons.size - 1
                val mask = (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                if (isDown) MotionEvent.ACTION_POINTER_DOWN or mask else MotionEvent.ACTION_POINTER_UP or mask
            }
        }

        try {
            touchService?.injectMultiTouch(actions, ids, xs, ys)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

// ============================================================================
// كلاس مخصص لاعتراض الماوس
// ============================================================================
class MouseInterceptorView(context: Context, private val onMove: (Float, Float) -> Unit) : View(context) {
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                onMove(dx, dy)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // عشان نسمح للعبة إنها تستقبل اللمس العادي
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false 
    }
}

class KeyButton(context: Context, val keyCode: Int, val label: String) : View(context) {
    var isEditMode = true
    var isPressed = false
    var startX = 0f; var startY = 0f; var touchX = 0f; var touchY = 0f

    init { background = context.getDrawable(android.R.drawable.ic_menu_add) }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = if (isPressed) Color.GREEN else Color.WHITE
            textSize = 50f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height / 2f, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(150, 150) }
}
