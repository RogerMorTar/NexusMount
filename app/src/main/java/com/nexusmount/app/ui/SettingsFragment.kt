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
        data class Row(val title: String, val sub: String, val action: (() -> Unit)?)
        val rows = listOf(
            Row("── Red y acceso ──", "", null),
            Row("Tailscale VPN", ts, { findNavController().navigate(R.id.tailscaleFragment) }),
            Row("Samba / SMB", "Cliente y workgroup", { findNavController().navigate(R.id.sambaFragment) }),
            Row("Guía de red local", "Topología LAN + Tailscale", { findNavController().navigate(R.id.networkGuideFragment) }),
            Row("Acceso Web", "Preferencias acceso remoto", { findNavController().navigate(R.id.webAccessFragment) }),
            Row("Sync Web ↔ Teléfono", "Estado de sincronización", { findNavController().navigate(R.id.deviceSyncFragment) }),
            Row("Añadir conexión", "Multi-fuente (SMB, cloud…)", { findNavController().navigate(R.id.addConnectionFragment) }),
            Row("── Seguridad ──", "", null),
            Row("Seguridad 2FA", "TOTP y códigos de respaldo", { findNavController().navigate(R.id.securityFragment) }),
            Row("Alertas de intrusión", "Monitoreo de accesos", { findNavController().navigate(R.id.intrusionFragment) }),
            Row("Permisos / ACL", "Permisos de la app y NAS", { findNavController().navigate(R.id.permissionsFragment) }),
            Row("Logs técnicos", "Eventos de seguridad e IA", { findNavController().navigate(R.id.logsFragment) }),
            Row("── Mantenimiento ──", "", null),
            Row("Análisis de memoria", "RAM y almacenamiento con gráficos", { findNavController().navigate(R.id.memoryFragment) }),
            Row("Limpieza inteligente", "Caché, temporales, archivos grandes", { findNavController().navigate(R.id.cleanupFragment) }),
            Row("Bloqueo de anuncios", "Filtro in-app + DNS privado", { findNavController().navigate(R.id.adBlockFragment) }),
            Row("── Backups e IA ──", "", null),
            Row("Config. backups", "Programación e integridad", { findNavController().navigate(R.id.backupConfigFragment) }),
            Row("Recuperación", "Instantáneas históricas", { findNavController().navigate(R.id.recoveryFragment) }),
            Row("Automatización IA", "Recetas y disparadores", { findNavController().navigate(R.id.aiAutomationFragment) }),
            Row("Privacidad IA", "PII y cloud", { findNavController().navigate(R.id.aiPrivacyFragment) }),
            Row("Terminal / Rigo", "Consola de comandos", { findNavController().navigate(R.id.terminalFragment) }),
            Row("── Sistema ──", "", null),
            Row("Módulos y requisitos", "Drive, S3, WebDAV, keys", { findNavController().navigate(R.id.modulesConfigFragment) }),
            Row("Instalador modular", "Activar/desactivar módulos", { findNavController().navigate(R.id.installerFragment) }),
            Row("Idioma ES/EN", "Preferencia de idioma", { findNavController().navigate(R.id.languageFragment) }),
            Row("Permiso almacenamiento", if (hasReadPermission()) "Concedido ✓" else "Pendiente", { requestStoragePermissions() })
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(rows.map { it.title to it.sub }) { pos ->
            rows[pos].action?.invoke()
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
