package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.backup.BackupManager
import com.nexusmount.app.databinding.FragmentListBinding
import java.io.File

class BackupFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var bm: BackupManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bm = BackupManager(requireContext())
        binding.titleText.text = "Backups"
        binding.subtitleText.text = "Instantáneas ZIP · Restauración"
        binding.primaryAction.text = "Ejecutar backup ahora"
        binding.primaryAction.setOnClickListener {
            val src = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                ?: Environment.getExternalStorageDirectory()
            val r = bm.runBackup(listOf(src), "manual")
            r.onSuccess {
                Toast.makeText(requireContext(), "OK: ${it.name}", Toast.LENGTH_LONG).show()
                refresh()
            }.onFailure {
                Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
        refresh()
    }

    private fun refresh() {
        val snaps = bm.listSnapshots()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            snaps.map { it.name to "${it.size / 1024} KB · ${it.path}" }
        ) { pos ->
            val s = snaps[pos]
            AlertDialog.Builder(requireContext())
                .setTitle(s.name)
                .setItems(arrayOf("Restaurar a Documents", "Eliminar")) { _, which ->
                    when (which) {
                        0 -> {
                            val dest = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                                "restore_${s.name}"
                            )
                            val r = bm.restoreSnapshot(File(s.path), dest)
                            Toast.makeText(
                                requireContext(),
                                if (r.isSuccess) "Restaurado" else "Error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        1 -> {
                            File(s.path).delete()
                            refresh()
                        }
                    }
                }
                .show()
        }
        binding.emptyText.visibility = if (snaps.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "Sin instantáneas. Ejecuta un backup."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
