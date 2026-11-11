package com.oxendorax1000

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val blue = Color.rgb(20, 115, 255)
    private val bg = Color.rgb(7, 15, 32)
    private val card = Color.rgb(15, 29, 54)
    private val white = Color.WHITE
    private val handler = Handler(Looper.getMainLooper())
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var connected = false
    private var language = "en"
    private lateinit var status: TextView
    private lateinit var signal: TextView
    private lateinit var progress: ProgressBar
    private lateinit var details: TextView
    private lateinit var connectButton: Button
    private lateinit var scanButton: Button

    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        language = getPreferences(0).getString("language", "en") ?: "en"
        buildScreen()
        requestBluetoothPermissionIfNeeded()
    }

    private fun t(en: String, fa: String, ar: String): String =
        when (language) { "fa" -> fa; "ar" -> ar; else -> en }

    private fun label(text: String, size: Float = 16f): TextView =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(white); gravity = Gravity.CENTER }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply { this.text = text; setOnClickListener { action() } }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("☰") { showMenu() }, LinearLayout.LayoutParams(58, 52))
        top.addView(label("OXENDORAX 1000", 22f), LinearLayout.LayoutParams(0, 60, 1f))
        page.addView(top)
        page.addView(label(t("METAL DETECTOR", "فلزیاب", "كاشف المعادن"), 13f))
        status = label(t("Disconnected", "قطع اتصال", "غير متصل"), 17f)
        page.addView(panel(status))
        page.addView(label(t("LIVE SIGNAL", "سیگنال زنده", "الإشارة المباشرة"), 14f))
        signal = label("--", 48f)
        signal.setTextColor(blue)
        page.addView(panel(signal))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
        }
        page.addView(progress, LinearLayout.LayoutParams(-1, 18).apply { setMargins(0, 18, 0, 10) })
        details = label(t("Ready to connect to HC-05", "آماده اتصال به HC-05", "جاهز للاتصال بـ HC-05"), 13f)
        page.addView(details)
        connectButton = button(t("CONNECT HC-05", "اتصال به HC-05", "اتصال HC-05")) { chooseDevice() }
        page.addView(connectButton)
        scanButton = button(t("START SCAN", "شروع اسکن", "بدء المسح")) { send(if (scanButton.text.toString().contains("STOP")) "STOP" else "START") }
        page.addView(scanButton)
        page.addView(button(t("GROUND BALANCE", "بالانس زمین", "موازنة الأرض")) { startCalibration() })
        page.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        page.addView(label("Bluetooth SPP • Arduino Nano • HC-05", 11f))
        setContentView(page)
        refreshLabels()
    }

    private fun panel(v: View): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(12, 14, 12, 14)
        setBackgroundColor(card)
        addView(v, LinearLayout.LayoutParams(-1, -2))
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 8, 0, 12)
        layoutParams = lp
    }

    private fun refreshLabels() {
        if (!::status.isInitialized) return
        status.text = if (connected) t("Connected to HC-05", "متصل به HC-05", "متصل بـ HC-05")
                      else t("Disconnected", "قطع اتصال", "غير متصل")
        connectButton.text = if (connected) t("DISCONNECT", "قطع اتصال", "قطع الاتصال")
                             else t("CONNECT HC-05", "اتصال به HC-05", "اتصال HC-05")
        scanButton.text = t("START SCAN", "شروع اسکن", "بدء المسح")
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 101)
        }
    }

    private fun chooseDevice() {
        if (connected) { disconnect(); return }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { toast("Bluetooth is not supported"); return }
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissionIfNeeded(); return
            }
            val devices = adapter.bondedDevices.toList()
            if (devices.isEmpty()) { toast("Pair HC-05 in Android Bluetooth settings first"); return }
            val names = devices.map { "${it.name ?: "Unknown"} (${it.address})" }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Select paired device").setItems(names) { _, which -> connect(devices[which]) }
                .setNegativeButton("Cancel", null).show()
        } catch (e: Exception) { toast(e.message ?: "Bluetooth error") }
    }

    private fun connect(device: BluetoothDevice) {
        details.text = "Connecting to ${device.name ?: device.address}..."
        thread {
            try {
                val s = device.createRfcommSocketToServiceRecord(spp)
                s.connect()
                socket = s; output = s.outputStream; connected = true
                handler.post { refreshLabels(); details.text = "Bluetooth serial connection ready" }
                listen(s)
            } catch (e: Exception) {
                connected = false
                handler.post { refreshLabels(); details.text = "Connection failed: ${e.message}" }
            }
        }
    }

    private fun listen(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            while (connected) {
                val line = reader.readLine() ?: break
                handler.post { handleMessage(line.trim()) }
            }
        } catch (_: Exception) { }
        handler.post { connected = false; refreshLabels(); details.text = "Connection closed" }
    }

    private fun handleMessage(message: String) {
        when {
            message == "READY" -> details.text = "Device ready"
            message.startsWith("CAL_PROGRESS:") -> {
                val value = message.substringAfter(":").toIntOrNull()?.coerceIn(0, 100) ?: return
                progress.progress = value
                details.text = "Ground balance: $value%"
            }
            message == "CAL_DONE" -> { progress.progress = 100; details.text = "Ground balance completed by device" }
            message.startsWith("SIGNAL:") -> signal.text = message.substringAfter(":")
            message.startsWith("ERROR:") -> details.text = message.substringAfter(":")
            else -> details.text = message
        }
    }

    private fun startCalibration() {
        if (!connected) { toast("Connect HC-05 first"); return }
        details.text = "Preparing..."
        object : Runnable {
            var seconds = 3
            override fun run() {
                if (seconds > 0) {
                    details.text = "Calibration starts in $seconds"
                    seconds--; handler.postDelayed(this, 1000)
                } else {
                    progress.progress = 0
                    send("CALIBRATE")
                    details.text = "Waiting for Arduino calibration response..."
                }
            }
        }.run()
    }

    private fun send(command: String) {
        if (!connected || output == null) { toast("Connect HC-05 first"); return }
        try {
            output?.write((command + "\n").toByteArray())
            if (command == "START") details.text = "Scan command sent"
            if (command == "STOP") details.text = "Stop command sent"
        } catch (e: Exception) { toast("Send failed: ${e.message}") }
    }

    private fun disconnect() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null; output = null; refreshLabels()
        details.text = "Disconnected"
    }

    private fun showMenu() {
        val items = arrayOf("Language / زبان / اللغة", "About")
        AlertDialog.Builder(this).setTitle("OXENDORAX 1000").setItems(items) { _, which ->
            if (which == 0) chooseLanguage()
            else AlertDialog.Builder(this).setMessage("OXENDORAX 1000\nAndroid companion for Arduino Nano + HC-05.\nDetection accuracy depends on the sensor, firmware and hardware calibration.")
                .setPositiveButton("OK", null).show()
        }.show()
    }

    private fun chooseLanguage() {
        val options = arrayOf("English", "فارسی", "العربية")
        AlertDialog.Builder(this).setTitle("Language").setSingleChoiceItems(options, when(language){"fa"->1;"ar"->2;else->0}) { dialog, which ->
            language = arrayOf("en", "fa", "ar")[which]
            getPreferences(0).edit().putString("language", language).apply()
            dialog.dismiss(); buildScreen()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() { disconnect(); super.onDestroy() }
}
