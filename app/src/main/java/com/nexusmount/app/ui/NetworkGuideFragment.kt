package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding

class NetworkGuideFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Guía de red local"
        binding.subtitleText.text = "Topología Nexus Node"
        binding.primaryAction.visibility = View.GONE
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(listOf(
            "1. LAN" to "PC/NAS y router en la misma red Wi‑Fi o cable",
            "2. Samba" to "Comparte una carpeta en el PC (SMB puerto 445)",
            "3. Tailscale" to "Instala en PC y móvil → misma cuenta → IPs 100.x",
            "4. Conexión" to "En NexusMount: Unidades → SMB → IP 100.x o 192.168.x",
            "5. Firewall" to "Permite Samba en el PC (perfil red privada)",
            "Diagrama" to "Móvil ──Tailscale── PC/NAS ──SMB── discos",
            "Sin Tailscale" to "Usa solo IP local 192.168.x en la misma Wi‑Fi"
        ))
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
