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
            Toast.makeText(this@MainActivity, "المحرك جاهز للانطلاق", Toast.LENGTH_SHORT).show()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
            KeyMapperService.touchService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // واجهة التطبيق الرئيسية
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "ALPHA Keymapper\n==============="
            setTextColor(Color.WHITE)
            textSize = 24f
        }
        layout.addView(title)

        val btnOverlay = Button(this).apply {
            text = "1. منح صلاحية الظهور فوق التطبيقات"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
        layout.addView(btnOverlay)

        val btnAccess = Button(this).apply {
            text = "2. تفعيل خدمة إمكانية الوصول (Accessibility)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccess)

        val btnShizuku = Button(this).apply {
            text = "3. بدء تشغيل Shizuku وربط الخدمة"
            setOnClickListener {
                if (checkShizukuPermission()) {
                    bindUserService()
                    startService(Intent(this@MainActivity, KeyMapperService::class.java))
                    Toast.makeText(this@MainActivity, "تم الربط، افتح اللعبة الآن", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(btnShizuku)

        val btnStop = Button(this).apply {
            text = "إيقاف الأداة (إغلاق)"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopService(Intent(this@MainActivity, KeyMapperService::class.java))
                finish()
            }
        }
        layout.addView(btnStop)

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

// ============================================================================
// خدمة الـ Accessibility (القلب النابض للـ Overlay)
// ============================================================================
class KeyMapperService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var floatMenu: View? = null
    private var editPanel: LinearLayout? = null
    private var mouseArea: View? = null
    private val buttons = mutableListOf<KeyButton>()
    private var isEditMode = true
    private var isPanelVisible = false

    companion object {
        var touchService: ITouchService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatMenu()
    }

    // القائمة العائمة الصغيرة اللي بتظهر دايماً
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
            setOnClickListener {
                toggleEditPanel()
            }
        }
        
        // عشان تقدر تسحب زرار الإعدادات
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

    // النافذة الجانبية اللي فيها الإعدادات
    private fun toggleEditPanel() {
        if (isPanelVisible) {
            editPanel?.let { windowManager.removeView(it) }
            isPanelVisible = false
            return
        }

        val params = WindowManager.LayoutParams(
            400, // عرض النافذة
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

            val title = TextView(this@KeyMapperService).apply {
                text = "إعدادات الأداة"
                setTextColor(Color.WHITE); textSize = 18f
            }
            addView(title)

            val btnToggle = Button(this@KeyMapperService).apply {
                text = if (isEditMode) "الوضع الحالي: تعديل\n(اضغط للعب)" else "الوضع الحالي: لعب\n(اضغط للتعديل)"
                setOnClickListener {
                    isEditMode = !isEditMode
                    text = if (isEditMode) "الوضع الحالي: تعديل\n(اضغط للعب)" else "الوضع الحالي: لعب\n(اضغط للتعديل)"
                    updateButtonsMode()
                }
            }
            addView(btnToggle)

            val btnAddW = Button(this@KeyMapperService).apply {
                text = "إضافة زر W"
                setOnClickListener { addKeyButton(KeyEvent.KEYCODE_W, 200f, 200f, "W", ActionType.TAP) }
            }
            addView(btnAddW)

            val btnAddFire = Button(this@KeyMapperService).apply {
                text = "إضافة زر إطلاق (Click)"
                setOnClickListener { addKeyButton(KeyEvent.KEYCODE_BUTTON_L1, 800f, 400f, "Fire", ActionType.TAP) }
            }
            addView(btnAddFire)
            
            val btnAddAim = Button(this@KeyMapperService).apply {
                text = "إضافة زر تصويب (Hold)"
                setOnClickListener { addKeyButton(KeyEvent.KEYCODE_BUTTON_R1, 700f, 200f, "Aim", ActionType.HOLD) }
            }
            addView(btnAddAim)

            val btnAddMouse = Button(this@KeyMapperService).apply {
                text = "تحديد منطقة الماوس (يمين)"
                setOnClickListener { addMouseArea() }
            }
            addView(btnAddMouse)

            val btnHide = Button(this@KeyMapperService).apply {
                text = "إخفاء كل الأزرار (أثناء اللعب)"
                setOnClickListener { hideAllButtons() }
            }
            addView(btnHide)
        }

        windowManager.addView(editPanel, params)
        isPanelVisible = true
    }

    private fun addKeyButton(keyCode: Int, x: Float, y: Float, label: String, action: ActionType) {
        val btn = KeyButton(this, keyCode, label, action)
        btn.x = x; btn.y = y
        btn.isEditMode = isEditMode
        
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
                            v.x = initialX + (event.rawX - initialTouchX)
                            v.y = initialY + (event.rawY - initialTouchY)
                        }
                    }
                }
                return true
            }
        })
        
        // ضغطة طويلة لتغيير نوع الضغطة
        btn.setOnLongClickListener {
            if (isEditMode) {
                btn.actionType = when(btn.actionType) {
                    ActionType.TAP -> ActionType.HOLD
                    ActionType.HOLD -> ActionType.DRAG
                    ActionType.DRAG -> ActionType.TAP
                }
                Toast.makeText(this, "تم تغيير النوع إلى: ${btn.actionType}", Toast.LENGTH_SHORT).show()
                btn.invalidate()
            }
            true
        }

        val params = WindowManager.LayoutParams(
            150, 150,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.toInt(); this.y = y.toInt()
        }

        windowManager.addView(btn, params)
        buttons.add(btn)
    }

    private fun addMouseArea() {
        if (mouseArea != null) return
        val params = WindowManager.LayoutParams(
            600, // عرض المنطقة
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        mouseArea = MouseInterceptorView(this) { dx, dy ->
            val centerX = 1200f
            val centerY = 400f
            if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
                touchService?.injectSwipe(centerX, centerY, centerX + (dx * 15), centerY + (dy * 15), 50)
            }
        }.apply {
            setBackgroundColor(Color.parseColor("#22FF0000")) // لون أحمر شفاف عشان تشوفها وانت بتعدل
        }

        windowManager.addView(mouseArea, params)
        Toast.makeText(this, "تمت إضافة منطقة الماوس في يمين الشاشة", Toast.LENGTH_SHORT).show()
    }

    private fun hideAllButtons() {
        buttons.forEach { it.visibility = View.GONE }
        mouseArea?.visibility = View.GONE
        isEditMode = false
        Toast.makeText(this, "تم إخفاء الأزرار. للعودة اضغط على الترس ⚙", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonsMode() {
        buttons.forEach { 
            it.isEditMode = isEditMode
            it.visibility = View.VISIBLE
            it.invalidate() 
        }
        mouseArea?.let {
            it.visibility = View.VISIBLE
            it.setBackgroundColor(if(isEditMode) Color.parseColor("#22FF0000") else Color.TRANSPARENT)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isEditMode) return false
        val btn = buttons.find { it.keyCode == event.keyCode && it.visibility == View.VISIBLE }
        btn?.let {
            if (event.action == KeyEvent.ACTION_DOWN && !it.isActionPressed) {
                it.isActionPressed = true
                sendTouch(it, true)
            } else if (event.action == KeyEvent.ACTION_UP && it.isActionPressed) {
                it.isActionPressed = false
                sendTouch(it, false)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun sendTouch(btn: KeyButton, isDown: Boolean) {
        val pressedButtons = buttons.filter { it.isActionPressed }
        if (pressedButtons.isEmpty()) return

        val actions = IntArray(pressedButtons.size)
        val ids = IntArray(pressedButtons.size)
        val xs = IntArray(pressedButtons.size)
        val ys = IntArray(pressedButtons.size)

        for (i in pressedButtons.indices) {
            val b = pressedButtons[i]
            ids[i] = b.keyCode
            xs[i] = b.x.toInt() + 75
            ys[i] = b.y.toInt() + 75
            
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
        floatMenu?.let { windowManager.removeView(it) }
        editPanel?.let { windowManager.removeView(it) }
        buttons.forEach { windowManager.removeView(it) }
        mouseArea?.let { windowManager.removeView(it) }
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

class KeyButton(context: Context, val keyCode: Int, val label: String, var actionType: ActionType) : View(context) {
    var isEditMode = true
    var isActionPressed = false

    init { background = context.getDrawable(android.R.drawable.ic_menu_add) }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = if (isActionPressed) Color.GREEN else Color.WHITE
            textSize = 40f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("$label\n($actionType)", width / 2f, height / 2f - 10, paint)
    }
    // عرض اسم الزرار ونوعه
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(150, 150) }
}
