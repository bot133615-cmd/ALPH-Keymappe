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
            // تمرير الخدمة للـ Accessibility Service عشان يستخدمها
            KeyMapperService.touchService = touchService
            Toast.makeText(this@MainActivity, "خدمة اللمس جاهزة", Toast.LENGTH_SHORT).show()
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
            text = "بدء خدمة الشيزوكو واللمس"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    Toast.makeText(this@MainActivity, "محتاجين صلاحية Overlay", Toast.LENGTH_SHORT).show()
                } else if (checkShizukuPermission()) {
                    bindUserService()
                    startService(Intent(this@MainActivity, KeyMapperService::class.java))
                    Toast.makeText(this@MainActivity, "تم البدء! روح فعل Accessibility", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(btn)
        setContentView(layout)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bindUserService()
                startService(Intent(this, KeyMapperService::class.java))
            }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            Shizuku.requestPermission(1001)
            return false
        }
    }

    private fun bindUserService() {
        if (touchService != null) return
        val args = Shizuku.UserServiceArgs(
            ComponentName(packageName, TouchService::class.java.name)
        ).daemon(false).processNameSuffix("touch_service").debuggable(false).version(1)
        Shizuku.bindUserService(args, connection)
    }
}

// ============================================================================
// خدمة Accessibility اللي بترسم الأزرار على الشاشة
// ============================================================================
class KeyMapperService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
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
        toggleBtn.setPadding(30, 30, 30, 30)
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

        // إضافة زرار W (ممكن تكرر السطر ده لبقية الأزرار)
        addKeyButton(rootLayout, KeyEvent.KEYCODE_W, 200f, 200f, "W", ActionType.HOLD)

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
                    return true
                }
                return false
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
        
        val xPos = x + width / 2
        val yPos = y + height / 2
        
        try {
            if (pressed) {
                when (actionType) {
                    ActionType.TAP, ActionType.HOLD -> KeyMapperService.touchService?.injectTap(xPos.toInt(), yPos.toInt())
                    ActionType.DRAG -> KeyMapperService.touchService?.injectSwipe(xPos.toInt(), yPos.toInt(), xPos.toInt() + 300, yPos.toInt(), 300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
