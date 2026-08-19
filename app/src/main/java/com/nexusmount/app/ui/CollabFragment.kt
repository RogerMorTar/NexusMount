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
import org.json.JSONArray
import org.json.JSONObject

class CollabFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private fun prefs() = requireContext().getSharedPreferences("nexus_collab", 0)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Trabajo colaborativo"
        binding.subtitleText.text = "Espacios y miembros"
        binding.primaryAction.text = "Nuevo espacio"
        binding.primaryAction.setOnClickListener {
            val input = EditText(requireContext()).apply { hint = "Nombre del espacio" }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Crear espacio")
                .setView(input)
                .setPositiveButton("Crear") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val arr = JSONArray(prefs().getString("spaces", "[]"))
                        arr.put(JSONObject().put("name", name).put("members", 1))
                        prefs().edit().putString("spaces", arr.toString()).apply()
                        Toast.makeText(requireContext(), "Creado", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }.setNegativeButton("Cancelar", null).show()
        }
        refresh()
    }

    private fun refresh() {
        val arr = JSONArray(prefs().getString("spaces", "[]"))
        val items = mutableListOf<Pair<String, String>>()
        if (arr.length() == 0) {
            items.add("Sin espacios" to "Crea uno para compartir rutas SMB/Tailscale")
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(o.getString("name") to "Miembros: ${o.optInt("members", 1)}")
            }
        }
        items.add("Invitar" to "Comparte la IP Tailscale + share SMB del equipo")
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items)
        binding.emptyText.visibility = View.GONE
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
