package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.data.DriveType
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.data.TransferItem
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.transfer.TransferManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TransfersFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val repo get() = (requireActivity().application as NexusApp).repository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Transferencias"
        binding.subtitleText.text = "Historial y nuevas copias"
        binding.primaryAction.text = "+ Nueva transferencia"
        binding.primaryAction.setOnClickListener { startNewTransfer() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val list = repo.getTransfers()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        if (list.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.text = "Sin transferencias.\nPulsa «+ Nueva transferencia» o copia desde el explorador SMB."
            binding.recycler.adapter = SimpleAdapter(emptyList())
        } else {
            binding.emptyText.visibility = View.GONE
            binding.recycler.adapter = SimpleAdapter(
                list.map { "${it.name} · ${it.progress}%" to "${it.status} · ${it.fromPath} → ${it.toPath}" }
            ) { pos ->
                val t = list[pos]
                AlertDialog.Builder(requireContext())
                    .setTitle(t.name)
                    .setMessage("Estado: ${t.status}\n${t.fromPath}\n→\n${t.toPath}")
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Quitar del historial") { _, _ ->
                        val next = list.toMutableList().also { it.removeAt(pos) }
                        repo.saveTransfers(next)
                        refresh()
                    }
                    .show()
            }
        }
    }

    private fun startNewTransfer() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tipo de transferencia")
            .setItems(
                arrayOf(
                    "Copiar archivo local → carpeta local",
                    "Descargar desde unidad SMB (solo lectura ok)",
                    "Ver historial / actualizar"
                )
            ) { _, which ->
                when (which) {
                    0 -> localCopyDialog()
                    1 -> smbDownloadDialog()
                    2 -> refresh()
                }
            }
            .show()
    }

    private fun localCopyDialog() {
        val src = EditText(requireContext()).apply { hint = "Ruta origen absoluta" }
        val dst = EditText(requireContext()).apply {
            hint = "Carpeta destino"
            setText(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "")
        }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
            addView(src); addView(dst)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Copia local")
            .setView(box)
            .setPositiveButton("Copiar") { _, _ ->
                val s = File(src.text.toString().trim())
                val dDir = File(dst.text.toString().trim())
                if (!s.exists()) {
                    toast("Origen no existe"); return@setPositiveButton
                }
                val dest = File(dDir, s.name)
                viewLifecycleOwner.lifecycleScope.launch {
                    val tm = TransferManager(repo)
                    toast("Copiando…")
                    val r = tm.copyFile(s, dest)
                    r.onSuccess {
                        toast("OK: ${dest.absolutePath}")
                        refresh()
                    }.onFailure { toast("Error: ${it.message}") }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun smbDownloadDialog() {
        val drives = repo.getDrives().filter { it.type == DriveType.SMB }
        if (drives.isEmpty()) {
            toast("No hay unidades SMB. Añade una en Mis Unidades.")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Unidad SMB origen")
            .setItems(drives.map { it.name }.toTypedArray()) { _, which ->
                val d = drives[which]
                val pathIn = EditText(requireContext()).apply {
                    hint = "Ruta relativa en el share (ej. docs/informe.pdf)"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Archivo a descargar")
                    .setView(pathIn)
                    .setPositiveButton("Descargar") { _, _ ->
                        val rel = pathIn.text.toString().trim()
                        if (rel.isEmpty()) {
                            toast("Indica la ruta del archivo"); return@setPositiveButton
                        }
                        val m = Regex("""^//([^/]+)/(.+)$""").find(d.path) ?: return@setPositiveButton
                        val host = m.groupValues[1]
                        val share = m.groupValues[2]
                        val prefs = requireContext().getSharedPreferences("smb_creds", 0)
                        val user = prefs.getString("${d.id}_user", "") ?: ""
                        val pass = prefs.getString("${d.id}_pass", "") ?: ""
                        val domain = prefs.getString("${d.id}_domain", "") ?: ""
                        val destDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: requireContext().filesDir
                        val dest = File(destDir, rel.substringAfterLast('/'))
                        viewLifecycleOwner.lifecycleScope.launch {
                            toast("Descargando…")
                            val r = SmbHelper.downloadFile(host, share, user, pass, rel, dest, domain)
                            if (r.success) {
                                val list = repo.getTransfers()
                                list.add(
                                    0,
                                    TransferItem(
                                        UUID.randomUUID().toString(),
                                        dest.name,
                                        "//$host/$share/$rel",
                                        dest.absolutePath,
                                        100,
                                        "completed"
                                    )
                                )
                                repo.saveTransfers(list)
                                toast("Guardado: ${dest.absolutePath}")
                                refresh()
                            } else toast(r.message)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .show()
    }

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
