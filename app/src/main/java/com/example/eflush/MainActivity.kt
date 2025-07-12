
// Stack Universal

package com.example.eflush

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : AppCompatActivity() {
    private var mqttClient: MqttClient? = null
    private var isConnected = false
    private val statusHandler = Handler(Looper.getMainLooper())
    private var lastStatusReceived: Long = 0
    private val statusTimeoutMs = 10_000L
    private var ignoreSwitchChange = false
    private var isScheduleActive = false
    private var isRelayOnBySchedule = false

    private val serverUri = ""
    private val username = ""
    private val passwordStr = "" // Ganti nama variabel agar tidak bentrok dengan property

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val greeting = findViewById<TextView>(R.id.greeting)
        val connectionStatus = findViewById<View>(R.id.connectionStatus)
        val deviceStatusText = findViewById<TextView>(R.id.deviceStatusText)
        val deviceStatus = findViewById<View>(R.id.deviceStatus)
        val deviceSwitch = findViewById<Switch>(R.id.deviceSwitch)
        val progressPager = findViewById<ViewPager2>(R.id.progressPager)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)

        greeting.text = "Hi! I’m thrilled to welcome you to eFlush"

        val layouts = listOf(R.layout.item_progress_ring, R.layout.item_schedule)
        progressPager.adapter = ProgressPagerAdapter(layouts)

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        fun connectMqtt() {
            Thread {
                try {
                    val clientId = "eFlushApp" + System.currentTimeMillis()
                    mqttClient = MqttClient(serverUri, clientId, null)
                    val options = MqttConnectOptions().apply {
                        userName = username
                        password = passwordStr.toCharArray()
                        isAutomaticReconnect = true
                        isCleanSession = true
                        socketFactory = sslContext.socketFactory
                    }
                    mqttClient?.setCallback(object : MqttCallback {
                        override fun connectionLost(cause: Throwable?) {
                            runOnUiThread { updateStatus(false, false) }
                        }
                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            if (topic == "eflush/status") {
                                lastStatusReceived = System.currentTimeMillis()
                                val statusJson = message?.toString() ?: "{}"
                                var status = "OFF"
                                var schedule = "manual"
                                var source = "manual"
                                try {
                                    val json = JSONObject(statusJson)
                                    status = json.optString("status", "OFF")
                                    schedule = json.optString("schedule", "manual")
                                    source = json.optString("source", "manual")
                                } catch (e: Exception) { /* fallback ke string lama */ }

                                isScheduleActive = (schedule == "auto")
                                isRelayOnBySchedule = (schedule == "auto" && status == "ON")

                                runOnUiThread {
                                    ignoreSwitchChange = true
                                    deviceSwitch.isChecked = (status == "ON")
                                    ignoreSwitchChange = false
                                    updateStatus(true, true)
                                    swipeRefresh.isRefreshing = false // Hentikan animasi refresh setelah status update
                                }
                            } else if (topic == "eflush/schedule") {
                                isScheduleActive = true
                            } else if (topic == "eflush/reset_schedule") {
                                isScheduleActive = false
                            }
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                    mqttClient?.connect(options)
                    isConnected = true
                    mqttClient?.subscribe("eflush/status", 0)
                    mqttClient?.subscribe("eflush/schedule", 0)
                    mqttClient?.subscribe("eflush/reset_schedule", 0)
                    runOnUiThread { updateStatus(true, false) }
                } catch (e: MqttException) {
                    e.printStackTrace()
                    isConnected = false
                    runOnUiThread {
                        updateStatus(false, false)
                        swipeRefresh.isRefreshing = false
                    }
                }
            }.start()
        }

        // Inisialisasi MQTT pertama kali
        connectMqtt()

        // Fitur swipe-to-refresh
        swipeRefresh.setOnRefreshListener {
            Thread {
                try {
                    mqttClient?.disconnect()
                } catch (_: Exception) {}
                connectMqtt()
            }.start()
        }

        val btnExit = findViewById<Button>(R.id.btnExit)
        btnExit.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Aplikasi")
                .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
                .setPositiveButton("Ya") { _, _ ->
                    finishAffinity()
                }
                .setNegativeButton("Batal", null)
                .show()
        }


        // Cek timeout status board
        val statusCheckRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val isBoardOnline = (now - lastStatusReceived) < statusTimeoutMs
                updateStatus(isConnected, isBoardOnline)
                statusHandler.postDelayed(this, 2000)
            }
        }
        statusHandler.post(statusCheckRunnable)

        // ON/OFF relay manual via MQTT dengan dua kondisi dialog konfirmasi
        deviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchChange) return@setOnCheckedChangeListener
            if (isConnected && deviceSwitch.isEnabled) {
                if (isScheduleActive && isChecked) {
                    AlertDialog.Builder(this)
                        .setTitle("Konfirmasi")
                        .setMessage("Anda sudah mensetting jadwal penyiraman otomatis. Apakah mau beralih ke manual?")
                        .setPositiveButton("Iya") { _, _ ->
                            Thread {
                                try {
                                    mqttClient?.publish("eflush/reset_schedule", MqttMessage("RESET".toByteArray()))
                                    mqttClient?.publish("eflush/relay1", MqttMessage("ON".toByteArray()))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }.start()
                            isScheduleActive = false
                        }
                        .setNegativeButton("Tidak") { dialog, _ ->
                            ignoreSwitchChange = true
                            deviceSwitch.isChecked = false
                            ignoreSwitchChange = false
                            dialog.dismiss()
                        }
                        .show()
                } else if (isScheduleActive && isRelayOnBySchedule && !isChecked) {
                    AlertDialog.Builder(this)
                        .setTitle("Konfirmasi")
                        .setMessage("Anda sedang dalam jadwal penyiraman otomatis. Apakah anda mau mematikannya?")
                        .setPositiveButton("Iya") { _, _ ->
                            Thread {
                                try {
                                    mqttClient?.publish("eflush/reset_schedule", MqttMessage("RESET".toByteArray()))
                                    mqttClient?.publish("eflush/relay1", MqttMessage("OFF".toByteArray()))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }.start()
                            isScheduleActive = false
                        }
                        .setNegativeButton("Tidak") { dialog, _ ->
                            ignoreSwitchChange = true
                            deviceSwitch.isChecked = true
                            ignoreSwitchChange = false
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    val topic = "eflush/relay1"
                    val payload = if (isChecked) "ON" else "OFF"
                    Thread {
                        try {
                            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }



        // Handler untuk update progress ring & jadwal
        fun setupSchedulePage() {
            val dateText = progressPager.findViewById<TextView>(R.id.dateText)
            val timeOnSpinner = progressPager.findViewById<Spinner>(R.id.timeOnSpinner)
            val timeOffSpinner = progressPager.findViewById<Spinner>(R.id.timeOffSpinner)
            val btnSaveSchedule = progressPager.findViewById<Button>(R.id.btnSaveSchedule)

            // Tanggal hari ini (selalu update otomatis)
            val today = Date()
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            val todayStr = dateFormat.format(today)
            dateText?.text = todayStr

            // ISI SPINNER JAM (00:00 - 23:59, hanya jam yang akan datang)
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            val filteredTimeList = mutableListOf<String>()
            for (h in 0..23) {
                for (m in 0..59) {
                    if (h > currentHour || (h == currentHour && m >= currentMinute)) {
                        filteredTimeList.add(String.format("%02d:%02d", h, m))
                    }
                }
            }
            val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filteredTimeList)
            timeOnSpinner?.adapter = timeAdapter
            timeOffSpinner?.adapter = timeAdapter

            // Tombol Simpan Jadwal
            btnSaveSchedule?.setOnClickListener {
                val on = timeOnSpinner?.selectedItem?.toString() ?: ""
                val off = timeOffSpinner?.selectedItem?.toString() ?: ""

                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val formattedDate = outputFormat.format(today)

                val payload = """
        {
          "date": "$formattedDate",
          "on": "$on",
          "off": "$off"
        }
    """.trimIndent()

                if (isConnected && btnSaveSchedule.isEnabled) {
                    Thread {
                        try {
                            mqttClient?.publish("eflush/schedule", MqttMessage(payload.toByteArray()))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                    isScheduleActive = true
                    Toast.makeText(this@MainActivity, "Jadwal dikirim ke board!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "MQTT belum terkoneksi!", Toast.LENGTH_SHORT).show()
                }
            }
        }


        // Selalu isi adapter Spinner saat page jadwal aktif
        progressPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 1) {
                    progressPager.post { setupSchedulePage() }
                }
            }
        })

        // Juga isi adapter saat pertama kali page jadwal diakses (jika user swipe ke page 1)
        progressPager.post {
            if (progressPager.currentItem == 1) {
                setupSchedulePage()
            }
        }
    }


    // Fungsi update status board & MQTT
    private fun updateStatus(isMqttConnected: Boolean, isBoardOnline: Boolean) {
        val deviceStatusText = findViewById<TextView>(R.id.deviceStatusText)
        val deviceStatus = findViewById<View>(R.id.deviceStatus)
        val deviceSwitch = findViewById<Switch>(R.id.deviceSwitch)
        val connectionStatus = findViewById<View>(R.id.connectionStatus)

        val progressPager = findViewById<ViewPager2>(R.id.progressPager)
        val btnSaveSchedule = progressPager.findViewById<Button>(R.id.btnSaveSchedule)

        if (!isMqttConnected) {
            deviceStatus.setBackgroundResource(R.drawable.status_dot_red)
            connectionStatus.setBackgroundResource(R.drawable.status_dot_red)
            deviceSwitch.isEnabled = false
            btnSaveSchedule?.isEnabled = false
        } else if (isBoardOnline) {
            deviceStatusText.text = "Online"
            deviceStatus.setBackgroundResource(R.drawable.status_dot_green)
            connectionStatus.setBackgroundResource(R.drawable.status_dot_green)
            deviceSwitch.isEnabled = true
            btnSaveSchedule?.isEnabled = true
        } else {
            deviceStatusText.text = "Offline"
            deviceStatus.setBackgroundResource(R.drawable.status_dot_red)
            connectionStatus.setBackgroundResource(R.drawable.status_dot_red)
            deviceSwitch.isEnabled = false
            btnSaveSchedule?.isEnabled = false
        }


        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.isRefreshing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        statusHandler.removeCallbacksAndMessages(null)
        if (mqttClient != null && mqttClient!!.isConnected) {
            mqttClient!!.disconnect()
        }
    }
}