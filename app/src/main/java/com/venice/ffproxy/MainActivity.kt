package com.venice.ffproxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvEndpoint: TextView
    private lateinit var etPort: EditText
    private lateinit var swProxy: MaterialSwitch
    private lateinit var swMitm: MaterialSwitch
    private lateinit var adapter: RuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CaStore.init(filesDir)
        RulesStore.init(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvEndpoint = findViewById(R.id.tvEndpoint)
        etPort = findViewById(R.id.etPort)
        swProxy = findViewById(R.id.swProxy)
        swMitm = findViewById(R.id.swMitm)

        adapter = RuleAdapter(
            onToggle = { rule, on -> RulesStore.setEnabled(rule.id, on) },
            onEdit = { rule -> editRule(rule) }
        )

        val rv = findViewById<RecyclerView>(R.id.rvRules)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<Button>(R.id.btnCa).setOnClickListener { exportCa() }
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        swProxy.setOnCheckedChangeListener { _, on ->
            if (on) startProxy() else stopProxy()
        }

        askNotificationPermission()

        lifecycleScope.launch {
            ProxyEngine.running.collectLatest { running ->
                tvStatus.text = if (running) "Proxy: RODANDO" else "Proxy: PARADO"
                tvEndpoint.text = "IP: " + Util.localIpv4() + "   Porta: " + etPort.text
                if (swProxy.isChecked != running) {
                    swProxy.setOnCheckedChangeListener(null)
                    swProxy.isChecked = running
                    swProxy.setOnCheckedChangeListener { _, on ->
                        if (on) startProxy() else stopProxy()
                    }
                }
            }
        }

        lifecycleScope.launch {
            RulesStore.rules.collectLatest { adapter.submit(it) }
        }

        tvEndpoint.text = "IP: " + Util.localIpv4() + "   Porta: 8080"
    }

    private fun startProxy() {
        val port = etPort.text.toString().toIntOrNull() ?: 8080
        if (port < 1024 || port > 65535) {
            toast("Porta invalida (use 1024-65535)")
            swProxy.isChecked = false
            return
        }
        val i = Intent(this, ProxyService::class.java)
            .putExtra(ProxyService.EXTRA_PORT, port)
            .putExtra(ProxyService.EXTRA_MITM, swMitm.isChecked)
        ContextCompat.startForegroundService(this, i)
    }

    private fun stopProxy() {
        stopService(Intent(this, ProxyService::class.java))
        ProxyEngine.stop()
    }

    private fun exportCa() {
        val f = File(filesDir, "ffproxy-ca.crt")
        f.writeText(CaStore.certPem())
        val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-x509-ca-cert"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Instalar certificado CA"))
    }

    private fun editRule(rule: Rule) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val nameEt = EditText(this).apply { setText(rule.name) }
        val etUrl = EditText(this).apply {
            hint = "Regex da URL"
            setText(rule.urlRegex)
        }
        val etKey = EditText(this).apply {
            hint = "Regex da chave JSON"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(rule.keyRegex)
        }
        val etVal = EditText(this).apply {
            hint = "Valor"
            setText(rule.value)
        }

        box.addView(TextView(this).apply { text = "Nome" })
        box.addView(nameEt)
        box.addView(TextView(this).apply { text = "URL (regex)" })
        box.addView(etUrl)
        box.addView(TextView(this).apply { text = "Chave JSON (regex)" })
        box.addView(etKey)
        box.addView(TextView(this).apply { text = "Valor" })
        box.addView(etVal)

        AlertDialog.Builder(this)
            .setTitle(rule.name)
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                RulesStore.update(
                    rule.copy(
                        name = nameEt.text.toString().ifBlank { rule.name },
                        urlRegex = etUrl.text.toString().ifBlank { ".*" },
                        keyRegex = etKey.text.toString(),
                        value = etVal.text.toString()
                    )
                )
            }
            .setNeutralButton("Restaurar padrao") { _, _ -> RulesStore.resetToDefaults() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42
            )
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}