package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil

class DashboardFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Dashboard"
        binding.primaryAction.text = "Estado Tailscale"
        binding.primaryAction.setOnClickListener {
            val msg = TailscaleUtil.statusSummary(requireContext())
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Tailscale")
                .setMessage(msg)
                .setPositiveButton("Abrir Tailscale") { _, _ ->
                    TailscaleUtil.openTailscale(requireContext())
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }

        val repo = (requireActivity().application as NexusApp).repository
        val drives = repo.getDrives()
        val online = drives.count { it.status.name == "ONLINE" || it.status.name == "SYNCING" }

        binding.subtitleText.text = "${drives.size} unidades • $online en línea"

        val lines = drives.map {
            it.name to "${it.type} • ${it.path} • ${it.status}"
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines)
        binding.emptyText.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "Sin unidades"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
