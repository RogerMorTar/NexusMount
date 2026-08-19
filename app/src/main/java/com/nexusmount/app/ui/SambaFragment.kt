package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding

class SambaFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private fun prefs() = requireContext().getSharedPreferences("nexus_samba", 0)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Samba / SMB"
        binding.subtitleText.text = "Servidor y recursos compartidos"
        binding.primaryAction.text = "Guardar configuración"
        val p = prefs()
        refresh()
        binding.primaryAction.setOnClickListener {
            val name = EditText(requireContext()).apply { setText(p.getString("name", "NEXUS-MOUNT")); hint = "Nombre equipo" }
            val wg = EditText(requireContext()).apply { setText(p.getString("workgroup", "WORKGROUP")); hint = "Workgroup" }
            val box = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 20, 40, 10); addView(name); addView(wg)
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Configurar Samba")
                .setView(box)
                .setPositiveButton("Guardar") { _, _ ->
                    p.edit().putString("name", name.text.toString())
                        .putString("workgroup", wg.text.toString())
                        .putBoolean("enabled", true).apply()
                    Toast.makeText(requireContext(), "Guardado", Toast.LENGTH_SHORT).show()
                    refresh()
                }.setNegativeButton("Cancelar", null).show()
        }
    }

    private fun refresh() {
        val p = prefs()
        val en = p.getBoolean("enabled", false)
        val items = listOf(
            "Estado" to if (en) "Activo (cliente)" else "Configurar",
            "Nombre" to (p.getString("name", "NEXUS-MOUNT") ?: ""),
            "Workgroup" to (p.getString("workgroup", "WORKGROUP") ?: ""),
            "Puerto" to "445",
            "Añadir unidad SMB" to "Ir a Mis Unidades → Añadir Samba",
            "Nota" to "En Android la app actúa como CLIENTE SMB hacia NAS/PC"
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
