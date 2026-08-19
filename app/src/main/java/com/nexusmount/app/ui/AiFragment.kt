package com.nexusmount.app.ui

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.nexusmount.app.ai.CommandAI
import com.nexusmount.app.backup.BackupManager
import com.nexusmount.app.zip.ZipUtils
import java.io.File

class AiFragment : Fragment() {

    private lateinit var logView: TextView
    private var cwd: File = Environment.getExternalStorageDirectory()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF0B1326.toInt())
        }
        val title = TextView(ctx).apply {
            text = "Nexus Command AI"
            textSize = 20f
            setTextColor(0xFFDAE2FD.toInt())
        }
        val sub = TextView(ctx).apply {
            text = "IA local · comandos en español"
            setTextColor(0xFFC2C6D6.toInt())
            textSize = 13f
        }
        val input = EditText(ctx).apply {
            hint = "Ej: busca pdf, crear carpeta Fotos, backup…"
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
        }
        val btn = MaterialButton(ctx).apply { text = "Ejecutar" }
        logView = TextView(ctx).apply {
            setTextColor(0xFFADC6FF.toInt())
            textSize = 13f
            setPadding(0, 24, 0, 0)
            text = CommandAI.helpText()
        }
        val scroll = ScrollView(ctx).apply { addView(logView) }

        btn.setOnClickListener {
            val cmd = input.text.toString()
            append("> $cmd")
            handle(cmd)
            input.text.clear()
        }

        root.addView(title)
        root.addView(sub)
        root.addView(input)
        root.addView(btn)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return root
    }

    private fun handle(cmd: String) {
        when (val action = CommandAI.parse(cmd)) {
            is CommandAI.Action.Help -> append(action.text)
            is CommandAI.Action.ListDir -> append(CommandAI.executeList(cwd))
            is CommandAI.Action.Search -> {
                val hits = com.nexusmount.app.search.FileSearch.search(
                    cwd,
                    com.nexusmount.app.search.SearchQuery(nameContains = action.query),
                    30
                )
                append(if (hits.isEmpty()) "Sin resultados" else hits.joinToString("\n") { it.path })
            }
            is CommandAI.Action.Mkdir -> {
                val d = File(cwd, action.name)
                append(if (d.mkdir()) "Carpeta creada: ${d.name}" else "Error al crear")
            }
            is CommandAI.Action.Delete -> {
                val f = File(cwd, action.name)
                append(if (f.exists() && f.deleteRecursively()) "Eliminado: ${action.name}" else "No encontrado")
            }
            is CommandAI.Action.Zip -> {
                val f = File(cwd, action.name)
                if (!f.exists()) { append("No existe: ${action.name}"); return }
                val dest = File(cwd, f.nameWithoutExtension + ".zip")
                val r = ZipUtils.zip(listOf(f), dest)
                append(if (r.isSuccess) "ZIP: ${dest.name}" else "Error: ${r.exceptionOrNull()?.message}")
            }
            is CommandAI.Action.Unzip -> {
                val f = File(cwd, action.name)
                if (!f.exists()) { append("No existe"); return }
                val dest = File(cwd, f.nameWithoutExtension)
                val r = ZipUtils.unzip(f, dest)
                append(if (r.isSuccess) "Extraído en ${dest.name}" else "Error")
            }
            is CommandAI.Action.Backup -> {
                val bm = BackupManager(requireContext())
                val r = bm.runBackup(listOf(cwd), "ai")
                append(
                    r.fold(
                        onSuccess = { "Backup OK: ${it.name} (${it.size} bytes)" },
                        onFailure = { "Error backup: ${it.message}" }
                    )
                )
            }
            is CommandAI.Action.Unknown -> append(action.text)
        }
    }

    private fun append(msg: String) {
        logView.text = logView.text.toString() + "\n\n" + msg
    }
}
