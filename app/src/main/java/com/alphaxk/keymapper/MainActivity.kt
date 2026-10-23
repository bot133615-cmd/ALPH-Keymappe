package com.alphaxk.keymapper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private var touchService: ITouchService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            touchService = ITouchService.Stub.asInterface(service)
            Toast.makeText(this@MainActivity, "الخدمة جاهزة", Toast.LENGTH_SHORT).show()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = FrameLayout(this)
        val btn = Button(this).apply {
            text = "بدء الخدمة"
            setOnClickListener {
                if (checkOverlayPermission() && checkShizukuPermission()) {
                    val intent = Intent(this@MainActivity, KeyMapperService::class.java)
                    startService(intent)
                }
            }
        }
        layout.addView(btn)
        setContentView(layout)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bindUserService()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return false
        }
        return true
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            bindUserService()
            return true
        } else {
            Shizuku.requestPermission(1001)
            return false
        }
    }

    private fun bindUserService() {
        val args = Shizuku.UserServiceArgs(
            ComponentName(packageName, TouchService::class.java.name)
        ).daemon(false).processNameSuffix("touch_service").debuggable(false).version(1)
        Shizuku.bindUserService(args, connection)
    }
}
