package com.andrewshulgin.audioactivatedwebapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest


class MainActivity : ComponentActivity() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var sharedPref: SharedPreferences? = null
    private var sleepHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), MODE_PRIVATE
        )

        with(sharedPref!!.edit()) {
            if (intent.hasExtra("url")) {
                putString("url", intent.getStringExtra("url"))
            } else if (!sharedPref!!.contains("url")) {
                putString("url", getString(R.string.default_url))
            }
            if (intent.hasExtra("wake_rms")) {
                putInt("wake_rms", intent.getIntExtra("wake_rms", 1000))
            } else if (!sharedPref!!.contains("wake_rms")) {
                putInt("wake_rms", 1000)
            }
            if (intent.hasExtra("wake_timeout")) {
                putLong("wake_timeout", intent.getLongExtra("wake_timeout", 10))
            } else if (!sharedPref!!.contains("wake_timeout")) {
                putLong("wake_timeout", 10)
            }
            apply()
        }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioActivatedWebApp::WifiLock"
        )
        wifiLock.acquire()

        val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AudioActivatedWebApp::WakeLock"
        )

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val audioWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<AudioWorker>().build()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                WorkManager.getInstance(this).enqueue(audioWorkRequest)
            }
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), content = { MainWebView() })
        }
        initialize()
    }

    fun initialize() {
        sleepHandler?.removeCallbacksAndMessages(null)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,

            )
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this,
                object : KeyguardManager.KeyguardDismissCallback() {})
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        wakeLock?.acquire(sharedPref!!.getLong("wake_timeout", 10) * 1000)

        sleepHandler = Handler(Looper.getMainLooper())
        sleepHandler?.postDelayed({
            if (wakeLock!!.isHeld) {
                wakeLock?.release()
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }, sharedPref!!.getLong("wake_timeout", 10) * 1000)
    }


    fun isExternalPowerConnected(): Boolean {
        val intent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val plugged = intent!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB
    }

    override fun onResume() {
        super.onResume()
        initialize()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun MainWebView() {
        val activity = this
        val url = sharedPref!!.getString("url", "about:blank")
        AndroidView(factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                addJavascriptInterface(WebAppInterface(activity), "Android")
                webViewClient = StableWebViewClient(url!!)
                loadUrl(url)
            }
        }, update = {
            it.loadUrl(url!!)
        })
    }
}
