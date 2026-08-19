package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil

class DashboardFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Dashboard NexusMount"
        val repo = (requireActivity().application as NexusApp).repository
        val drives = repo.getDrives()
        val online = drives.count { it.status.name == "ONLINE" || it.status.name == "SYNCING" }
        val transfers = repo.getTransfers()
        val ts = if (TailscaleUtil.isInstalled(requireContext())) "Tailscale OK" else "Sin Tailscale"
        val vpn = if (TailscaleUtil.hasVpnActive(requireContext())) "VPN on" else "VPN off"
        binding.subtitleText.text = "${drives.size} unidades · $online online · ${transfers.size} transferencias · $ts · $vpn"
        binding.primaryAction.text = "Añadir unidad SMB"
        binding.primaryAction.setOnClickListener { findNavController().navigate(R.id.drivesFragment) }

        val lines = mutableListOf(
            "Estado del sistema" to "Operativo",
            "Unidades en línea" to "$online / ${drives.size}",
            "Transferencias" to "${transfers.size}",
            "Tailscale" to TailscaleUtil.statusSummary(requireContext()).lines().take(3).joinToString(" · "),
            "Ir a Explorador" to "Archivos locales + ZIP",
            "Hablar con Rigo" to "Asistente IA bajo solicitud",
            "Ir a Seguridad" to "2FA y alertas",
            "Ir a Backups" to "Instantáneas ZIP",
            "Ir a Módulos" to "Drive, S3, WebDAV, recetas"
        )
        drives.take(5).forEach { lines.add(it.name to "${it.type} · ${it.status} · ${it.path}") }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines) { pos ->
            when (pos) {
                4 -> findNavController().navigate(R.id.filesFragment)
                5 -> findNavController().navigate(R.id.aiFragment)
                6 -> findNavController().navigate(R.id.securityFragment)
                7 -> findNavController().navigate(R.id.backupFragment)
                8 -> findNavController().navigate(R.id.modulesConfigFragment)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
