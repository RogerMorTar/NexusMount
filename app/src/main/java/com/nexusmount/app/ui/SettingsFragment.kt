package com.nexusmount.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

class SettingsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result.values.any { it }
        Toast.makeText(requireContext(), if (ok) "Permisos concedidos" else "Denegados", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Ajustes y módulos"
        binding.subtitleText.text = "NexusMount v4.5 nativo · ES/EN"
        binding.primaryAction.text = "Pedir permisos de almacenamiento"
        binding.primaryAction.setOnClickListener { requestStoragePermissions() }

        val items = listOf(
            "Módulos y requisitos" to "Drive, S3, IA cloud, ACL…",
            "Nexus Command AI" to "Control por lenguaje natural",
            "Seguridad · 2FA · Alertas" to "TOTP e intrusiones",
            "Backups e instantáneas" to "ZIP programables",
            "Estado Tailscale" to TailscaleUtil.statusSummary(requireContext()).lines().first(),
            "Abrir Tailscale" to "App oficial",
            "Google Drive (setup)" to "Requiere OAuth",
            "Amazon S3 (setup)" to "Access Key + bucket",
            "WebDAV (setup)" to "Nextcloud / ownCloud",
            "Idioma" to "Español (predeterminado)",
            "Permiso lectura" to if (hasReadPermission()) "Concedido" else "No concedido",
            "Versión" to "4.5.0 · API ${Build.VERSION.SDK_INT}"
        )

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items) { pos ->
            when (pos) {
                0 -> findNavController().navigate(R.id.modulesConfigFragment)
                1 -> findNavController().navigate(R.id.aiFragment)
                2 -> findNavController().navigate(R.id.securityFragment)
                3 -> findNavController().navigate(R.id.backupFragment)
                4 -> android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Tailscale")
                    .setMessage(TailscaleUtil.statusSummary(requireContext()))
                    .setPositiveButton("OK", null).show()
                5 -> TailscaleUtil.openTailscale(requireContext())
                6 -> showCloud(CloudConnectors.Provider.GOOGLE_DRIVE)
                7 -> showCloud(CloudConnectors.Provider.S3)
                8 -> showCloud(CloudConnectors.Provider.WEBDAV)
                9 -> Toast.makeText(requireContext(), "Interfaz en castellano. EN: próximamente strings.xml", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCloud(p: CloudConnectors.Provider) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(p.name)
            .setMessage(CloudConnectors.describeSetup(p))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun hasReadPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Ajustes → Apps → NexusMount → Permisos", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionLauncher.launch(perms)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
