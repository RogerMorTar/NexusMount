package com.nexusmount.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.share.ReadOnlyShareServer
import com.nexusmount.app.share.ShareServerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Interconexión SIN Samba:
 * - Este dispositivo puede EXPONER su disco (HTTP solo lectura).
 * - Puede VER el disco de otro dispositivo NexusMount por IP (Wi‑Fi o Tailscale).
 * Solo ver y copiar. Editar/borrar = solo vía SMB en Mis Unidades.
 */
class InterconnectFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var remoteHost = ""
    private var remotePort = ReadOnlyShareServer.DEFAULT_PORT
    private var pathStack = mutableListOf<String>()
    private var browsing = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && !browsing) showHome()
    }

    private fun showHome() {
        browsing = false
        pathStack.clear()
        binding.titleText.text = "Interconexión (visor)"
        binding.subtitleText.text = "Sin Samba · solo ver/copiar · Wi‑Fi o Tailscale"
        binding.primaryAction.text = if (ShareServerHolder.isRunning()) "Detener exposición" else "Exponer mi disco"
        binding.primaryAction.setOnClickListener {
            if (ShareServerHolder.isRunning()) {
                ShareServerHolder.stop()
                toast("Exposición detenida")
                showHome()
            } else {
                try {
                    val msg = ShareServerHolder.start(requireContext())
                    AlertDialog.Builder(requireContext())
                        .setTitle("Disco expuesto (solo lectura)")
                        .setMessage(
                            "$msg\n\nOtros dispositivos NexusMount → Interconexión → " +
                                "ponen tu IP y puerto ${ReadOnlyShareServer.DEFAULT_PORT}."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    showHome()
                } catch (e: Exception) {
                    toast("No se pudo iniciar: ${e.message}")
                }
            }
        }

        val ips = ReadOnlyShareServer.localIpv4()
        val lines = mutableListOf(
            "── En este dispositivo ──" to "",
            "Estado exposición" to if (ShareServerHolder.isRunning()) "ACTIVA (solo lectura)" else "Parada",
            "Mis IPs" to if (ips.isEmpty()) "—" else ips.joinToString(),
            "Puerto" to "${ReadOnlyShareServer.DEFAULT_PORT}",
            "── Ver otro dispositivo ──" to "",
            "Conectar por IP" to "Tablet/PC/móvil con NexusMount exponiendo disco",
            "Cómo funciona" to "No usa Samba. El otro equipo debe tener «Exponer mi disco» activo.",
            "Editar o borrar archivos" to "Solo con SMB en Mis Unidades (compartido del PC/NAS)"
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(lines) { pos ->
            when (lines[pos].first) {
                "Conectar por IP" -> connectDialog()
                "Cómo funciona" -> AlertDialog.Builder(requireContext())
                    .setTitle("Interconexión")
                    .setMessage(
                        "1. En el dispositivo A: Interconexión → Exponer mi disco.\n" +
                            "2. Anota su IP (Wi‑Fi 192.168.x o Tailscale 100.x).\n" +
                            "3. En el dispositivo B: Interconexión → Conectar por IP.\n" +
                            "4. Solo puedes listar, abrir y copiar.\n" +
                            "5. Para modificar: expón por Samba en el PC y usa Mis Unidades."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun connectDialog() {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val host = EditText(requireContext()).apply {
            hint = "IP (100.x Tailscale o 192.168.x Wi‑Fi)"
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
        }
        val port = EditText(requireContext()).apply {
            hint = "Puerto (por defecto ${ReadOnlyShareServer.DEFAULT_PORT})"
            setText(ReadOnlyShareServer.DEFAULT_PORT.toString())
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
        }
        box.addView(host)
        box.addView(port)
        AlertDialog.Builder(requireContext())
            .setTitle("Ver disco remoto")
            .setMessage("El otro dispositivo debe tener NexusMount con «Exponer mi disco» activo.")
            .setView(box)
            .setPositiveButton("Conectar") { _, _ ->
                val h = host.text.toString().trim()
                val p = port.text.toString().toIntOrNull() ?: ReadOnlyShareServer.DEFAULT_PORT
                if (h.isEmpty()) {
                    toast("IP obligatoria")
                    return@setPositiveButton
                }
                remoteHost = h
                remotePort = p
                pathStack.clear()
                browseRemote()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun currentPath(): String = pathStack.joinToString("/")

    private fun browseRemote() {
        browsing = true
        val path = currentPath()
        binding.titleText.text = "Visor $remoteHost"
        binding.subtitleText.text = if (path.isEmpty()) "/" else "/$path"
        binding.primaryAction.text = if (pathStack.isEmpty()) "← Salir del visor" else "↑ Subir"
        binding.primaryAction.setOnClickListener {
            if (pathStack.isEmpty()) showHome()
            else {
                pathStack.removeAt(pathStack.lastIndex)
                browseRemote()
            }
        }
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Cargando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchList(remoteHost, remotePort, path) }
            if (result == null) {
                binding.emptyText.text = "No se pudo conectar a $remoteHost:$remotePort\n¿Exposición activa en el otro dispositivo?"
                binding.recycler.adapter = SimpleAdapter(emptyList())
                return@launch
            }
            val entries = result
            binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "Carpeta vacía"
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(
                entries.map {
                    val icon = if (it.dir) "📁 " else "📄 "
                    icon + it.name to if (it.dir) "Carpeta" else formatSize(it.size)
                }
            ) { pos ->
                val e = entries[pos]
                if (e.dir) {
                    pathStack.add(e.name)
                    browseRemote()
                } else {
                    fileMenu(e)
                }
            }
        }
    }

    private data class Entry(val name: String, val dir: Boolean, val size: Long, val path: String)

    private fun fetchList(host: String, port: Int, path: String): List<Entry>? {
        return try {
            val q = if (path.isEmpty()) "" else "?path=${URLEncoder.encode(path, "UTF-8")}"
            val url = URL("http://$host:$port/api/list$q")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val arr = json.getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    o.getString("name"),
                    o.optBoolean("dir"),
                    o.optLong("size"),
                    o.optString("path")
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fileMenu(e: Entry) {
        AlertDialog.Builder(requireContext())
            .setTitle(e.name)
            .setItems(arrayOf("Abrir (descargar y ver)", "Copiar al teléfono", "Info")) { _, w ->
                when (w) {
                    0 -> downloadAndOpen(e)
                    1 -> downloadOnly(e)
                    2 -> AlertDialog.Builder(requireContext())
                        .setMessage("Tamaño: ${formatSize(e.size)}\nRuta: ${e.path}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun downloadAndOpen(e: Entry) {
        toast("Descargando…")
        viewLifecycleOwner.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { downloadToCache(e) }
            if (file == null) {
                toast("Error al descargar")
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().packageName + ".fileprovider",
                    file
                )
                val ext = file.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Abrir ${e.name}"
                    )
                )
            } catch (ex: Exception) {
                toast("No se pudo abrir: ${ex.message}")
            }
        }
    }

    private fun downloadOnly(e: Entry) {
        toast("Copiando…")
        viewLifecycleOwner.lifecycleScope.launch {
            val destDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: requireContext().filesDir
            val file = withContext(Dispatchers.IO) {
                downloadTo(e, File(destDir, e.name))
            }
            toast(if (file != null) "Guardado: ${file.absolutePath}" else "Error al copiar")
        }
    }

    private fun downloadToCache(e: Entry): File? {
        val dir = File(requireContext().cacheDir, "interconnect").also { it.mkdirs() }
        return downloadTo(e, File(dir, e.name))
    }

    private fun downloadTo(e: Entry, dest: File): File? {
        return try {
            val enc = e.path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
            val url = URL("http://$remoteHost:$remotePort/file/$enc")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 120000
            }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${b / (1024 * 1024 * 1024)} GB"
    }

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
