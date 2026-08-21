package com.nexusmount.app.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.data.FileEntry
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.transfer.TransferManager
import com.nexusmount.app.data.TransferItem
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Explorador tipo Explorer: carpetas, abrir archivos con apps del sistema,
 * copiar al móvil, eliminar.
 */
class SmbBrowserFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var host = ""
    private var share = ""
    private var user = ""
    private var pass = ""
    private var domain = ""
    private var pathStack = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            host = it.getString("host") ?: ""
            share = it.getString("share") ?: ""
            user = it.getString("user") ?: ""
            pass = it.getString("pass") ?: ""
            domain = it.getString("domain") ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "//$host/$share"
        binding.primaryAction.text = "↑ Subir / Acciones"
        binding.primaryAction.setOnClickListener { showRootActions() }
        load()
    }

    private fun currentPath(): String = pathStack.joinToString("/")

    private fun showRootActions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Explorador SMB")
            .setItems(arrayOf("Subir un nivel", "Actualizar", "Ir a la raíz")) { _, w ->
                when (w) {
                    0 -> {
                        if (pathStack.isNotEmpty()) {
                            pathStack.removeAt(pathStack.lastIndex)
                            load()
                        } else toast("Ya estás en la raíz")
                    }
                    1 -> load()
                    2 -> { pathStack.clear(); load() }
                }
            }
            .show()
    }

    private fun load() {
        val remote = currentPath()
        binding.subtitleText.text = if (remote.isEmpty()) "/  ·  toca carpeta o archivo" else "/$remote"
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Cargando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SmbHelper.listFiles(host, share, user, pass, remote, domain)
            if (!result.success) {
                binding.emptyText.text = result.message
                binding.recycler.adapter = SimpleAdapter(emptyList())
                toast(result.message)
                return@launch
            }
            val files = result.files.sortedWith(
                compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
            )
            binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "Carpeta vacía"
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(
                files.map {
                    val icon = if (it.isDirectory) "📁 " else "📄 "
                    icon + it.name to if (it.isDirectory) "Carpeta · tocar para abrir" else "${formatSize(it.sizeBytes)} · tocar para abrir"
                }
            ) { pos ->
                val f = files[pos]
                if (f.isDirectory) {
                    pathStack.add(f.name)
                    load()
                } else {
                    showFileMenu(f)
                }
            }
        }
    }

    private fun showFileMenu(f: FileEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(f.name)
            .setItems(
                arrayOf(
                    "Abrir con…",
                    "Copiar al teléfono",
                    "Información",
                    "Eliminar del servidor"
                )
            ) { _, which ->
                when (which) {
                    0 -> openFile(f)
                    1 -> copyToPhone(f)
                    2 -> AlertDialog.Builder(requireContext())
                        .setTitle(f.name)
                        .setMessage("Tamaño: ${formatSize(f.sizeBytes)}\nRuta: /${f.path}")
                        .setPositiveButton("OK", null)
                        .show()
                    3 -> confirmDelete(f)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun openFile(f: FileEntry) {
        toast("Abriendo ${f.name}…")
        viewLifecycleOwner.lifecycleScope.launch {
            val cacheDir = File(requireContext().cacheDir, "smb_open").also { it.mkdirs() }
            val dest = File(cacheDir, f.name)
            val result = SmbHelper.downloadFile(host, share, user, pass, f.path, dest, domain)
            if (!result.success) {
                toast(result.message)
                return@launch
            }
            // Registrar transferencia
            try {
                val repo = (requireActivity().application as NexusApp).repository
                val list = repo.getTransfers()
                list.add(0, TransferItem(UUID.randomUUID().toString(), f.name, "//$host/$share", dest.absolutePath, 100, "completed"))
                repo.saveTransfers(list)
            } catch (_: Exception) {}

            val uri = try {
                FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().packageName + ".fileprovider",
                    dest
                )
            } catch (e: Exception) {
                Uri.fromFile(dest)
            }
            val mime = mimeFor(f.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(Intent.createChooser(intent, "Abrir ${f.name} con"))
            } catch (e: ActivityNotFoundException) {
                toast("No hay app para abrir este tipo de archivo ($mime)")
            } catch (e: Exception) {
                toast("No se pudo abrir: ${e.message}")
            }
        }
    }

    private fun copyToPhone(f: FileEntry) {
        toast("Copiando ${f.name}…")
        viewLifecycleOwner.lifecycleScope.launch {
            val dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: requireContext().getExternalFilesDir(null)
                ?: requireContext().filesDir
            val dest = File(dir, f.name)
            val result = SmbHelper.downloadFile(host, share, user, pass, f.path, dest, domain)
            if (result.success) {
                toast("Guardado en: ${dest.absolutePath}")
            } else {
                toast(result.message)
            }
        }
    }

    private fun confirmDelete(f: FileEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("¿Eliminar ${f.name}?")
            .setMessage("Se borrará del servidor Samba. No se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val r = SmbHelper.deleteRemote(host, share, user, pass, f.path, f.isDirectory, domain)
                    toast(r.message)
                    if (r.success) load()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "pdf" -> "application/pdf"
                "txt", "log", "md" -> "text/plain"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                "zip" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                else -> "application/octet-stream"
            }
    }

    private fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${b / (1024 * 1024 * 1024)} GB"
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun args(host: String, share: String, user: String, pass: String, domain: String) =
            Bundle().apply {
                putString("host", host)
                putString("share", share)
                putString("user", user)
                putString("pass", pass)
                putString("domain", domain)
            }
    }
}
