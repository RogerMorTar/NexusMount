package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.adblock.AdBlockManager
import com.nexusmount.app.databinding.FragmentListBinding

class AdBlockFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Bloqueo de anuncios"
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val on = AdBlockManager.isEnabled(requireContext())
        binding.subtitleText.text = if (on) "Filtro in-app ACTIVO" else "Filtro in-app inactivo"
        binding.primaryAction.text = if (on) "Desactivar filtro" else "Activar filtro"
        binding.primaryAction.setOnClickListener {
            AdBlockManager.setEnabled(requireContext(), !on)
            Toast.makeText(
                requireContext(),
                if (!on) "Filtro activado" else "Filtro desactivado",
                Toast.LENGTH_SHORT
            ).show()
            refresh()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            listOf(
                "Estado" to AdBlockManager.stats(requireContext()).replace("\n", " · "),
                "Activar / desactivar filtro in-app" to "Bloquea dominios de anuncios conocidos en la app",
                "DNS privado del sistema (recomendado)" to "Abre ajustes · usa dns.adguard.com o similar",
                "Lista de dominios" to "${AdBlockManager.BLOCKLIST.size} dominios de ads/trackers",
                "Limitación Android" to "Sin root/VPN de sistema no se bloquean anuncios de otras apps",
                "Cómo potenciarlo" to "Ajustes Android → Red → DNS privado → dns.adguard.com"
            )
        ) { pos ->
            when (pos) {
                1 -> {
                    val next = !AdBlockManager.isEnabled(requireContext())
                    AdBlockManager.setEnabled(requireContext(), next)
                    refresh()
                }
                2, 5 -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("DNS privado")
                        .setMessage(
                            "1. Entra en Ajustes del sistema → Red e internet → DNS privado\n" +
                                "2. Elige «Nombre de host»\n" +
                                "3. Escribe: dns.adguard.com\n" +
                                "   (o dns.adguard-dns.com)\n\n" +
                                "Eso bloquea anuncios en casi todo el teléfono sin root."
                        )
                        .setPositiveButton("Abrir ajustes") { _, _ ->
                            AdBlockManager.openPrivateDnsSettings(requireContext())
                        }
                        .setNegativeButton("Cerrar", null)
                        .show()
                }
                3 -> {
                    val sample = AdBlockManager.BLOCKLIST.take(12).joinToString("\n") { "• $it" }
                    AlertDialog.Builder(requireContext())
                        .setTitle("Ejemplos de lista")
                        .setMessage("$sample\n…")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
