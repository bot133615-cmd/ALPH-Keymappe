package com.alphaxk.keymapper

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import android.widget.LinearLayout
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
            Toast.makeText(this@MainActivity, "المحرك متصل وجاهز!", Toast.LENGTH_SHORT).show()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
            KeyMapperService.touchService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "ALPHA Keymapper v2"
            setTextColor(Color.WHITE)
            textSize = 24f
        }
        layout.addView(title)

        layout.addView(Button(this).apply {
            text = "1. صلاحية الظهور فوق التطبيقات"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        })

        layout.addView(Button(this).apply {
            text = "2. تفعيل خدمة Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        layout.addView(Button(this).apply {
            text = "3. تشغيل Shizuku وربط الخدمة"
            setOnClickListener {
                if (checkShizukuPermission()) {
                    bindUserService()
                    startService(Intent(this@MainActivity, KeyMapperService::class.java))
                    Toast.makeText(this@MainActivity, "تم الربط! اذهب للعبة", Toast.LENGTH_LONG).show()
                }
            }
        })

        setContentView(layout)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bindUserService()
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
    private var floatMenu: View? = null
    private var editPanel: LinearLayout? = null
    private var mouseArea: View? = null
    private var dPadView: View? = null
    
    private val buttons = mutableListOf<KeyButton>()
    private var isEditMode = true
    private var isPanelVisible = false
    private var currentPanelX = 200 // لتحديد مكان النافذة الجانبية

    companion object {
        var touchService: ITouchService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatMenu()
    }

    private fun showFloatMenu() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = 20
        }

        val menuBtn = TextView(this).apply {
            text = "⚙"
            textSize = 30f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#AA000000"))
            setPadding(20, 20, 20, 20)
            setOnClickListener { toggleEditPanel() }
        }
        
        menuBtn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatMenu, params)
                    }
                }
                return false
            }
        })

        floatMenu = menuBtn
        windowManager.addView(floatMenu, params)
    }

    private fun toggleEditPanel() {
        if (isPanelVisible) {
            editPanel?.let { windowManager.removeView(it) }
            isPanelVisible = false
            return
        }

        val params = WindowManager.LayoutParams(
            450, 
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 20
        }

        editPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(20, 20, 20, 20)

            addView(TextView(this@KeyMapperService).apply { text = "لوحة التحكم"; setTextColor(Color.WHITE); textSize = 18f })
            addView(Button(this@KeyMapperService).apply {
                text = if (isEditMode) "الوضع: تعديل (دوس للعب)" else "الوضع: لعب (دوس للتعديل)"
                setOnClickListener {
                    isEditMode = !isEditMode
                    text = if (isEditMode) "الوضع: تعديل (دوس للعب)" else "الوضع: لعب (دوس للتعديل)"
                    updateButtonsMode()
                }
            })

            // زرار دائرة التحكم
            addView(Button(this@KeyMapperService).apply {
                text = "إظهار/إخفاء دائرة التحكم (WASD)"
                setOnClickListener { toggleDPad() }
            })

            // زرار إضافة عناصر
            addView(Button(this@KeyMapperService).apply {
                text = "إضافة زر إطلاق (Fire)"
                setOnClickListener { addSingleButton(KeyEvent.KEYCODE_BUTTON_L1, "Fire", ActionType.TAP) }
            })
            addView(Button(this@KeyMapperService).apply {
                text = "إضافة زر تصويب (Aim)"
                setOnClickListener { addSingleButton(KeyEvent.KEYCODE_BUTTON_R1, "Aim", ActionType.HOLD) }
            })

            addView(Button(this@KeyMapperService).apply {
                text = "تفعيل منطقة الماوس"
                setOnClickListener { addMouseArea() }
            })

            addView(Button(this@KeyMapperService).apply {
                text = "إخفاء كل شيء (لعب نظيف)"
                setOnClickListener { hideAllButtons() }
            })
        }

        windowManager.addView(editPanel, params)
        isPanelVisible = true
    }

    private fun addSingleButton(keyCode: Int, label: String, action: ActionType) {
        val btn = KeyButton(this, keyCode, label, action, 80) // حجم 80
        btn.x = 200f; btn.y = 400f
        btn.isEditMode = isEditMode
        setupButtonTouch(btn)
        windowManager.addView(btn, getLayoutParams(80, 80, btn.x.toInt(), btn.y.toInt()))
        buttons.add(btn)
    }

    private fun toggleDPad() {
        if (dPadView != null) {
            windowManager.removeView(dPadView)
            dPadView = null
            // إزالة أزرار الـ D-Pad من المصفوفة
            buttons.removeAll { it.label in listOf("W", "A", "S", "D") }
            Toast.makeText(this, "تم إخفاء الدائرة", Toast.LENGTH_SHORT).show()
            return
        }

        val params = WindowManager.LayoutParams(
            300, 300,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 100; y = 100
        }

        val dPad = FrameLayout(this)
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#44000000"))
        shape.setStroke(5, Color.WHITE)
        dPad.background = shape

        val size = 80
        val btnW = KeyButton(this, KeyEvent.KEYCODE_W, "W", ActionType.HOLD, size)
        val btnS = KeyButton(this, KeyEvent.KEYCODE_S, "S", ActionType.HOLD, size)
        val btnA = KeyButton(this, KeyEvent.KEYCODE_A, "A", ActionType.HOLD, size)
        val btnD = KeyButton(this, KeyEvent.KEYCODE_D, "D", ActionType.HOLD, size)

        setupButtonTouch(btnW); setupButtonTouch(btnS); setupButtonTouch(btnA); setupButtonTouch(btnD)

        val pW = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = 10 }
        val pS = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = 10 }
        val pA = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; leftMargin = 10 }
        val pD = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = 10 }

        dPad.addView(btnW, pW)
        dPad.addView(btnS, pS)
        dPad.addView(btnA, pA)
        dPad.addView(btnD, pD)

        windowManager.addView(dPad, params)
        dPadView = dPad
        buttons.addAll(listOf(btnW, btnS, btnA, btnD))
        updateButtonsMode()
        Toast.makeText(this, "تم إضافة الدائرة", Toast.LENGTH_SHORT).show()
    }

    private fun setupButtonTouch(btn: KeyButton) {
        btn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0f; var initialY = 0f; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isEditMode) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = v.x; initialY = v.y
                            initialTouchX = event.rawX; initialTouchY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (v.parent is FrameLayout && dPadView != null) return true // نمنع تحريك أزرار الدائرة
                            v.x = initialX + (event.rawX - initialTouchX)
                            v.y = initialY + (event.rawY - initialTouchY)
                        }
                    }
                }
                return true
            }
        })
        btn.setOnLongClickListener {
            if (isEditMode) {
                btn.actionType = when(btn.actionType) {
                    ActionType.TAP -> ActionType.HOLD
                    ActionType.HOLD -> ActionType.DRAG
                    ActionType.DRAG -> ActionType.TAP
                }
                Toast.makeText(this, "النوع: ${btn.actionType}", Toast.LENGTH_SHORT).show()
                btn.invalidate()
            }
            true
        }
    }

    private fun getLayoutParams(w: Int, h: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
        }
    }

    private fun addMouseArea() {
        if (mouseArea != null) return
        val params = WindowManager.LayoutParams(
            600, 
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        mouseArea = MouseInterceptorView(this) { dx, dy ->
            if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
                // سحب سريع لمحاكاة حركة الماوس في اللعبة
                touchService?.injectTouch(MotionEvent.ACTION_DOWN, 99, 1000f, 500f)
                touchService?.injectTouch(MotionEvent.ACTION_MOVE, 99, 1000f + (dx * 10), 500f + (dy * 10))
                touchService?.injectTouch(MotionEvent.ACTION_UP, 99, 1000f + (dx * 10), 500f + (dy * 10))
            }
        }.apply {
            setBackgroundColor(Color.parseColor("#22FF0000"))
        }

        windowManager.addView(mouseArea, params)
        Toast.makeText(this, "تمت إضافة منطقة الماوس", Toast.LENGTH_SHORT).show()
    }

    private fun hideAllButtons() {
        buttons.forEach { it.visibility = View.GONE }
        mouseArea?.visibility = View.GONE
        dPadView?.visibility = View.GONE
        isEditMode = false
        Toast.makeText(this, "تم الإخفاء. اضغط على ⚙ للعودة", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonsMode() {
        buttons.forEach { it.isEditMode = isEditMode; it.visibility = View.VISIBLE; it.invalidate() }
        mouseArea?.let { it.visibility = View.VISIBLE; it.setBackgroundColor(if(isEditMode) Color.parseColor("#22FF0000") else Color.TRANSPARENT) }
        dPadView?.visibility = View.VISIBLE
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) return false
        if (touchService == null) {
            Toast.makeText(this, "الخدمة غير متصلة!", Toast.LENGTH_SHORT).show()
            return false
        }

        val btn = buttons.find { it.keyCode == event.keyCode }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN && !it.isActionPressed) {
                it.isActionPressed = true
                it.invalidate()
                sendTouch(it)
            } else if (event.action == KeyEvent.ACTION_UP && it.isActionPressed) {
                it.isActionPressed = false
                it.invalidate()
                sendTouch(it)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun sendTouch(btn: KeyButton) {
        val activeButtons = buttons.filter { it.isActionPressed }
        if (activeButtons.isEmpty()) return

        val ids = IntArray(activeButtons.size)
        val xs = FloatArray(activeButtons.size)
        val ys = FloatArray(activeButtons.size)

        for (i in activeButtons.indices) {
            val b = activeButtons[i]
            ids[i] = b.keyCode
            // حساب المكان الفعلي للزر على الشاشة
            var posX = b.x
            var posY = b.y
            if (b.parent is View && b.parent is FrameLayout) {
                val parent = b.parent as FrameLayout
                val parentParams = parent.layoutParams as WindowManager.LayoutParams
                posX += parentParams.x
                posY += parentParams.y
            }
            xs[i] = posX + (b.width / 2f)
            ys[i] = posY + (b.height / 2f)
        }

        // إرسال اللمسات المتعددة
        touchService?.injectMultiTouch(ids, xs, ys)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        floatMenu?.let { windowManager.removeView(it) }
        editPanel?.let { windowManager.removeView(it) }
        buttons.forEach { windowManager.removeView(it) }
        mouseArea?.let { windowManager.removeView(it) }
        dPadView?.let { windowManager.removeView(it) }
    }
}

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
    override fun onTouchEvent(event: MotionEvent): Boolean = false 
}

enum class ActionType { TAP, HOLD, DRAG }

class KeyButton(context: Context, val keyCode: Int, val label: String, var actionType: ActionType, private val btnSize: Int) : View(context) {
    var isEditMode = true
    var isActionPressed = false

    init { 
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#55000000"))
        shape.setStroke(3, Color.WHITE)
        background = shape
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = if (isActionPressed) Color.GREEN else Color.WHITE
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(label, width / 2f, height / 2f + 8, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { 
        setMeasuredDimension(btnSize, btnSize) 
    }
}
