package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.cleanup.CleanupItem
import com.nexusmount.app.cleanup.SmartCleanup
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanupFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<CleanupItem> = emptyList()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Limpieza inteligente"
        binding.subtitleText.text = "Caché · temporales · vacíos · descargas antiguas"
        binding.primaryAction.text = "Analizar dispositivo"
        binding.primaryAction.setOnClickListener { scan() }
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Pulsa «Analizar dispositivo» para escanear."
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun scan() {
        binding.emptyText.text = "Analizando…"
        binding.emptyText.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { SmartCleanup.scan(requireContext()) }
            val total = items.sumOf { it.bytes }
            binding.subtitleText.text =
                "Encontrado: ${SmartCleanup.formatSize(total)} · toca una categoría"
            binding.emptyText.visibility = View.GONE
            binding.recycler.adapter = SimpleAdapter(
                items.map {
                    val flag = if (it.safe) "Seguro" else "Revisar"
                    "${it.title} · $flag" to
                        "${SmartCleanup.formatSize(it.bytes)} · ${it.detail}"
                }
            ) { pos ->
                val item = items[pos]
                AlertDialog.Builder(requireContext())
                    .setTitle(item.title)
                    .setMessage(
                        "${item.detail}\n\nTamaño: ${SmartCleanup.formatSize(item.bytes)}\n" +
                            "Elementos: ${item.files.size}\n" +
                            if (item.safe) "Limpieza segura."
                            else "No se borra solo: confirma si conoces estos archivos."
                    )
                    .setPositiveButton(if (item.safe) "Limpiar" else "Limpiar con cuidado") { _, _ ->
                        clean(item)
                    }
                    .setNeutralButton("Limpiar TODO lo seguro") { _, _ -> cleanAllSafe() }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
            binding.primaryAction.text = "Limpiar todo lo seguro"
            binding.primaryAction.setOnClickListener { cleanAllSafe() }
        }
    }

    private fun clean(item: CleanupItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (n, b) = withContext(Dispatchers.IO) { SmartCleanup.clean(item) }
            Toast.makeText(
                requireContext(),
                "Eliminados $n · ${SmartCleanup.formatSize(b)}",
                Toast.LENGTH_LONG
            ).show()
            scan()
        }
    }

    private fun cleanAllSafe() {
        val safe = items.filter { it.safe }
        if (safe.isEmpty()) {
            Toast.makeText(requireContext(), "Nada seguro que limpiar. Analiza primero.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Limpiar todo lo seguro")
            .setMessage(
                "Se borrará caché, temporales, carpetas vacías y descargas antiguas de la app.\n" +
                    "No se tocan archivos grandes del almacenamiento general sin revisión."
            )
            .setPositiveButton("Limpiar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var n = 0
                    var b = 0L
                    withContext(Dispatchers.IO) {
                        safe.forEach {
                            val r = SmartCleanup.clean(it)
                            n += r.first
                            b += r.second
                        }
                    }
                    Toast.makeText(
                        requireContext(),
                        "Listo: $n elementos · ${SmartCleanup.formatSize(b)}",
                        Toast.LENGTH_LONG
                    ).show()
                    scan()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
