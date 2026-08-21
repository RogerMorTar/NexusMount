package com.nexusmount.app.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.search.FileSearch
import com.nexusmount.app.search.SearchQuery
import com.nexusmount.app.transfer.TransferManager
import com.nexusmount.app.zip.ZipUtils
import kotlinx.coroutines.launch
import java.io.File

class FilesFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SimpleAdapter
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var clipboard: Pair<File, Boolean>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            toast("Permiso concedido")
            openBestRoot()
        } else {
            toast("Sin permiso de almacenamiento")
            showPermissionHelp()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Explorador Pro"
        binding.primaryAction.text = "Acciones / Permisos"
        binding.primaryAction.setOnClickListener { showActionsMenu() }

        adapter = SimpleAdapter(emptyList()) { pos ->
            val files = listCurrent()
            if (pos !in files.indices) return@SimpleAdapter
            val f = files[pos]
            AlertDialog.Builder(requireContext())
                .setTitle(f.name)
                .setItems(
                    arrayOf(
                        if (f.isDirectory) "Abrir" else "Info",
                        "Copiar", "Cortar", "Renombrar", "Eliminar",
                        "Comprimir ZIP",
                        if (f.extension.equals("zip", true)) "Extraer ZIP" else "—"
                    )
                ) { _, which ->
                    when (which) {
                        0 -> if (f.isDirectory) {
                            currentDir = f
                            loadDir()
                        } else toast("${f.name}\n${f.length()} bytes")
                        1 -> { clipboard = f to false; toast("Copiado") }
                        2 -> { clipboard = f to true; toast("Cortado") }
                        3 -> promptRename(f)
                        4 -> {
                            if (f.deleteRecursively()) { toast("Eliminado"); loadDir() }
                            else toast("No se pudo eliminar")
                        }
                        5 -> compress(f)
                        6 -> if (f.extension.equals("zip", true)) extract(f)
                    }
                }
                .show()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        ensureStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        // Always refresh after returning from system settings
        if (hasFullStorageAccess()) {
            openBestRoot()
            toast("Permiso de almacenamiento: concedido")
        } else {
            binding.subtitleText.text = (currentDir.absolutePath + " · SIN permiso completo")
            binding.emptyText.visibility = android.view.View.VISIBLE
            binding.emptyText.text = "Permiso pendiente. Acciones → Pedir permiso de todos los archivos, luego vuelve aquí."
        }
    }

    private fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureStorageAccess() {
        if (hasFullStorageAccess()) {
            openBestRoot()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showPermissionHelp()
            // Still try app-specific dirs so something opens
            openBestRoot()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun showPermissionHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("Acceso al almacenamiento")
            .setMessage(
                "Para abrir el almacenamiento interno y los discos, Android pide el permiso " +
                    "«Permitir acceso a todos los archivos».\n\n" +
                    "1. Pulsa Abrir ajustes\n" +
                    "2. Activa el permiso para NexusMount\n" +
                    "3. Vuelve a la app"
            )
            .setPositiveButton("Abrir ajustes") { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${requireContext().packageName}")
                        }
                        startActivity(intent)
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    }
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {
                        toast("Abre Ajustes → Apps → NexusMount → Permisos")
                    }
                }
            }
            .setNegativeButton("Usar carpeta de la app", null)
            .show()
    }

    /** Prefer shared storage; fall back to accessible app dirs. */
    private fun openBestRoot() {
        val candidates = mutableListOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { candidates.add(it) }
        } catch (_: Exception) {}
        try {
            File("/storage/emulated/0").let { if (it.exists()) candidates.add(it) }
        } catch (_: Exception) {}
        try {
            requireContext().getExternalFilesDir(null)?.let { candidates.add(it) }
        } catch (_: Exception) {}
        try {
            requireContext().filesDir?.let { candidates.add(it) }
        } catch (_: Exception) {}

        val root = candidates.firstOrNull { canList(it) }
            ?: candidates.firstOrNull()
            ?: requireContext().filesDir

        currentDir = root
        loadDir()
    }

    private fun canList(dir: File): Boolean {
        return try {
            dir.exists() && dir.isDirectory && dir.canRead() && dir.listFiles() != null
        } catch (_: Exception) {
            false
        }
    }

    private fun showActionsMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("Acciones")
            .setItems(
                arrayOf(
                    "Subir nivel",
                    "Nueva carpeta",
                    "Pegar aquí",
                    "Buscar…",
                    "Almacenamiento interno (/storage/emulated/0)",
                    "Carpeta de la app (siempre accesible)",
                    "Pedir permiso de todos los archivos",
                    "Actualizar"
                )
            ) { _, which ->
                when (which) {
                    0 -> currentDir.parentFile?.let { currentDir = it; loadDir() }
                    1 -> promptMkdir()
                    2 -> pasteHere()
                    3 -> promptSearch()
                    4 -> {
                        currentDir = File("/storage/emulated/0")
                        if (!canList(currentDir)) {
                            toast("Sin acceso — concede permiso de todos los archivos")
                            showPermissionHelp()
                        }
                        loadDir()
                    }
                    5 -> {
                        currentDir = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
                        loadDir()
                    }
                    6 -> showPermissionHelp()
                    7 -> loadDir()
                }
            }
            .show()
    }

    private fun listCurrent(): List<File> {
        return try {
            if (!currentDir.exists()) return emptyList()
            currentDir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        } catch (e: SecurityException) {
            toast("Sin permiso: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadDir() {
        binding.subtitleText.text = currentDir.absolutePath
        val files = listCurrent()
        if (files.isEmpty() && !canList(currentDir)) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.text =
                "No se puede leer esta carpeta.\n\nPulsa «Acciones / Permisos» → «Pedir permiso de todos los archivos»."
            adapter.submit(emptyList())
            return
        }
        adapter.submit(files.map { f ->
            f.name to if (f.isDirectory) "Carpeta" else formatSize(f.length())
        })
        binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "Carpeta vacía"
    }

    private fun promptMkdir() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle("Nueva carpeta")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val dir = File(currentDir, name)
                    if (dir.mkdir()) { toast("Creada"); loadDir() } else toast("Error al crear")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptRename(f: File) {
        val input = EditText(requireContext()).apply { setText(f.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Renombrar")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val neu = input.text.toString().trim()
                if (neu.isNotEmpty() && f.renameTo(File(f.parentFile, neu))) {
                    toast("Renombrado"); loadDir()
                } else toast("Error")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pasteHere() {
        val clip = clipboard ?: run { toast("Portapapeles vacío"); return }
        val (src, cut) = clip
        val dest = File(currentDir, src.name)
        val repo = (requireActivity().application as NexusApp).repository
        val tm = TransferManager(repo)
        viewLifecycleOwner.lifecycleScope.launch {
            toast("Transfiriendo…")
            val result = if (cut) tm.moveFile(src, dest) else tm.copyFile(src, dest)
            result.onSuccess {
                if (cut) clipboard = null
                toast("Listo")
                loadDir()
            }.onFailure { toast("Error: ${it.message}") }
        }
    }

    private fun compress(f: File) {
        val dest = File(currentDir, f.nameWithoutExtension + ".zip")
        val r = ZipUtils.zip(listOf(f), dest)
        if (r.isSuccess) { toast("ZIP creado: ${dest.name}"); loadDir() }
        else toast("Error ZIP: ${r.exceptionOrNull()?.message}")
    }

    private fun extract(f: File) {
        val dest = File(currentDir, f.nameWithoutExtension)
        val r = ZipUtils.unzip(f, dest)
        if (r.isSuccess) { toast("Extraído"); loadDir() }
        else toast("Error: ${r.exceptionOrNull()?.message}")
    }

    private fun promptSearch() {
        val input = EditText(requireContext()).apply { hint = "Nombre contiene…" }
        AlertDialog.Builder(requireContext())
            .setTitle("Búsqueda")
            .setView(input)
            .setPositiveButton("Buscar") { _, _ ->
                val q = input.text.toString().trim()
                val hits = FileSearch.search(currentDir, SearchQuery(nameContains = q.ifBlank { null }), 100)
                AlertDialog.Builder(requireContext())
                    .setTitle("${hits.size} resultados")
                    .setItems(hits.map { it.path }.toTypedArray()) { _, i ->
                        val hit = hits[i]
                        currentDir = if (hit.isDir) hit.file else (hit.file.parentFile ?: currentDir)
                        loadDir()
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
}
