package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil

class TailscaleFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Tailscale VPN"
        binding.subtitleText.text = "Estado real de la red mesh"
        binding.primaryAction.text = "Abrir app Tailscale"
        binding.primaryAction.setOnClickListener { TailscaleUtil.openTailscale(requireContext()) }
        val installed = TailscaleUtil.isInstalled(requireContext())
        val vpn = TailscaleUtil.hasVpnActive(requireContext())
        val ips = TailscaleUtil.getTailscaleIpv4()
        val lines = mutableListOf(
            "Instalado" to if (installed) "Sí" else "No — instala desde Play Store",
            "VPN activa" to if (vpn) "Sí" else "No detectada",
        )
        if (ips.isEmpty()) lines.add("IPs 100.x" to "Ninguna (conecta Tailscale)")
        else ips.forEach { lines.add("IP Tailscale" to it) }
        lines.add("Uso" to "En Unidades → SMB usa la IP 100.x del NAS/PC")
        lines.add("Red" to "No inventada: datos del sistema")
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
