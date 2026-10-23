package com.alphaxk.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.io.DataOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// ============================================================================
// 1. Main Activity: إدارة الصلاحيات وتشغيل الخدمة
// ============================================================================
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // واجهة بسيطة لبدء الخدمة
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        val btn = Button(this)
        btn.text = "تشغيل ALPHA Keymapper (يتطلب Root)"
        btn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
                Toast.makeText(this, "الرجاء إعطاء صلاحية Overlay ثم المحاولة مجدداً", Toast.LENGTH_LONG).show()
            } else {
                // طلب صلاحية Root للـ Injection السريع
                if (RootUtils.checkRoot()) {
                    startService(Intent(this, KeyMapperService::class.java))
                    Toast.makeText(this, "تم التشغيل! اذهب لإعدادات Accessibility وفعل الخدمة.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "الرجاء منح صلاحية Root للأداة (ضروري للـ Multi-touch)", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        layout.addView(btn)
        setContentView(layout)
    }
}

// ============================================================================
// 2. Accessibility Service: القلب النابض للأداة (Overlay + Input Capture)
// ============================================================================
class KeyMapperService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    
    // إحداثيات الأزرار وإعداداتها
    private val buttons = mutableListOf<KeyButton>()
    private var isEditMode = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showOverlay()
        Toast.makeText(this, "ALPHA Keymapper جاهز", Toast.LENGTH_SHORT).show()
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
        
        // زر تعديل/لعب في الجانب
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

        // إضافة زر افتراضي للكيبورد
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

    // استقبال ضغطات الكيبورد
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) return false
        val btn = buttons.find { it.keyCode == event.keyCode }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN) {
                it.triggerAction(true)
            } else if (event.action == KeyEvent.ACTION_UP) {
                it.triggerAction(false)
            }
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

// ============================================================================
// 3. فئات مساعدة (KeyButton, ActionType, TouchInjector)
// ============================================================================
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
        // رسم بسيط للزر
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
        setMeasuredDimension(150, 150) // حجم الزر
    }

    fun triggerAction(pressed: Boolean) {
        isPressed = pressed
        invalidate()
        if (pressed) {
            when (actionType) {
                ActionType.TAP -> TouchInjector.injectTouch(x + width/2, y + height/2, true)
                ActionType.HOLD -> TouchInjector.injectTouch(x + width/2, y + height/2, true)
                ActionType.DRAG -> TouchInjector.injectSwipe(x + width/2, y + height/2, x + width/2 + 200, y + height/2, 300)
            }
        } else {
            if (actionType == ActionType.TAP || actionType == ActionType.HOLD) {
                TouchInjector.injectTouch(x + width/2, y + height/2, false)
            }
        }
    }
}

// ============================================================================
// 4. محرك الحقن (Touch Injection) - يتطلب Root
// ============================================================================
object TouchInjector {
    private val executor = Executors.newSingleThreadExecutor()

    // دالة الحقن (Swipe أو Tap)
    fun injectTouch(x: Float, y: Float, down: Boolean) {
        executor.execute {
            try {
                // استخدام Input command المباشر من الروت (سريع جداً)
                val action = if (down) "DOWN" else "UP"
                val cmd = "input motionevent $action $x $y"
                RootUtils.executeCommand(cmd)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int) {
        executor.execute {
            try {
                val cmd = "input swipe $x1 $y1 $x2 $y2 $durationMs"
                RootUtils.executeCommand(cmd)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

object RootUtils {
    fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            process.waitFor(1, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun executeCommand(cmd: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
