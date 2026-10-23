package com.alphaxk.keymapper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
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
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        val btn = Button(this)
        btn.text = "تشغيل ALPHA Keymapper (Shizuku)"
        btn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
                Toast.makeText(this, "الرجاء إعطاء صلاحية Overlay ثم المحاولة مجدداً", Toast.LENGTH_LONG).show()
            } else {
                checkShizukuPermission()
            }
        }
        
        layout.addView(btn)
        setContentView(layout)

        // طلب صلاحية Shizuku
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                startService(Intent(this, KeyMapperService::class.java))
                Toast.makeText(this, "تم التشغيل! اذهب لإعدادات Accessibility وفعل الخدمة.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            startService(Intent(this, KeyMapperService::class.java))
            Toast.makeText(this, "تم التشغيل! اذهب لإعدادات Accessibility وفعل الخدمة.", Toast.LENGTH_LONG).show()
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener { _, _ -> }
        super.onDestroy()
    }
}

class KeyMapperService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val buttons = mutableListOf<KeyButton>()
    private var isEditMode = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val rootLayout = FrameLayout(this)
        val toggleBtn = TextView(this)
        toggleBtn.text = "تعديل"
        toggleBtn.setTextColor(Color.WHITE)
        toggleBtn.setBackgroundColor(Color.parseColor("#80000000"))
        toggleBtn.setPadding(20, 20, 20, 20)
        val toggleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = 20 }
        
        toggleBtn.setOnClickListener {
            isEditMode = !isEditMode
            toggleBtn.text = if (isEditMode) "تعديل" else "لعب"
            updateButtonsMode()
        }
        rootLayout.addView(toggleBtn, toggleParams)

        addKeyButton(rootLayout, KeyEvent.KEYCODE_W, 200f, 200f, "W", ActionType.TAP)

        overlayView = rootLayout
        windowManager.addView(overlayView, params)
    }

    private fun addKeyButton(parent: FrameLayout, keyCode: Int, x: Float, y: Float, label: String, actionType: ActionType) {
        val btn = KeyButton(this, keyCode, label, actionType)
        btn.x = x
        btn.y = y
        btn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isEditMode) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = v.x.toInt()
                            initialY = v.y.toInt()
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            v.x = initialX + (event.rawX - initialTouchX)
                            v.y = initialY + (event.rawY - initialTouchY)
                        }
                    }
                }
                return true
            }
        })
        parent.addView(btn)
        buttons.add(btn)
    }

    private fun updateButtonsMode() {
        buttons.forEach { it.isEditMode = isEditMode; it.invalidate() }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) return false
        val btn = buttons.find { it.keyCode == event.keyCode }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN) it.triggerAction(true)
            else if (event.action == KeyEvent.ACTION_UP) it.triggerAction(false)
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}

enum class ActionType { TAP, HOLD, DRAG }

class KeyButton(
    context: Context, 
    val keyCode: Int, 
    val label: String, 
    var actionType: ActionType
) : View(context) {
    var isEditMode = true
    private var isPressed = false

    init {
        background = context.getDrawable(android.R.drawable.ic_menu_add)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = if (isPressed) Color.GREEN else Color.WHITE
            textSize = 50f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height / 2f, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(150, 150)
    }

    fun triggerAction(pressed: Boolean) {
        isPressed = pressed
        invalidate()
        if (pressed) {
            when (actionType) {
                ActionType.TAP, ActionType.HOLD -> ShizukuUtils.injectTouch(x + width/2, y + height/2, true)
                ActionType.DRAG -> ShizukuUtils.injectSwipe(x + width/2, y + height/2, x + width/2 + 200, y + height/2, 300)
            }
        } else {
            if (actionType == ActionType.TAP || actionType == ActionType.HOLD) {
                ShizukuUtils.injectTouch(x + width/2, y + height/2, false)
            }
        }
    }
}

object ShizukuUtils {
    private val executor = Executors.newSingleThreadExecutor()

    fun injectTouch(x: Float, y: Float, down: Boolean) {
        executor.execute {
            try {
                val action = if (down) "DOWN" else "UP"
                executeCommand("input motionevent $action $x $y")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int) {
        executor.execute {
            try { executeCommand("input swipe $x1 $y1 $x2 $y2 $durationMs") } 
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun executeCommand(cmd: String) {
        if (!Shizuku.pingBinder()) return
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
