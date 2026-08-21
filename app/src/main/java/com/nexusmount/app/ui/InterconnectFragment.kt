package com.nexusmount.app.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.R
import com.nexusmount.app.data.FileEntry
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * Interconexión por IP (LAN o Tailscale): SOLO ver y copiar.
 * No eliminar ni modificar. Para edición completa usar SMB en Mis Unidades.
 */
class InterconnectFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var host = ""
    private var share = ""
    private var user = ""
    private var pass = ""
    private var domain = ""
    private var pathStack = mutableListOf<String>()
    private var browsing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            host = it.getString("host") ?: ""
            share = it.getString("share") ?: ""
            user = it.getString("user") ?: ""
            pass = it.getString("pass") ?: ""
            domain = it.getString("domain") ?: ""
            browsing = host.isNotEmpty() && share.isNotEmpty()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browsing && pathStack.isNotEmpty()) {
                    pathStack.removeAt(pathStack.lastIndex)
                    load()
                } else if (browsing) {
                    browsing = false
                    showConnectForm()
                } else {
                    try { findNavController().navigate(R.id.collabFragment) } catch (_: Exception) {
                        findNavController().popBackStack()
                    }
                }
            }
        })

        if (browsing) {
            binding.titleText.text = "Visor · //$host/$share"
            binding.subtitleText.text = "Solo lectura: ver y copiar (sin borrar ni editar)"
            updateBtn()
            binding.primaryAction.setOnClickListener { onPrimary() }
            load()
        } else {
            showConnectForm()
        }
    }

    private fun showConnectForm() {
        browsing = false
        binding.titleText.text = "Interconexión de sistemas"
        binding.subtitleText.text = "IP local o Tailscale · solo ver y copiar"
        binding.primaryAction.text = "Conectar como visor"
        binding.primaryAction.setOnClickListener { promptConnect() }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            listOf(
                "Modo" to "Visor de solo lectura",
                "Red" to "IP 192.168.x o Tailscale 100.x",
                "Permisos" to "Ver carpetas · Abrir · Copiar al teléfono",
                "Prohibido aquí" to "Eliminar / renombrar / escribir",
                "Edición completa" to "Usa Mis Unidades → SMB con permisos de escritura",
                "Abrir con" to "WPS, PDF, galería… según el archivo"
            )
        ) { }
        binding.emptyText.visibility = View.GONE
    }

    private fun promptConnect() {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        fun field(h: String) = EditText(requireContext()).apply {
            hint = h
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
            box.addView(this)
        }
        val hostIn = field("IP del dispositivo (100.x o 192.168.x)")
        val shareIn = field("Nombre del share SMB expuesto")
        val userIn = field("Usuario (o vacío si invitado)")
        val passIn = field("Contraseña").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Conectar visor")
            .setMessage("El otro dispositivo debe tener Samba/compartido activo. Solo lectura en esta vista.")
            .setView(box)
            .setPositiveButton("Abrir visor") { _, _ ->
                host = hostIn.text.toString().trim()
                share = shareIn.text.toString().trim()
                user = userIn.text.toString().trim()
                pass = passIn.text.toString()
                if (host.isEmpty() || share.isEmpty()) {
                    toast("IP y share obligatorios")
                    return@setPositiveButton
                }
                browsing = true
                pathStack.clear()
                binding.titleText.text = "Visor · //$host/$share"
                binding.subtitleText.text = "Solo lectura · ver y copiar"
                updateBtn()
                binding.primaryAction.setOnClickListener { onPrimary() }
                load()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateBtn() {
        binding.primaryAction.text = if (pathStack.isEmpty()) "← Salir del visor" else "↑ Subir"
    }

    private fun onPrimary() {
        if (pathStack.isNotEmpty()) {
            pathStack.removeAt(pathStack.lastIndex)
            load()
        } else {
            showConnectForm()
        }
    }

    private fun currentPath() = pathStack.joinToString("/")

    private fun load() {
        updateBtn()
        val remote = currentPath()
        binding.subtitleText.text = (if (remote.isEmpty()) "/" else "/$remote") + " · solo lectura"
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
            val files = result.files
            binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyText.text = "Vacío"
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(
                files.map {
                    val icon = if (it.isDirectory) "📁 " else "📄 "
                    icon + it.name to if (it.isDirectory) "Carpeta" else "Abrir / copiar"
                }
            ) { pos ->
                val f = files[pos]
                if (f.isDirectory) {
                    pathStack.add(f.name)
                    load()
                } else {
                    fileMenu(f)
                }
            }
        }
    }

    private fun fileMenu(f: FileEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(f.name)
            .setItems(
                arrayOf(
                    "Abrir con… (WPS, PDF, galería…)",
                    "Copiar al teléfono",
                    "Información"
                )
            ) { _, w ->
                when (w) {
                    0 -> openFile(f)
                    1 -> copyFile(f)
                    2 -> AlertDialog.Builder(requireContext())
                        .setMessage("Solo lectura\n${f.path}\n${f.sizeBytes} bytes")
                        .setPositiveButton("OK", null).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun openFile(f: FileEntry) {
        toast("Descargando para abrir…")
        viewLifecycleOwner.lifecycleScope.launch {
            val dest = File(File(requireContext().cacheDir, "view").also { it.mkdirs() }, f.name)
            val r = SmbHelper.downloadFile(host, share, user, pass, f.path, dest, domain)
            if (!r.success) {
                toast(r.message); return@launch
            }
            val uri = try {
                FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".fileprovider", dest)
            } catch (_: Exception) {
                Uri.fromFile(dest)
            }
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            try {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Abrir con"
                    )
                )
            } catch (e: ActivityNotFoundException) {
                toast("No hay app para este tipo (instala WPS / visor PDF)")
            }
        }
    }

    private fun copyFile(f: FileEntry) {
        toast("Copiando…")
        viewLifecycleOwner.lifecycleScope.launch {
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: requireContext().filesDir
            val dest = File(dir, f.name)
            val r = SmbHelper.downloadFile(host, share, user, pass, f.path, dest, domain)
            toast(if (r.success) "Copiado: ${dest.absolutePath}" else r.message)
        }
    }

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
