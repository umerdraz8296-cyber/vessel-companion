package com.companion.hollowai
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class FloatingCompanionService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var ivKnight: ImageView
    private lateinit var cardCorrection: View
    private lateinit var tvCorrectedText: TextView
    private lateinit var btnCopy: View
    private lateinit var btnDismiss: View
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastAnalyzedText = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("typed_text") ?: return
            if (text != lastAnalyzedText && text.trim().split(" ").size >= 2) {
                lastAnalyzedText = text
                requestGeminiCorrection(text)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_companion, null)

        ivKnight = floatingView.findViewById(R.id.ivKnight)
        cardCorrection = floatingView.findViewById(R.id.cardCorrection)
        tvCorrectedText = floatingView.findViewById(R.id.tvCorrectedText)
        btnCopy = floatingView.findViewById(R.id.btnCopy)
        btnDismiss = floatingView.findViewById(R.id.btnDismiss)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 350
        }

        windowManager.addView(floatingView, params)
        setupAnimations(params)

        btnDismiss.setOnClickListener { cardCorrection.visibility = View.GONE }
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Corrected", tvCorrectedText.text))
            cardCorrection.visibility = View.GONE
        }

        val filter = IntentFilter("ACTION_INPUT_TEXT_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun setupAnimations(params: WindowManager.LayoutParams) {
        ObjectAnimator.ofFloat(ivKnight, "translationY", 0f, -16f, 0f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }.start()

        ivKnight.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun requestGeminiCorrection(inputText: String) {
        val prefs = getSharedPreferences("vessel_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_key", "") ?: ""
        if (apiKey.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                val prompt = "If this has grammatical or spelling mistakes, fix it and return ONLY the corrected sentence. If it is already correct, return EXACTLY 'CLEAN'. Text: \"$inputText\""
                val json = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey").post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val reply = JSONObject(responseBody).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                    withContext(Dispatchers.Main) {
                        if (!reply.equals("CLEAN", ignoreCase = true) && reply.isNotBlank()) {
                            tvCorrectedText.text = reply
                            cardCorrection.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        scope.cancel()
    }
}