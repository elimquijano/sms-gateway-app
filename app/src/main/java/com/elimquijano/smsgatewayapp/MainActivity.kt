package com.elimquijano.smsgatewayapp

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View // <-- IMPORT QUE FALTABA
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.elimquijano.smsgatewayapp.databinding.ActivityMainBinding
import com.elimquijano.smsgatewayapp.gateway.SmsGatewayService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appSettings: AppSettings

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SmsGatewayService.ACTION_LOG_UPDATE -> {
                    intent.getStringExtra(SmsGatewayService.EXTRA_LOG_MESSAGE)?.let {
                        addLog(it)
                    }
                }
                SmsGatewayService.ACTION_SERVICE_STOPPED -> {
                    setUIStateToStopped()
                }
            }
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkBatteryOptimizations()
        } else {
            Toast.makeText(this, "Se requieren todos los permisos para funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSettings = AppSettings(this)

        lifecycleScope.launch {
            binding.etUrl.setText(appSettings.getServerUrl.first())
            binding.tvLogs.text = appSettings.getLogs.first()
            scrollToBottom()
        }

        binding.btnToggleService.setOnClickListener { toggleService() }
        binding.btnClearLogs.setOnClickListener { clearLogs() }

        val intentFilter = IntentFilter().apply {
            addAction(SmsGatewayService.ACTION_LOG_UPDATE)
            addAction(SmsGatewayService.ACTION_SERVICE_STOPPED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStateReceiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        if (isServiceRunning()) {
            setUIStateToRunning()
        } else {
            setUIStateToStopped()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStateReceiver)
    }

    // ===================================================================
    // ===== ESTA ES LA FUNCIÓN CORREGIDA ================================
    // ===================================================================
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // CORRECCIÓN: Arreglado el error de tipeo y el manejo de nulos.
        return manager.getRunningServices(Integer.MAX_VALUE)
            ?.any { it.service.className == SmsGatewayService::class.java.name }
            ?: false
    }

    private fun toggleService() {
        if (isServiceRunning()) {
            stopGatewayService()
        } else {
            val url = binding.etUrl.text.toString().trim()
            if (url.isBlank() || (!url.startsWith("ws://") && !url.startsWith("wss://"))) {
                binding.layoutUrl.error = "URL inválida. Debe empezar con ws:// o wss://"
                return
            }
            binding.layoutUrl.error = null

            lifecycleScope.launch { appSettings.saveServerUrl(url) }
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkBatteryOptimizations()
        }
    }

    private fun checkBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryOptimizationDialog()
        } else {
            startGatewayService()
        }
    }

    private fun showBatteryOptimizationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Optimización de Batería Requerida")
            .setMessage("Para que el servicio funcione de forma continua, es crucial desactivar la optimización de batería para esta app.\n\nEn la siguiente pantalla, busca 'SmsGatewayApp' y selecciona 'No optimizar'.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir la configuración automáticamente. Por favor, hazlo manualmente.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(false)
            .show()
    }

    private fun startGatewayService() {
        addLog("Iniciando servicio...")
        setUIStateToRunning()

        val intent = Intent(this, SmsGatewayService::class.java).apply {
            putExtra("EXTRA_FULL_URL", binding.etUrl.text.toString().trim())
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopGatewayService() {
        addLog("Deteniendo servicio...")
        setUIStateToStopped()

        stopService(Intent(this, SmsGatewayService::class.java))
    }

    private fun setUIStateToRunning() {
        binding.btnToggleService.text = "Detener Servicio"
        val redColor = ContextCompat.getColor(this, R.color.button_stop_red)
        binding.btnToggleService.backgroundTintList = ColorStateList.valueOf(redColor)
        binding.btnToggleService.setTextColor(Color.WHITE)
        binding.etUrl.isEnabled = false
    }

    private fun setUIStateToStopped() {
        binding.btnToggleService.text = "Iniciar Servicio"
        val greenColor = ContextCompat.getColor(this, R.color.button_start_green)
        binding.btnToggleService.backgroundTintList = ColorStateList.valueOf(greenColor)
        binding.btnToggleService.setTextColor(Color.BLACK)
        binding.etUrl.isEnabled = true
    }

    private fun addLog(message: String) {
        lifecycleScope.launch {
            appSettings.appendLog(message)
            binding.tvLogs.text = appSettings.getLogs.first()
            scrollToBottom()
        }
    }

    private fun clearLogs() {
        lifecycleScope.launch {
            appSettings.clearLogs()
            binding.tvLogs.text = ""
            Toast.makeText(this@MainActivity, "Logs borrados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToBottom() {
        binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
    }
}