package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding

class InstallerFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private fun p() = requireContext().getSharedPreferences("nexus_modules", 0)
    private val mods = listOf(
        "core" to "Núcleo (siempre)",
        "smb" to "Cliente SMB",
        "zip" to "Compresión ZIP",
        "ai" to "Command AI local",
        "backup" to "Backups",
        "security" to "Seguridad 2FA",
        "tailscale" to "Integración Tailscale",
        "cloud" to "Conectores cloud (config)"
    )
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Instalador modular"
        binding.subtitleText.text = "Activa/desactiva módulos (preferencias)"
        binding.primaryAction.text = "Activar todos"
        binding.primaryAction.setOnClickListener {
            val e = p().edit()
            mods.forEach { e.putBoolean("mod_${it.first}", true) }
            e.apply()
            Toast.makeText(requireContext(), "Todos activados", Toast.LENGTH_SHORT).show()
            refresh()
        }
        refresh()
    }
    private fun refresh() {
        val lines = mods.map { (id, label) ->
            val on = p().getBoolean("mod_$id", true)
            label to if (on) "Activo — tocar para desactivar" else "Inactivo — tocar para activar"
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines) { pos ->
            val id = mods[pos].first
            if (id == "core") {
                Toast.makeText(requireContext(), "El núcleo no se puede desactivar", Toast.LENGTH_SHORT).show()
                return@SimpleAdapter
            }
            val next = !p().getBoolean("mod_$id", true)
            p().edit().putBoolean("mod_$id", next).apply()
            refresh()
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
