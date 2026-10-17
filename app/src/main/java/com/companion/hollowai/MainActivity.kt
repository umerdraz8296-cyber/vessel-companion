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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prefs = getSharedPreferences("vessel_prefs", Context.MODE_PRIVATE)
        binding.etApiKey.setText(prefs.getString("gemini_key", ""))
        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_key", key).apply()
            Toast.makeText(this, "Key saved to phone storage", Toast.LENGTH_SHORT).show()
        }
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    binding.switchOverlay.isChecked = false
                } else {
                    startService(Intent(this, FloatingCompanionService::class.java))
                }
            } else {
                stopService(Intent(this, FloatingCompanionService::class.java))
            }
        }
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'Vessel AI' and enable it", Toast.LENGTH_LONG).show()
        }
    }
}