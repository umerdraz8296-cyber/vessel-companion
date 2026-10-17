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
import kotlin.math.abs

class FloatingCompanionService : Service() {
    companion object {
        var activeInstance: FloatingCompanionService? = null
        fun onTextReceived(text: String) {
            activeInstance?.processText(text)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var ivKnight: ImageView
    private lateinit var cardCorrection: View
    private lateinit var tvCorrectedText: TextView
    private lateinit var tvCardHeader: TextView
    private lateinit var btnCopy: View
    private lateinit var btnDismiss: View
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastAnalyzedText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_companion, null)

        ivKnight = floatingView.findViewById(R.id.ivKnight)
        cardCorrection = floatingView.findViewById(R.id.cardCorrection)
        tvCorrectedText = floatingView.findViewById(R.id.tvCorrectedText)
        tvCardHeader = floatingView.findViewById(R.id.tvCardHeader)
        btnCopy = floatingView.findViewById(R.id.btnCopy)
        btnDismiss = floatingView.findViewById(R.id.btnDismiss)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

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
        setupInteractiveKnight(params)

        btnDismiss.setOnClickListener { cardCorrection.visibility = View.GONE }
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Corrected", tvCorrectedText.text))
            cardCorrection.visibility = View.GONE
        }
    }

    private fun setupInteractiveKnight(params: WindowManager.LayoutParams) {
        val bobbing = ObjectAnimator.ofFloat(ivKnight, "translationY", 0f, -14f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        bobbing.start()

        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        var startTouchTime = 0L

        ivKnight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    startTouchTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - startTouchTime
                    val diffX = abs(event.rawX - touchX)
                    val diffY = abs(event.rawY - touchY)
                    // If tap instead of drag
                    if (duration < 250 && diffX < 15 && diffY < 15) {
                        onKnightTapped()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onKnightTapped() {
        if (cardCorrection.visibility == View.VISIBLE) {
            cardCorrection.visibility = View.GONE
        } else {
            tvCardHeader.text = "✦ Hallownest Companion"
            tvCorrectedText.text = "I am listening to your typing across all apps. Make a spelling or grammar mistake to summon a correction!"
            cardCorrection.visibility = View.VISIBLE
        }
    }

    fun processText(inputText: String) {
        if (inputText == lastAnalyzedText || inputText.split(" ").size < 2) return
        lastAnalyzedText = inputText

        val prefs = getSharedPreferences("vessel_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_key", "") ?: ""

        if (apiKey.isBlank()) {
            tvCardHeader.text = "⚠ Vessel Needs Key"
            tvCorrectedText.text = "Open the Vessel AI app and enter your Gemini API key."
            cardCorrection.visibility = View.VISIBLE
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val prompt = "Fix spelling and grammar in this sentence. Output ONLY the corrected text. If the sentence has NO mistakes, reply ONLY with 'CLEAN'. Text: \"$inputText\""
                val json = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    ))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val reply = JSONObject(responseBody)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    withContext(Dispatchers.Main) {
                        if (!reply.equals("CLEAN", ignoreCase = true) && reply.isNotBlank()) {
                            tvCardHeader.text = "✦ Correction Suggested"
                            tvCorrectedText.text = reply
                            cardCorrection.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvCardHeader.text = "✕ Gemini Error"
                    tvCorrectedText.text = "Connection error: ${e.localizedMessage ?: "Check API Key"}"
                    cardCorrection.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        scope.cancel()
    }
}