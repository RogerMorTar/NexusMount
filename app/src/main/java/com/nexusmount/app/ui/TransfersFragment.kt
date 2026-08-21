package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.data.DriveType
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.data.TransferItem
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TransfersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val repo get() = (requireActivity().application as NexusApp).repository

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Transferencias"
        binding.subtitleText.text = "Copiar archivos · historial"
        binding.primaryAction.text = "Nueva transferencia"
        binding.primaryAction.setOnClickListener { startTransferWizard() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val list = repo.getTransfers()
        val rows = mutableListOf(
            "── Accesos rápidos ──" to "",
            "Ir a Dashboard" to "Resumen del sistema",
            "Ir a Mis Unidades" to "SMB y local",
            "Interconexión (solo lectura)" to "Ver/copiar por IP sin modificar",
            "── Historial ──" to ""
        )
        if (list.isEmpty()) {
            rows.add("Sin transferencias" to "Pulsa «Nueva transferencia» o copia desde el explorador SMB")
        } else {
            list.take(40).forEach {
                rows.add(it.name to "${it.status} · ${it.progress}% · ${it.fromPath} → ${it.toPath}")
            }
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(rows) { pos ->
            when (rows[pos].first) {
                "Ir a Dashboard" -> findNavController().navigate(R.id.dashboardFragment)
                "Ir a Mis Unidades" -> findNavController().navigate(R.id.drivesFragment)
                "Interconexión (solo lectura)" -> findNavController().navigate(R.id.interconnectFragment)
                "Nueva transferencia", "Sin transferencias" -> startTransferWizard()
            }
        }
        binding.emptyText.visibility = View.GONE
    }

    private fun startTransferWizard() {
        AlertDialog.Builder(requireContext())
            .setTitle("Nueva transferencia")
            .setItems(
                arrayOf(
                    "Desde unidad SMB → teléfono",
                    "Ir al explorador SMB (abrir unidad)",
                    "Interconexión solo lectura → teléfono",
                    "Ir a Mis Unidades"
                )
            ) { _, w ->
                when (w) {
                    0 -> copyFromSmbDrive()
                    1, 3 -> findNavController().navigate(R.id.drivesFragment)
                    2 -> findNavController().navigate(R.id.interconnectFragment)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun copyFromSmbDrive() {
        val drives = repo.getDrives().filter { it.type == DriveType.SMB }
        if (drives.isEmpty()) {
            Toast.makeText(requireContext(), "No hay unidades SMB. Añade una en Mis Unidades.", Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.drivesFragment)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Elige unidad SMB")
            .setItems(drives.map { it.name }.toTypedArray()) { _, i ->
                val d = drives[i]
                val pathIn = EditText(requireContext()).apply {
                    hint = "Ruta archivo dentro del share (ej. foto.jpg o Docs/a.pdf)"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Archivo a copiar")
                    .setView(pathIn)
                    .setPositiveButton("Copiar al teléfono") { _, _ ->
                        val rel = pathIn.text.toString().trim()
                        if (rel.isEmpty()) return@setPositiveButton
                        val m = Regex("""^//([^/]+)/(.+)$""").find(d.path) ?: return@setPositiveButton
                        val host = m.groupValues[1]
                        val share = m.groupValues[2]
                        val prefs = requireContext().getSharedPreferences("smb_creds", 0)
                        val user = prefs.getString("${d.id}_user", "") ?: ""
                        val pass = prefs.getString("${d.id}_pass", "") ?: ""
                        val domain = prefs.getString("${d.id}_domain", "") ?: ""
                        val destDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                            ?: requireContext().filesDir
                        val dest = File(destDir, File(rel).name)
                        Toast.makeText(requireContext(), "Copiando…", Toast.LENGTH_SHORT).show()
                        viewLifecycleOwner.lifecycleScope.launch {
                            val r = SmbHelper.downloadFile(host, share, user, pass, rel, dest, domain)
                            val item = TransferItem(
                                UUID.randomUUID().toString(),
                                dest.name,
                                "//$host/$share/$rel",
                                dest.absolutePath,
                                if (r.success) 100 else 0,
                                if (r.success) "completed" else "error"
                            )
                            val all = repo.getTransfers()
                            all.add(0, item)
                            repo.saveTransfers(all)
                            Toast.makeText(requireContext(), r.message, Toast.LENGTH_LONG).show()
                            refresh()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
