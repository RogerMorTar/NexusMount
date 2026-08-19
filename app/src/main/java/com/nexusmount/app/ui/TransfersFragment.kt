package com.nexusmount.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.databinding.FragmentListBinding

class TransfersFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Transferencias"
        binding.subtitleText.text = "Historial de operaciones"
        binding.primaryAction.visibility = View.GONE

        val transfers = (requireActivity().application as NexusApp).repository.getTransfers()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            transfers.map { it.name to "${it.from} → ${it.to} • ${it.status} ${it.progress}%" }
        )
        binding.emptyText.visibility = if (transfers.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "No hay transferencias todavía"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
