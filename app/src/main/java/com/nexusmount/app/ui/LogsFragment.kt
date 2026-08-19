package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.security.SecurityMonitor

class LogsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Logs técnicos"
        binding.subtitleText.text = "Seguridad e IA"
        binding.primaryAction.text = "Actualizar"
        val monitor = SecurityMonitor(requireContext())
        fun load() {
            val events = monitor.getEvents()
            val lines = if (events.isEmpty()) {
                listOf("Sin eventos" to "Las acciones de seguridad e IA aparecerán aquí")
            } else events.map {
                "${it.severity} · ${it.type}" to "${monitor.formatTime(it.time)} · ${it.detail}"
            }
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(lines)
        }
        binding.primaryAction.setOnClickListener { load() }
        load()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
