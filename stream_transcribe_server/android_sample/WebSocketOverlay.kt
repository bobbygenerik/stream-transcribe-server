/*
WebSocket client (OkHttp) to receive caption JSON and an overlay helper to show text.
Copy into your AccessibilityService/Activity and adapt.
*/

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject

class CaptionOverlay(val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val tv = TextView(ctx).apply { textSize = 18f }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.BOTTOM }

    fun show() {
        wm.addView(tv, params)
    }

    fun hide() {
        try { wm.removeView(tv) } catch (e: Exception) {}
    }

    fun updateText(t: String) {
        GlobalScope.launch(Dispatchers.Main) { tv.text = t }
    }
}

fun startWs(sessionId: String, overlay: CaptionOverlay) {
    val client = OkHttpClient()
    val request = Request.Builder().url("ws://127.0.0.1:5000/ws/$sessionId").build()
    client.newWebSocket(request, object: WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val o = JSONObject(text)
                val t = o.getString("text")
                overlay.updateText(t)
            } catch (e: Exception) {}
        }
    })
}
