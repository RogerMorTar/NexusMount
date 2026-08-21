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
import com.nexusmount.app.interconnect.InterconnectClient
import com.nexusmount.app.interconnect.InterconnectServer
import com.nexusmount.app.interconnect.RemoteEntry
import com.nexusmount.app.util.TailscaleUtil
import kotlinx.coroutines.launch
import java.io.File

/**
 * Interconexión: visor de disco entre dispositivos con NexusMount (app o web host).
 * - Este teléfono puede EXPONER su disco (solo lectura).
 * - Puede ABRIR el disco de otro dispositivo por IP Wi‑Fi/Tailscale (solo ver/copiar).
 * Edición/borrado = solo vía Samba en Mis Unidades.
 */
class InterconnectFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var remoteHost: String? = null
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

    private fun server() = InterconnectServer.get(requireContext())

    private fun showHome() {
        browsing = false
        remoteHost = null
        pathStack.clear()
        binding.titleText.text = "Interconexión (visor)"
        binding.subtitleText.text =
            "Sin Samba · solo ver/copiar · el otro lado debe tener NexusMount (app/web)"
        val exposing = InterconnectServer.running
        val myIps = TailscaleUtil.getTailscaleIpv4().ifEmpty {
            listOf("(sin IP Tailscale — usa IP Wi‑Fi del teléfono)")
        }
        binding.primaryAction.text =
            if (exposing) "Detener exposición de mi disco" else "Exponer mi disco (solo lectura)"
        binding.primaryAction.setOnClickListener {
            if (InterconnectServer.running) {
                server().stop()
                toast("Ya no se expone el disco")
                showHome()
            } else {
                val ok = server().start()
                if (ok) {
                    toast("Expuesto en puerto ${InterconnectServer.PORT} (solo lectura)")
                } else {
                    toast("No se pudo iniciar el visor (¿puerto ocupado?)")
                }
                showHome()
            }
        }

        val rows = mutableListOf(
            "── Este dispositivo ──" to "",
            "Estado exposición" to if (exposing)
                "ACTIVO · puerto ${InterconnectServer.PORT} · ${InterconnectServer.rootPath}"
            else "Inactivo — otros no pueden ver tu disco",
            "Tus IPs Tailscale" to myIps.joinToString(" · "),
            "── Ver otro dispositivo ──" to "",
            "Abrir por IP (Wi‑Fi o Tailscale)" to "El otro debe tener NexusMount con disco expuesto",
            "── Modelo ──" to "",
            "Qué es" to "Visor de disco entre NexusMount. No usa Samba.",
            "Qué no hace" to "No borra ni modifica en remoto. Eso solo con SMB en Mis Unidades.",
            "Web / PC" to "La app o el host web en el otro dispositivo debe exponer el disco igual"
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(rows) { pos ->
            if (rows[pos].first.contains("Abrir por IP")) openRemoteDialog()
        }
    }

    private fun openRemoteDialog() {
        val input = EditText(requireContext()).apply {
            hint = "IP (100.x.x.x o 192.168.x.x)"
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Visor remoto NexusMount")
            .setMessage(
                "En el otro dispositivo debe estar NexusMount (app o programa web) " +
                    "con «Exponer mi disco» activo.\nPuerto ${InterconnectServer.PORT}."
            )
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                val host = input.text.toString().trim()
                if (host.isEmpty()) return@setPositiveButton
                toast("Comprobando $host…")
                viewLifecycleOwner.lifecycleScope.launch {
                    val r = InterconnectClient.probe(host)
                    if (!r.success) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("No conecta")
                            .setMessage(r.message)
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        remoteHost = host
                        pathStack.clear()
                        browseRemote()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun browseRemote() {
        val host = remoteHost ?: return showHome()
        browsing = true
        val path = pathStack.joinToString("/")
        binding.titleText.text = "Visor · $host"
        binding.subtitleText.text = if (path.isEmpty()) "/" else "/$path"
        binding.primaryAction.text =
            if (pathStack.isEmpty()) "← Salir del visor" else "↑ Subir carpeta"
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
            val r = InterconnectClient.list(host, path)
            if (!r.success) {
                binding.emptyText.text = r.message
                binding.recycler.adapter = SimpleAdapter(emptyList())
                return@launch
            }
            val entries = r.entries
            binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "Carpeta vacía"
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(
                entries.map {
                    val icon = if (it.isDir) "📁 " else "📄 "
                    icon + it.name to if (it.isDir) "Carpeta · solo lectura"
                    else "${formatSize(it.size)} · abrir/copiar"
                }
            ) { pos ->
                val e = entries[pos]
                if (e.isDir) {
                    pathStack.add(e.name)
                    browseRemote()
                } else {
                    fileMenu(host, e)
                }
            }
        }
    }

    private fun fileMenu(host: String, e: RemoteEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("${e.name} · solo lectura")
            .setItems(arrayOf("Abrir con…", "Copiar al teléfono", "Información")) { _, which ->
                when (which) {
                    0, 1 -> downloadAndMaybeOpen(host, e, open = which == 0)
                    2 -> AlertDialog.Builder(requireContext())
                        .setMessage("Ruta: /${e.path}\nTamaño: ${formatSize(e.size)}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun downloadAndMaybeOpen(host: String, e: RemoteEntry, open: Boolean) {
        toast("Copiando ${e.name}…")
        viewLifecycleOwner.lifecycleScope.launch {
            val dir = if (open) {
                File(requireContext().cacheDir, "interconnect").also { it.mkdirs() }
            } else {
                requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: requireContext().filesDir
            }
            val dest = File(dir, e.name)
            val r = InterconnectClient.download(host, e.path, dest)
            toast(r.message)
            if (r.success && open) {
                try {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().packageName + ".fileprovider",
                        dest
                    )
                    val ext = e.name.substringAfterLast('.', "").lowercase()
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
