package com.nexusmount.app.ui

import android.Manifest
import android.app.AlertDialog
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
import com.nexusmount.app.cloud.CloudConnectors
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil

/**
 * Punto único de configuración: agrupa todas las pantallas de ajustes.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result.values.any { it }
        Toast.makeText(
            requireContext(),
            if (ok || hasReadPermission()) "Permisos actualizados" else "Permiso denegado",
            Toast.LENGTH_SHORT
        ).show()
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
            "Permiso almacenamiento" to if (hasReadPermission()) "Concedido ✓" else "Pendiente — tocar o usar botón"
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items) { pos ->
            when (pos) {
                1 -> findNavController().navigate(R.id.tailscaleFragment)
                2 -> findNavController().navigate(R.id.sambaFragment)
                3 -> findNavController().navigate(R.id.networkGuideFragment)
                4 -> findNavController().navigate(R.id.webAccessFragment)
                5 -> findNavController().navigate(R.id.deviceSyncFragment)
                6 -> findNavController().navigate(R.id.addConnectionFragment)
                8 -> findNavController().navigate(R.id.securityFragment)
                9 -> findNavController().navigate(R.id.intrusionFragment)
                10 -> findNavController().navigate(R.id.permissionsFragment)
                11 -> findNavController().navigate(R.id.logsFragment)
                13 -> findNavController().navigate(R.id.backupConfigFragment)
                14 -> findNavController().navigate(R.id.recoveryFragment)
                15 -> findNavController().navigate(R.id.aiAutomationFragment)
                16 -> findNavController().navigate(R.id.aiPrivacyFragment)
                17 -> findNavController().navigate(R.id.terminalFragment)
                19 -> findNavController().navigate(R.id.modulesConfigFragment)
                20 -> findNavController().navigate(R.id.installerFragment)
                21 -> findNavController().navigate(R.id.languageFragment)
                22 -> requestStoragePermissions()
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
                    Toast.makeText(
                        requireContext(),
                        "Ajustes → Apps → NexusMount → Permisos",
                        Toast.LENGTH_LONG
                    ).show()
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
