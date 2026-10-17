package com.companion.hollowai
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.companion.hollowai.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vessel_prefs", Context.MODE_PRIVATE)
        binding.etApiKey.setText(prefs.getString("gemini_key", ""))

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_key", key).apply()
            Toast.makeText(this, "API Key saved locally!", Toast.LENGTH_SHORT).show()
        }

        binding.btnOverlayPerm.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            } else {
                Toast.makeText(this, "Floating permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'Vessel AI' and turn it ON", Toast.LENGTH_LONG).show()
        }

        binding.btnToggleService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Floating Permission first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isRunning) {
                stopService(Intent(this, FloatingCompanionService::class.java))
                isRunning = false
                binding.btnToggleService.text = "START COMPANION"
                binding.btnToggleService.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
            } else {
                startService(Intent(this, FloatingCompanionService::class.java))
                isRunning = true
                binding.btnToggleService.text = "STOP COMPANION"
                binding.btnToggleService.setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            binding.btnOverlayPerm.text = "✓ Floating Permission Granted"
            binding.btnOverlayPerm.alpha = 0.6f
        } else {
            binding.btnOverlayPerm.text = "1. Grant Floating Permission"
            binding.btnOverlayPerm.alpha = 1.0f
        }
    }
}