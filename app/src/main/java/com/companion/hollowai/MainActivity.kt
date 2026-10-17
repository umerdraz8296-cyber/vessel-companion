package com.companion.hollowai
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.companion.hollowai.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Subtle bobbing animation on the header character preview
        ObjectAnimator.ofFloat(binding.ivHeaderAvatar, "translationY", 0f, -10f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }.start()

        val prefs = getSharedPreferences("vessel_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_key", "") ?: ""
        binding.etApiKey.setText(savedKey)
        if (savedKey.isNotBlank()) {
            binding.tvApiStatus.text = "Key saved on device storage"
            binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
        }

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_key", key).apply()
            binding.tvApiStatus.text = "Key saved!"
            binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
            Toast.makeText(this, "API Key saved locally", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testGeminiConnection(key)
        }

        binding.btnOverlayPerm.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            } else {
                Toast.makeText(this, "Overlay permission is already active!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Vessel AI' under Downloaded Services", Toast.LENGTH_LONG).show()
        }

        binding.btnToggleService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant Floating Permission first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (FloatingCompanionService.activeInstance != null) {
                stopService(Intent(this, FloatingCompanionService::class.java))
                updateCompanionButton(false)
            } else {
                startService(Intent(this, FloatingCompanionService::class.java))
                updateCompanionButton(true)
            }
        }

        // Live sandbox text entry testing
        binding.etSandbox.doAfterTextChanged { text ->
            if (FloatingCompanionService.activeInstance != null && !text.isNullOrBlank()) {
                FloatingCompanionService.onTextReceived(text.toString())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCards()
        updateCompanionButton(FloatingCompanionService.activeInstance != null)
    }

    private fun refreshPermissionCards() {
        // Check Floating permission
        if (Settings.canDrawOverlays(this)) {
            binding.btnOverlayPerm.text = "ACTIVE ✓"
            binding.btnOverlayPerm.setTextColor(android.graphics.Color.parseColor("#10B981"))
            binding.btnOverlayPerm.isEnabled = false
        } else {
            binding.btnOverlayPerm.text = "GRANT"
            binding.btnOverlayPerm.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            binding.btnOverlayPerm.isEnabled = true
        }

        // Check Accessibility Service status
        if (isAccessibilityEnabled()) {
            binding.btnEnableAccessibility.text = "ACTIVE ✓"
            binding.btnEnableAccessibility.setTextColor(android.graphics.Color.parseColor("#10B981"))
            binding.btnEnableAccessibility.isEnabled = false
        } else {
            binding.btnEnableAccessibility.text = "GRANT"
            binding.btnEnableAccessibility.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            binding.btnEnableAccessibility.isEnabled = true
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, CompanionAccessibilityService::class.java)
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) return true
        }
        return false
    }

    private fun updateCompanionButton(isRunning: Boolean) {
        if (isRunning) {
            binding.btnToggleService.text = "BANISH COMPANION (STOP)"
            binding.btnToggleService.setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
        } else {
            binding.btnToggleService.text = "SUMMON FLOATING KNIGHT"
            binding.btnToggleService.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
        }
    }

    private fun testGeminiConnection(key: String) {
        binding.tvApiStatus.text = "Contacting Gemini..."
        binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#A5B4FC"))

        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", "Say OK")
                        ))
                    ))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        binding.tvApiStatus.text = "✓ Gemini 1.5 Flash Connected Successfully!"
                        binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
                    } else {
                        binding.tvApiStatus.text = "✕ API Error (Code $code). Check Key."
                        binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvApiStatus.text = "✕ Network Error: ${e.localizedMessage}"
                    binding.tvApiStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}