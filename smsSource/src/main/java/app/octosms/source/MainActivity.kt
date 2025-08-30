package app.octosms.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.octosms.commoncrypto.config.OctoSmsSdk
import app.octosms.commoncrypto.config.SecurityConfigManager
import app.octosms.smsserver.BuildConfig

class MainActivity : AppCompatActivity() {

    private lateinit var btnRequestSms: Button
    private lateinit var switchPush: Switch

    // 申请权限 Launcher
    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                permissions[Manifest.permission.READ_SMS] == true
        if (granted) {
            Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "短信权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnRequestSms = findViewById(R.id.btn_request_sms)
        switchPush = findViewById(R.id.switch_push)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        switchPush.isChecked = prefs.getBoolean("push_enabled", false)

        btnRequestSms.setOnClickListener {
            checkAndRequestSmsPermission()
        }

        switchPush.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("push_enabled", isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "推送已开启" else "推送已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }

        val customSecurityConfig = SecurityConfigManager.builder()
            .enablePackageWhitelist(true)
            .enableFingerprintVerification(true)
            .enableDebugMode(BuildConfig.DEBUG)
            .addAllowedApp("app.octoclip.source.sms", "SHA1_FINGERPRINT_1", "SHA1_FINGERPRINT_2")
            .build()

        OctoSmsSdk.init {
            securityConfig = customSecurityConfig
        }
    }

    private fun checkAndRequestSmsPermission() {
        val receiveSmsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readSmsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        if (receiveSmsGranted && readSmsGranted) {
            Toast.makeText(this, "已拥有短信权限", Toast.LENGTH_SHORT).show()
        } else {
            requestSmsPermission.launch(arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ))
        }
    }
}
