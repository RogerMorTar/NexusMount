package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.files.ExplorerFavorites
import com.nexusmount.app.files.ExplorerUtils
import com.nexusmount.app.files.FileClipboard
import com.nexusmount.app.zip.ZipUtils
import java.io.File

/**
 * Explorador estilo ES File Explorer:
 * navegar, multiselección, copiar/cortar/pegar, renombrar, favoritos,
 * categorías media, ocultos, propiedades, compartir, zip.
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private var files: List<File> = emptyList()
    private val selected = mutableSetOf<String>()
    private var multiMode = false
    private var showHidden = false
    private var sortBy = "name" // name | size | date
    private var categoryMode: String? = null // images, music, ...

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        openBestRoot()
        binding.primaryAction.setOnClickListener { showActionsMenu() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (multiMode) {
                        multiMode = false
                        selected.clear()
                        loadDir()
                    } else if (categoryMode != null) {
                        categoryMode = null
                        loadDir()
                    } else {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            currentDir = parent
                            loadDir()
                        } else {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
        loadDir()
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        if (hasFullStorageAccess()) {
            if (currentDir.listFiles() == null) openBestRoot()
            else loadDir()
        } else {
            binding.subtitleText.text = currentDir.absolutePath + " · SIN permiso completo"
        }
    }

    private fun openBestRoot() {
        val candidates = listOf(
            File("/storage/emulated/0"),
            Environment.getExternalStorageDirectory(),
            requireContext().getExternalFilesDir(null),
            requireContext().filesDir
        )
        currentDir = candidates.firstOrNull {
            it != null && it.exists() && it.canRead() && it.listFiles() != null
        } ?: requireContext().filesDir
    }

    private fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    private fun loadDir() {
        selected.clear()
        multiMode = false
        val cat = categoryMode
        files = if (cat != null) {
            ExplorerUtils.mediaCategory(currentDir, cat)
        } else {
            val list = currentDir.listFiles()?.toList() ?: emptyList()
            list.filter { showHidden || !it.name.startsWith(".") }
                .sortedWith(
                    when (sortBy) {
                        "size" -> compareByDescending<File> { if (it.isFile) it.length() else 0L }
                            .thenBy { it.name.lowercase() }
                        "date" -> compareByDescending<File> { it.lastModified() }
                        else -> compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                    }
                )
        }
        binding.titleText.text = if (cat != null) "Categoría: $cat" else "Explorador Pro"
        binding.subtitleText.text = currentDir.absolutePath
        binding.primaryAction.text = "☰ Acciones"
        binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "Carpeta vacía o sin permiso"
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            files.map { f ->
                val icon = when {
                    f.isDirectory -> "📁 "
                    f.extension.lowercase() in listOf("jpg", "png", "gif", "webp") -> "🖼️ "
                    f.extension.lowercase() in listOf("mp3", "wav", "flac") -> "🎵 "
                    f.extension.lowercase() in listOf("mp4", "mkv", "avi") -> "🎬 "
                    f.extension.lowercase() == "apk" -> "📦 "
                    f.extension.lowercase() in listOf("zip", "rar", "7z") -> "🗜️ "
                    else -> "📄 "
                }
                val mark = if (f.absolutePath in selected) "✓ " else ""
                mark + icon + f.name to
                    if (f.isDirectory) "Carpeta · ${ExplorerUtils.formatDate(f.lastModified())}"
                    else "${ExplorerUtils.formatSize(f.length())} · ${ExplorerUtils.formatDate(f.lastModified())}"
            }
        ) { pos ->
            if (pos !in files.indices) return@SimpleAdapter
            val f = files[pos]
            if (multiMode) {
                if (f.absolutePath in selected) selected.remove(f.absolutePath)
                else selected.add(f.absolutePath)
                loadDirKeepMulti()
            } else {
                if (f.isDirectory && categoryMode == null) {
                    currentDir = f
                    loadDir()
                } else {
                    showItemMenu(f)
                }
            }
        }
    }

    private fun loadDirKeepMulti() {
        val keep = multiMode
        loadDir()
        multiMode = keep
        binding.primaryAction.text = "Selección (${selected.size})"
        binding.primaryAction.setOnClickListener { showSelectionMenu() }
    }

    private fun showActionsMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("Explorador · acciones")
            .setItems(
                arrayOf(
                    "↑ Carpeta superior",
                    "Nueva carpeta",
                    "Nuevo archivo de texto",
                    "Multiselección",
                    "Pegar aquí (${FileClipboard.paths.size})",
                    "Buscar en esta carpeta",
                    "Favoritos",
                    "Categorías (Imágenes/Música/Vídeo/Docs/APK)",
                    "Ordenar (nombre/tamaño/fecha)",
                    "Mostrar ocultos: ${if (showHidden) "Sí" else "No"}",
                    "Ir a almacenamiento raíz",
                    "Ir a Descargas",
                    "Actualizar"
                )
            ) { _, w ->
                when (w) {
                    0 -> {
                        currentDir.parentFile?.let {
                            if (it.canRead()) {
                                currentDir = it
                                categoryMode = null
                                loadDir()
                            }
                        }
                    }
                    1 -> newFolder()
                    2 -> newTextFile()
                    3 -> {
                        multiMode = true
                        selected.clear()
                        binding.primaryAction.text = "Selección (0)"
                        binding.primaryAction.setOnClickListener { showSelectionMenu() }
                        toast("Toca archivos para seleccionar")
                    }
                    4 -> pasteHere()
                    5 -> searchHere()
                    6 -> showFavorites()
                    7 -> showCategories()
                    8 -> showSort()
                    9 -> {
                        showHidden = !showHidden
                        loadDir()
                    }
                    10 -> {
                        categoryMode = null
                        openBestRoot()
                        loadDir()
                    }
                    11 -> {
                        categoryMode = null
                        val dl = File("/storage/emulated/0/Download")
                        currentDir = if (dl.canRead()) dl else currentDir
                        loadDir()
                    }
                    12 -> loadDir()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSelectionMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("Selección: ${selected.size}")
            .setItems(
                arrayOf(
                    "Copiar",
                    "Cortar",
                    "Eliminar",
                    "Comprimir ZIP",
                    "Compartir (1er archivo)",
                    "Seleccionar todo",
                    "Cancelar selección"
                )
            ) { _, w ->
                when (w) {
                    0 -> {
                        FileClipboard.mode = FileClipboard.Mode.COPY
                        FileClipboard.paths = selected.toMutableList()
                        toast("Copiados ${selected.size}")
                        multiMode = false
                        loadDir()
                    }
                    1 -> {
                        FileClipboard.mode = FileClipboard.Mode.CUT
                        FileClipboard.paths = selected.toMutableList()
                        toast("Cortados ${selected.size}")
                        multiMode = false
                        loadDir()
                    }
                    2 -> confirmDeleteSelected()
                    3 -> zipSelected()
                    4 -> {
                        val first = selected.firstOrNull()?.let { File(it) }
                        if (first != null && first.isFile) ExplorerUtils.share(requireContext(), first)
                    }
                    5 -> {
                        selected.clear()
                        files.forEach { selected.add(it.absolutePath) }
                        loadDirKeepMulti()
                    }
                    6 -> {
                        multiMode = false
                        selected.clear()
                        binding.primaryAction.setOnClickListener { showActionsMenu() }
                        loadDir()
                    }
                }
            }
            .show()
    }

    private fun showItemMenu(f: File) {
        val fav = ExplorerFavorites.list(requireContext()).contains(f.absolutePath)
        AlertDialog.Builder(requireContext())
            .setTitle(f.name)
            .setItems(
                arrayOf(
                    if (f.isDirectory) "Abrir" else "Abrir con…",
                    "Copiar",
                    "Cortar",
                    "Renombrar",
                    "Eliminar",
                    "Propiedades",
                    if (fav) "Quitar de favoritos" else "Añadir a favoritos",
                    "Compartir",
                    if (f.isFile && f.extension.lowercase() == "zip") "Descomprimir" else "Comprimir ZIP",
                    "Seleccionar"
                )
            ) { _, w ->
                when (w) {
                    0 -> if (f.isDirectory) {
                        currentDir = f
                        categoryMode = null
                        loadDir()
                    } else ExplorerUtils.open(requireContext(), f)
                    1 -> {
                        FileClipboard.mode = FileClipboard.Mode.COPY
                        FileClipboard.paths = mutableListOf(f.absolutePath)
                        toast("Copiado")
                    }
                    2 -> {
                        FileClipboard.mode = FileClipboard.Mode.CUT
                        FileClipboard.paths = mutableListOf(f.absolutePath)
                        toast("Cortado")
                    }
                    3 -> rename(f)
                    4 -> AlertDialog.Builder(requireContext())
                        .setTitle("¿Eliminar ${f.name}?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            if (f.deleteRecursively()) {
                                toast("Eliminado")
                                loadDir()
                            } else toast("No se pudo eliminar")
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    5 -> AlertDialog.Builder(requireContext())
                        .setTitle("Propiedades")
                        .setMessage(ExplorerUtils.properties(f))
                        .setPositiveButton("OK", null)
                        .show()
                    6 -> {
                        val added = ExplorerFavorites.toggle(requireContext(), f.absolutePath)
                        toast(if (added) "Añadido a favoritos" else "Quitado de favoritos")
                    }
                    7 -> if (f.isFile) ExplorerUtils.share(requireContext(), f)
                    else toast("Comparte archivos, no carpetas")
                    8 -> {
                        if (f.isFile && f.extension.lowercase() == "zip") {
                            val dest = File(currentDir, f.nameWithoutExtension)
                            val r = ZipUtils.unzip(f, dest)
                            toast(if (r.isSuccess) "Extraído" else "Error al extraer")
                            loadDir()
                        } else {
                            val dest = File(currentDir, f.nameWithoutExtension + ".zip")
                            val r = ZipUtils.zip(listOf(f), dest)
                            toast(if (r.isSuccess) "ZIP creado" else "Error ZIP")
                            loadDir()
                        }
                    }
                    9 -> {
                        multiMode = true
                        selected.clear()
                        selected.add(f.absolutePath)
                        loadDirKeepMulti()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun pasteHere() {
        if (!FileClipboard.hasItems()) {
            toast("Portapapeles vacío")
            return
        }
        var ok = 0
        FileClipboard.paths.forEach { p ->
            val src = File(p)
            if (!src.exists()) return@forEach
            val success = if (FileClipboard.mode == FileClipboard.Mode.CUT) {
                ExplorerUtils.moveFile(src, currentDir)
            } else {
                ExplorerUtils.copyFile(src, currentDir)
            }
            if (success) ok++
        }
        if (FileClipboard.mode == FileClipboard.Mode.CUT) FileClipboard.clear()
        toast("Pegados: $ok")
        loadDir()
    }

    private fun newFolder() {
        val input = EditText(requireContext()).apply { hint = "Nombre de carpeta" }
        AlertDialog.Builder(requireContext())
            .setTitle("Nueva carpeta")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val d = File(currentDir, name)
                toast(if (d.mkdir()) "Creada" else "No se pudo crear")
                loadDir()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun newTextFile() {
        val input = EditText(requireContext()).apply { hint = "nombre.txt" }
        AlertDialog.Builder(requireContext())
            .setTitle("Nuevo texto")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) name = "nuevo.txt"
                if (!name.contains(".")) name += ".txt"
                val f = File(currentDir, name)
                try {
                    f.writeText("")
                    toast("Creado: ${f.name}")
                    loadDir()
                } catch (e: Exception) {
                    toast("Error: ${e.message}")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun rename(f: File) {
        val input = EditText(requireContext()).apply { setText(f.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Renombrar")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val dest = File(f.parentFile, name)
                toast(if (f.renameTo(dest)) "Renombrado" else "Error")
                loadDir()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteSelected() {
        AlertDialog.Builder(requireContext())
            .setTitle("¿Eliminar ${selected.size} elementos?")
            .setPositiveButton("Eliminar") { _, _ ->
                selected.forEach { File(it).deleteRecursively() }
                multiMode = false
                selected.clear()
                binding.primaryAction.setOnClickListener { showActionsMenu() }
                toast("Eliminados")
                loadDir()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun zipSelected() {
        val list = selected.map { File(it) }.filter { it.exists() }
        if (list.isEmpty()) return
        val dest = File(currentDir, "seleccion_${System.currentTimeMillis()}.zip")
        val r = ZipUtils.zip(list, dest)
        toast(if (r.isSuccess) "ZIP: ${dest.name}" else "Error ZIP")
        multiMode = false
        selected.clear()
        binding.primaryAction.setOnClickListener { showActionsMenu() }
        loadDir()
    }

    private fun searchHere() {
        val input = EditText(requireContext()).apply { hint = "nombre o parte" }
        AlertDialog.Builder(requireContext())
            .setTitle("Buscar")
            .setView(input)
            .setPositiveButton("Buscar") { _, _ ->
                val q = input.text.toString().trim().lowercase()
                if (q.isEmpty()) return@setPositiveButton
                val hits = mutableListOf<File>()
                fun walk(d: File, depth: Int) {
                    if (depth > 4 || hits.size > 80) return
                    d.listFiles()?.forEach { f ->
                        if (f.name.lowercase().contains(q)) hits.add(f)
                        if (f.isDirectory) walk(f, depth + 1)
                    }
                }
                walk(currentDir, 0)
                if (hits.isEmpty()) toast("Sin resultados")
                else {
                    files = hits
                    categoryMode = "search"
                    binding.titleText.text = "Búsqueda: $q"
                    binding.recycler.adapter = SimpleAdapter(
                        files.map {
                            (if (it.isDirectory) "📁 " else "📄 ") + it.name to it.absolutePath
                        }
                    ) { pos ->
                        val f = files[pos]
                        if (f.isDirectory) {
                            currentDir = f
                            categoryMode = null
                            loadDir()
                        } else showItemMenu(f)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFavorites() {
        val favs = ExplorerFavorites.list(requireContext())
        if (favs.isEmpty()) {
            toast("Sin favoritos")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Favoritos")
            .setItems(favs.map { File(it).name + "\n" + it }.toTypedArray()) { _, i ->
                val f = File(favs[i])
                if (f.isDirectory && f.canRead()) {
                    currentDir = f
                    categoryMode = null
                    loadDir()
                } else if (f.isFile) showItemMenu(f)
                else toast("No accesible")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showCategories() {
        val cats = arrayOf("images", "music", "video", "docs", "apk", "zip")
        val labels = arrayOf("Imágenes", "Música", "Vídeo", "Documentos", "APK", "Comprimidos")
        AlertDialog.Builder(requireContext())
            .setTitle("Categorías")
            .setItems(labels) { _, i ->
                categoryMode = cats[i]
                loadDir()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSort() {
        AlertDialog.Builder(requireContext())
            .setTitle("Ordenar por")
            .setItems(arrayOf("Nombre", "Tamaño", "Fecha")) { _, i ->
                sortBy = when (i) {
                    1 -> "size"
                    2 -> "date"
                    else -> "name"
                }
                loadDir()
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
