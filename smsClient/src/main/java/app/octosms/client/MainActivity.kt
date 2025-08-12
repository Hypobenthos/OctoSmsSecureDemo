package app.octosms.client

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.octosms.commoncrypto.callback.SmsDataCallbackManager
import app.octosms.commoncrypto.config.OctoSmsSdk
import app.octosms.commoncrypto.log.LogManager
import app.octosms.commoncrypto.model.SmsData

class MainActivity : AppCompatActivity() {

    private lateinit var tvSmsInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvSmsInfo = findViewById(R.id.tv_sms_info)
        OctoSmsSdk.init {
            // 注册短信监听器
            smsListener = object : app.octosms.commoncrypto.callback.SmsDataListener {
                override fun onSmsDataReceived(smsData: SmsData, senderApp: String) {
                    val info = """
                    发送者: $senderApp
                    短信内容: ${smsData.message}
                    接收时间: ${smsData.timestamp}
                """.trimIndent()

                    // 更新 UI
                    runOnUiThread {
                        tvSmsInfo.text = info
                    }
                }
            }
        }

    }
}
