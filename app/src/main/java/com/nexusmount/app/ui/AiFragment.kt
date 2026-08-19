package com.nexusmount.app.ui

import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.ai.CommandAI
import com.nexusmount.app.backup.BackupManager
import com.nexusmount.app.util.TailscaleUtil
import com.nexusmount.app.zip.ZipUtils
import java.io.File

/**
 * Chat con Rigo — asistente bajo solicitud.
 */
class AiFragment : Fragment() {

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private var cwd: File = Environment.getExternalStorageDirectory()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF0B1326.toInt())
        }

        val title = TextView(ctx).apply {
            text = "Rigo · Asistente NexusMount"
            textSize = 20f
            setTextColor(0xFFDAE2FD.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val sub = TextView(ctx).apply {
            text = "IA local · control bajo solicitud · ES"
            setTextColor(0xFF2DD4BF.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 12)
        }

        val quick = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(label: String, cmd: String) {
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                setOnClickListener { runCommand(cmd) }
                setPadding(16, 0, 16, 0)
            }
            quick.addView(b)
        }
        chip("Hola", "hola")
        chip("Estado", "estado")
        chip("Ayuda", "ayuda")
        chip("Backup", "backup")

        val input = EditText(ctx).apply {
            hint = "Escribe a Rigo… (ej: busca pdf, abrir unidades)"
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
            minLines = 1
            maxLines = 3
        }
        val btn = MaterialButton(ctx).apply { text = "Enviar a Rigo" }
        logView = TextView(ctx).apply {
            setTextColor(0xFFADC6FF.toInt())
            textSize = 14f
            setPadding(8, 16, 8, 16)
            setTextIsSelectable(true)
            text = CommandAI.greeting()
        }
        scroll = ScrollView(ctx).apply {
            addView(logView)
            setBackgroundColor(0xFF131B2E.toInt())
            setPadding(12, 12, 12, 12)
        }

        btn.setOnClickListener {
            val cmd = input.text.toString()
            if (cmd.isBlank()) return@setOnClickListener
            append("\n\nTú: $cmd")
            runCommand(cmd)
            input.text.clear()
        }

        // Cloud toggle note
        val cloudNote = TextView(ctx).apply {
            text = "IA Cloud (Gemini/OpenAI): configúrala en Módulos y requisitos. Por defecto Rigo es 100% local."
            setTextColor(0xFF8C909F.toInt())
            textSize = 11f
            setPadding(0, 8, 0, 0)
        }

        root.addView(title)
        root.addView(sub)
        root.addView(quick)
        root.addView(input)
        root.addView(btn)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(cloudNote)

        // Prefer readable cwd
        val emulated = File("/storage/emulated/0")
        cwd = when {
            emulated.canRead() && emulated.listFiles() != null -> emulated
            else -> requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        }

        return root
    }

    private fun runCommand(cmd: String) {
        when (val action = CommandAI.parse(cmd)) {
            is CommandAI.Action.Help -> append("\n\nRigo: ${action.text}")
            is CommandAI.Action.Chat -> append("\n\nRigo: ${action.text}")
            is CommandAI.Action.Status -> {
                val repo = (requireActivity().application as NexusApp).repository
                val drives = repo.getDrives()
                val ts = TailscaleUtil.statusSummary(requireContext()).lines().take(2).joinToString(" · ")
                append(
                    "\n\nRigo: Estado\n" +
                        "• Unidades: ${drives.size}\n" +
                        "• Transferencias: ${repo.getTransfers().size}\n" +
                        "• Carpeta actual: ${cwd.absolutePath}\n" +
                        "• $ts"
                )
            }
            is CommandAI.Action.OpenSection -> {
                append("\n\nRigo: Abriendo ${action.section}…")
                navigateSection(action.section)
            }
            is CommandAI.Action.ListDir -> append("\n\nRigo:\n${CommandAI.executeList(cwd)}")
            is CommandAI.Action.Search -> {
                val hits = com.nexusmount.app.search.FileSearch.search(
                    cwd,
                    com.nexusmount.app.search.SearchQuery(nameContains = action.query),
                    30
                )
                append(
                    "\n\nRigo: " + if (hits.isEmpty()) "Sin resultados para «${action.query}»"
                    else hits.joinToString("\n") { it.path }
                )
            }
            is CommandAI.Action.Mkdir -> {
                val d = File(cwd, action.name)
                append("\n\nRigo: " + if (d.mkdir()) "Carpeta creada: ${d.name}" else "No pude crear la carpeta")
            }
            is CommandAI.Action.Delete -> {
                val f = File(cwd, action.name)
                append(
                    "\n\nRigo: " + if (f.exists() && f.deleteRecursively()) "Eliminado: ${action.name}"
                    else "No encontrado: ${action.name}"
                )
            }
            is CommandAI.Action.Zip -> {
                val f = File(cwd, action.name)
                if (!f.exists()) {
                    append("\n\nRigo: No existe ${action.name}")
                    return
                }
                val dest = File(cwd, f.nameWithoutExtension + ".zip")
                val r = ZipUtils.zip(listOf(f), dest)
                append("\n\nRigo: " + if (r.isSuccess) "ZIP listo: ${dest.name}" else "Error ZIP")
            }
            is CommandAI.Action.Unzip -> {
                val f = File(cwd, action.name)
                if (!f.exists()) {
                    append("\n\nRigo: No existe")
                    return
                }
                val dest = File(cwd, f.nameWithoutExtension)
                val r = ZipUtils.unzip(f, dest)
                append("\n\nRigo: " + if (r.isSuccess) "Extraído en ${dest.name}" else "Error al extraer")
            }
            is CommandAI.Action.Backup -> {
                val bm = BackupManager(requireContext())
                val r = bm.runBackup(listOf(cwd), "rigo")
                append(
                    "\n\nRigo: " + r.fold(
                        onSuccess = { "Backup OK: ${it.name}" },
                        onFailure = { "Error: ${it.message}" }
                    )
                )
            }
            is CommandAI.Action.Unknown -> append("\n\nRigo: ${action.text}")
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun navigateSection(section: String) {
        val id = when (section) {
            "drives" -> R.id.drivesFragment
            "files" -> R.id.filesFragment
            "security" -> R.id.securityFragment
            "tailscale" -> R.id.tailscaleFragment
            "backup" -> R.id.backupFragment
            "settings" -> R.id.settingsFragment
            "modules" -> R.id.modulesConfigFragment
            "samba" -> R.id.sambaFragment
            "logs" -> R.id.logsFragment
            else -> null
        }
        if (id != null) {
            try {
                findNavController().navigate(id)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No se pudo navegar: $section", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun append(msg: String) {
        logView.text = logView.text.toString() + msg
    }
}
