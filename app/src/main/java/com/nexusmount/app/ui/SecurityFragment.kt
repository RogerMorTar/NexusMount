package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.security.SecurityMonitor
import com.nexusmount.app.security.TotpHelper

class SecurityFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var monitor: SecurityMonitor

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        monitor = SecurityMonitor(requireContext())
        binding.titleText.text = "Seguridad"
        binding.subtitleText.text = "2FA TOTP · Alertas · Logs"
        binding.primaryAction.text = "Configurar 2FA"
        binding.primaryAction.setOnClickListener { setup2fa() }

        refresh()
    }

    private fun refresh() {
        val events = monitor.getEvents()
        val prefs = requireContext().getSharedPreferences("nexus_security", 0)
        val has2fa = prefs.contains("totp_secret")
        val lines = mutableListOf(
            "2FA TOTP" to if (has2fa) "Activado" else "Desactivado",
            "Verificar código 2FA" to "Probar TOTP",
            "Simular fallo de auth" to "Genera alerta de fuerza bruta",
            "Ruta sensible" to "Registra acceso a /data"
        )
        events.take(20).forEach {
            lines.add(
                "${it.severity.uppercase()} ${it.type}" to
                    "${monitor.formatTime(it.time)} · ${it.detail}"
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines) { pos ->
            when (pos) {
                0 -> setup2fa()
                1 -> verify2fa()
                2 -> {
                    monitor.recordFailedAuth("100.64.0.99")
                    Toast.makeText(requireContext(), "Alerta registrada", Toast.LENGTH_SHORT).show()
                    refresh()
                }
                3 -> {
                    monitor.recordSensitivePath("/data/data/com.nexusmount.app")
                    refresh()
                }
            }
        }
        binding.emptyText.visibility = View.GONE
    }

    private fun setup2fa() {
        val prefs = requireContext().getSharedPreferences("nexus_security", 0)
        val secret = TotpHelper.generateSecret()
        prefs.edit().putString("totp_secret", secret).apply()
        val code = TotpHelper.currentCode(secret)
        monitor.log("2fa_setup", "Secreto TOTP generado", "info")
        AlertDialog.Builder(requireContext())
            .setTitle("2FA activado")
            .setMessage(
                "Secreto (guárdalo en tu app TOTP):\n$secret\n\n" +
                    "Código actual de prueba: $code\n\n" +
                    "En producción se mostraría un QR (otpauth://)."
            )
            .setPositiveButton("OK") { _, _ -> refresh() }
            .show()
    }

    private fun verify2fa() {
        val prefs = requireContext().getSharedPreferences("nexus_security", 0)
        val secret = prefs.getString("totp_secret", null)
        if (secret == null) {
            Toast.makeText(requireContext(), "Configura 2FA primero", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply { hint = "Código de 6 dígitos" }
        AlertDialog.Builder(requireContext())
            .setTitle("Verificar 2FA")
            .setView(input)
            .setPositiveButton("Comprobar") { _, _ ->
                val ok = TotpHelper.verify(secret, input.text.toString())
                if (ok) {
                    monitor.log("2fa_ok", "Código válido", "info")
                    Toast.makeText(requireContext(), "Código válido", Toast.LENGTH_SHORT).show()
                } else {
                    monitor.recordFailedAuth("local-2fa")
                    Toast.makeText(requireContext(), "Código incorrecto", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
