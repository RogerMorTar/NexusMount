package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding

class WebAccessFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private fun p() = requireContext().getSharedPreferences("nexus_web", 0)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Acceso Web"
        binding.subtitleText.text = "Preferencias de acceso remoto"
        binding.primaryAction.text = "Alternar acceso externo"
        binding.primaryAction.setOnClickListener {
            val on = !p().getBoolean("external", false)
            p().edit().putBoolean("external", on).apply()
            Toast.makeText(requireContext(), if (on) "Externo ON (requiere servidor)" else "Externo OFF", Toast.LENGTH_SHORT).show()
            refresh()
        }
        refresh()
    }
    private fun refresh() {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(listOf(
            "HTTPS" to "Recomendado",
            "Acceso externo" to if (p().getBoolean("external", false)) "Activado" else "Desactivado",
            "2FA web" to "Usar módulo Seguridad TOTP",
            "Nota" to "La app nativa no abre un servidor web; usa la PWA o un reverse proxy en el PC"
        ))
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
