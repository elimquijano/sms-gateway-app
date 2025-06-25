package com.elimquijano.smsgatewayapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
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

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(SmsGatewayService.EXTRA_LOG_MESSAGE)?.let {
                addLog(it)
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
        }

        binding.btnToggleService.setOnClickListener { toggleService() }
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver, IntentFilter(SmsGatewayService.ACTION_LOG_BROADCAST)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun toggleService() {
        if (SmsGatewayService.isServiceRunning) {
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
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "No se pudo abrir la configuración automáticamente. Por favor, ve a Configuración -> Batería y desactiva la optimización para esta app.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(false)
            .show()
    }

    private fun startGatewayService() {
        addLog("Iniciando servicio...")
        val intent = Intent(this, SmsGatewayService::class.java).apply {
            putExtra("EXTRA_FULL_URL", binding.etUrl.text.toString().trim())
        }
        ContextCompat.startForegroundService(this, intent)
        updateButtonState()
    }

    private fun stopGatewayService() {
        addLog("Deteniendo servicio...")
        stopService(Intent(this, SmsGatewayService::class.java))
        updateButtonState()
    }

    private fun updateButtonState() {
        if (SmsGatewayService.isServiceRunning) {
            binding.btnToggleService.text = "Detener Servicio"
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_error))
            binding.etUrl.isEnabled = false
        } else {
            binding.btnToggleService.text = "Iniciar Servicio"
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_primary))
            binding.etUrl.isEnabled = true
        }
    }

    private fun addLog(message: String) {
        val currentLogs = binding.tvLogs.text.toString()
        val lines = currentLogs.lines()
        val last100Lines = if (lines.size > 100) lines.takeLast(100).joinToString("\n") else currentLogs

        binding.tvLogs.text = if (last100Lines.isEmpty()) message else "$last100Lines\n$message"
        binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
    }
}