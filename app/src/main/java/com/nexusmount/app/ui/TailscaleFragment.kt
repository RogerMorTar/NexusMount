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
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Tailscale VPN"
        binding.subtitleText.text = "Estado detectado en este teléfono"
        binding.primaryAction.text = "Actualizar / Abrir Tailscale"
        binding.primaryAction.setOnClickListener {
            refresh()
            TailscaleUtil.openTailscale(requireContext())
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val installed = TailscaleUtil.isInstalled(requireContext())
        val vpn = TailscaleUtil.hasVpnActive(requireContext())
        val ips = TailscaleUtil.getTailscaleIpv4()
        val lines = mutableListOf(
            "Instalado" to if (installed) "Sí" else "No detectado (¿permiso de visibilidad?)",
            "Paquete" to (TailscaleUtil.installedPackage(requireContext()) ?: "—"),
            "VPN activa" to if (vpn) "Sí" else "No",
        )
        if (ips.isEmpty()) {
            lines.add("IPs 100.x" to "Ninguna — abre Tailscale y pulsa Connect")
        } else {
            ips.forEach { lines.add("IP Tailscale" to it) }
        }
        lines.add("Uso con Samba" to "En Mis Unidades usa la IP 100.x del PC (app Tailscale → Machines)")
        lines.add("Resumen completo" to TailscaleUtil.statusSummary(requireContext()).replace("\n", " | "))
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
