package com.elimquijano.smsgatewayapp.gateway // He usado tu package name

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.elimquijano.smsgatewayapp.MainActivity
import com.elimquijano.smsgatewayapp.R
import com.google.gson.Gson
import okhttp3.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SmsGatewayService : Service() {

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var reconnectDelay = 5000L

    // Bandera para gestionar el cierre controlado
    private var isStopping = false

    companion object {
        const val TAG = "SmsGatewayService"
        const val NOTIFICATION_CHANNEL_ID = "SmsGatewayChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_LOG_UPDATE = "com.elimquijano.smsgatewayapp.LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"
        const val ACTION_SERVICE_STOPPED = "com.elimquijano.smsgatewayapp.SERVICE_STOPPED"
        const val ACTION_DISCONNECT_AND_STOP = "com.elimquijano.smsgatewayapp.DISCONNECT_AND_STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Comprobar si la Activity nos ha ordenado detenernos
        if (intent?.action == ACTION_DISCONNECT_AND_STOP) {
            logMessage("Comando de detención recibido. Iniciando cierre limpio...")
            isStopping = true
            reconnectRunnable = null
            reconnectHandler.removeCallbacksAndMessages(null)

            // Si el socket está conectado, lo cerramos. El callback 'onClosed' se encargará de detener el servicio.
            if (webSocket != null) {
                webSocket?.close(1000, "Client requested disconnection")
            } else {
                // Si no había conexión, nos detenemos directamente.
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // 2. Si no, es una orden de inicio normal
        isStopping = false
        val fullUrl = intent?.getStringExtra("EXTRA_FULL_URL")

        if (fullUrl.isNullOrBlank() || (!fullUrl.startsWith("ws://") && !fullUrl.startsWith("wss://"))) {
            logMessage("Error: URL inválida. Deteniendo servicio.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Iniciando conexión..."))

        client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
        connect(fullUrl)

        return START_STICKY
    }

    override fun onDestroy() {
        logMessage("Servicio destruido.")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        super.onDestroy()
    }

    private fun connect(fullUrl: String) {
        if (isStopping) return
        logMessage("Intentando conectar a $fullUrl")
        try {
            val request = Request.Builder().url(fullUrl).build()
            webSocket = client.newWebSocket(request, SmsWebSocketListener())
        } catch (e: IllegalArgumentException) {
            logMessage("Error: La URL '$fullUrl' no es válida. Deteniendo servicio.")
            stopSelf()
        }
    }

    private fun scheduleReconnect(url: String) {
        if (isStopping || reconnectRunnable != null) return
        logMessage("Programando reconexión en ${reconnectDelay / 1000} segundos...")
        reconnectRunnable = Runnable {
            connect(url)
            reconnectDelay = (reconnectDelay * 1.5).toLong().coerceAtMost(60000L)
            reconnectRunnable = null
        }
        reconnectHandler.postDelayed(reconnectRunnable!!, reconnectDelay)
    }

    private inner class SmsWebSocketListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            logMessage("¡Conexión WebSocket abierta!")
            updateNotification("Conectado y esperando tareas")
            reconnectDelay = 5000L
            reconnectHandler.removeCallbacks(reconnectRunnable ?: return)
            reconnectRunnable = null
        }

        override fun onMessage(ws: WebSocket, text: String) {
            logMessage("<- Mensaje recibido: $text")
            try {
                val message = gson.fromJson(text, ServerMessage::class.java)
                if (message.type == "NEW_TASK" && message.payload != null) {
                    handleSmsTask(message.payload)
                }
            } catch (e: Exception) {
                logMessage("Error: No se pudo parsear el mensaje del servidor: ${e.message}")
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            logMessage("Conexión cerrándose...")
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            logMessage("Error de conexión: ${t.message}")
            if (isStopping) {
                stopSelf()
            } else {
                updateNotification("Error de conexión. Reconectando...")
                scheduleReconnect(ws.request().url.toString())
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            logMessage("Conexión cerrada.")
            // Si el servicio se estaba apagando y se confirma el cierre, AHORA nos detenemos.
            if (isStopping) {
                stopSelf()
            } else {
                updateNotification("Desconectado. Reconectando...")
                scheduleReconnect(ws.request().url.toString())
            }
        }
    }

    private fun handleSmsTask(task: SmsTaskPayload) {
        logMessage("Procesando Tarea ${task.taskId} para ${task.numero}")
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(task.mensaje)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(task.numero, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(task.numero, null, task.mensaje, null, null)
            }

            logMessage("ÉXITO: Tarea de SMS ${task.taskId} enviada a la cola del sistema.")
            sendStatusUpdate(ClientStatusUpdate(status = "SENT", taskId = task.taskId))

        } catch (e: Exception) {
            logMessage("FALLO al enviar SMS para ${task.taskId}: ${e.message}")
            val failedUpdate = ClientStatusUpdate(
                status = "FAILED",
                taskId = task.taskId,
                details = e.message ?: "Error desconocido en el dispositivo",
                failedTask = task
            )
            sendStatusUpdate(failedUpdate)
        }
    }

    private fun sendStatusUpdate(update: ClientStatusUpdate) {
        val jsonUpdate = gson.toJson(update)
        logMessage("-> Enviando estado: $jsonUpdate")
        webSocket?.send(jsonUpdate)
    }

    private fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val log = "$timestamp - $message"
        Log.d(TAG, log)
        val intent = Intent(ACTION_LOG_UPDATE).putExtra(EXTRA_LOG_MESSAGE, log)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SMS Gateway Activo")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SMS Gateway Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}