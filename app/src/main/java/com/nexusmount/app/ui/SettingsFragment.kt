package com.nexusmount.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.R
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil

class SettingsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result.values.any { it } || hasReadPermission()
        Toast.makeText(requireContext(), if (ok) "Permisos actualizados" else "Permiso denegado", Toast.LENGTH_SHORT).show()
        refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Ajustes y configuración"
        binding.subtitleText.text = "Todo lo de configuración, en un solo sitio"
        binding.primaryAction.text = "Pedir permiso almacenamiento"
        binding.primaryAction.setOnClickListener { requestStoragePermissions() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val ts = TailscaleUtil.statusSummary(requireContext()).lines().take(2).joinToString(" · ")
        val items = listOf(
            "── Red y acceso ──" to "",
            "Tailscale VPN" to ts,
            "Samba / SMB" to "Cliente y workgroup",
            "Guía de red local" to "Topología LAN + Tailscale",
            "Acceso Web" to "Preferencias acceso remoto",
            "Sync Web ↔ Teléfono" to "Estado de sincronización",
            "Añadir conexión" to "Multi-fuente (SMB, cloud…)",
            "Interconexión (visor IP)" to "Solo lectura por LAN/Tailscale",
            "── Seguridad ──" to "",
            "Seguridad 2FA" to "TOTP y códigos de respaldo",
            "Alertas de intrusión" to "Monitoreo de accesos",
            "Permisos / ACL" to "Permisos de la app y NAS",
            "Logs técnicos" to "Eventos de seguridad e IA",
            "── Backups e IA ──" to "",
            "Config. backups" to "Programación e integridad",
            "Recuperación" to "Instantáneas históricas",
            "Automatización IA" to "Recetas y disparadores",
            "Privacidad IA" to "PII y cloud",
            "Terminal / Rigo" to "Consola de comandos",
            "── Sistema ──" to "",
            "Módulos y requisitos" to "Drive, S3, WebDAV, keys",
            "Instalador modular" to "Activar/desactivar módulos",
            "Idioma ES/EN" to "Preferencia de idioma",
            "Permiso almacenamiento" to if (hasReadPermission()) "Concedido ✓" else "Pendiente"
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items) { pos ->
            when (items[pos].first) {
                "Tailscale VPN" -> findNavController().navigate(R.id.tailscaleFragment)
                "Samba / SMB" -> findNavController().navigate(R.id.sambaFragment)
                "Guía de red local" -> findNavController().navigate(R.id.networkGuideFragment)
                "Acceso Web" -> findNavController().navigate(R.id.webAccessFragment)
                "Sync Web ↔ Teléfono" -> findNavController().navigate(R.id.deviceSyncFragment)
                "Añadir conexión" -> findNavController().navigate(R.id.addConnectionFragment)
                "Interconexión (visor IP)" -> findNavController().navigate(R.id.interconnectFragment)
                "Seguridad 2FA" -> findNavController().navigate(R.id.securityFragment)
                "Alertas de intrusión" -> findNavController().navigate(R.id.intrusionFragment)
                "Permisos / ACL" -> findNavController().navigate(R.id.permissionsFragment)
                "Logs técnicos" -> findNavController().navigate(R.id.logsFragment)
                "Config. backups" -> findNavController().navigate(R.id.backupConfigFragment)
                "Recuperación" -> findNavController().navigate(R.id.recoveryFragment)
                "Automatización IA" -> findNavController().navigate(R.id.aiAutomationFragment)
                "Privacidad IA" -> findNavController().navigate(R.id.aiPrivacyFragment)
                "Terminal / Rigo" -> findNavController().navigate(R.id.terminalFragment)
                "Módulos y requisitos" -> findNavController().navigate(R.id.modulesConfigFragment)
                "Instalador modular" -> findNavController().navigate(R.id.installerFragment)
                "Idioma ES/EN" -> findNavController().navigate(R.id.languageFragment)
                "Permiso almacenamiento" -> requestStoragePermissions()
            }
        }
    }

    private fun hasReadPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                )
                intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        )
                    )
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Ajustes → Apps → NexusMount → Permisos", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
