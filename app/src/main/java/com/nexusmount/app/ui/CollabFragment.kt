package com.nexusmount.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.data.DriveType
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.util.TailscaleUtil
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Trabajo colaborativo: espacios, miembros, recursos compartidos, invitaciones.
 * Persistencia local; enlace real vía Tailscale + SMB del equipo.
 */
class CollabFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private fun prefs() = requireContext().getSharedPreferences("nexus_collab", 0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Trabajo colaborativo"
        binding.subtitleText.text = "Espacios · miembros · recursos · invitaciones"
        binding.primaryAction.text = "+ Nuevo espacio"
        binding.primaryAction.setOnClickListener { createSpace() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun loadSpaces(): JSONArray {
        return try {
            JSONArray(prefs().getString("spaces", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun saveSpaces(arr: JSONArray) {
        prefs().edit().putString("spaces", arr.toString()).apply()
    }

    private fun refresh() {
        val arr = loadSpaces()
        val items = mutableListOf<Pair<String, String>>()
        items.add("── Resumen ──" to "")
        items.add("Espacios" to "${arr.length()} activos")
        val ips = TailscaleUtil.getTailscaleIpv4()
        items.add(
            "Tu IP Tailscale" to if (ips.isEmpty()) "No detectada (conecta Tailscale)" else ips.first()
        )
        val drives = try {
            (requireActivity().application as NexusApp).repository.getDrives()
                .filter { it.type == DriveType.SMB }
        } catch (_: Exception) {
            emptyList()
        }
        items.add("Unidades SMB para compartir" to "${drives.size} disponibles")

        items.add("── Espacios ──" to "")
        if (arr.length() == 0) {
            items.add("Sin espacios aún" to "Pulsa «+ Nuevo espacio» para empezar")
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "Espacio")
                val role = o.optString("role", "owner")
                val members = o.optJSONArray("members")?.length() ?: 1
                val resources = o.optJSONArray("resources")?.length() ?: 0
                val code = o.optString("invite", "")
                items.add(
                    "👥 $name" to "Rol: $role · $members miembro(s) · $resources recurso(s) · código: $code"
                )
            }
        }

        items.add("── Agenda y reuniones ──" to "")
        val meetings = loadMeetings()
        if (meetings.length() == 0) {
            items.add("Sin reuniones" to "Toca «Nueva reunión» abajo")
        } else {
            for (i in 0 until meetings.length()) {
                val m = meetings.getJSONObject(i)
                items.add(
                    "📅 ${m.optString("title")}" to
                        "${m.optString("when")} · ${m.optString("place")}"
                )
            }
        }
        items.add("Nueva reunión / agenda" to "Título, fecha, lugar, notas")

        items.add("── Interconexión ──" to "")
        items.add("Visor por IP (solo lectura)" to "Ver y copiar archivos de otro dispositivo")
        items.add("Abrir documento con apps" to "WPS, PDF, galería… (elige archivo local)")

        items.add("── Herramientas ──" to "")
        items.add("Abrir WPS Office" to "Documentos Office en el dispositivo")
        items.add("Abrir visor PDF" to "Elige app PDF instalada")
        items.add("Agenda / notas del equipo" to "Notas rápidas compartidas en el espacio")
        items.add("Calendario" to "Abrir calendario del sistema")
        items.add("Reuniones" to "Registrar reunión (fecha, enlace, notas)")
        items.add("Interconexión solo lectura" to "Ver archivos por IP sin modificar")
        items.add("── Acciones rápidas ──" to "")
        items.add("Unirse con código" to "Introduce un código de invitación")
        items.add("Cómo funciona" to "Tailscale + SMB: misma red mesh, carpetas del PC/NAS")

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(items) { pos ->
            val label = items[pos].first
            when {
                label.startsWith("──") -> {}
                label == "Sin espacios aún" -> createSpace()
                label.startsWith("👥") -> {
                    val name = label.removePrefix("👥 ").trim()
                    openSpace(name)
                }
                label == "Abrir WPS Office" -> openApp(listOf(
                    "cn.wps.moffice_eng", "cn.wps.moffice", "com.mobisystems.office"
                ), "WPS / Office")
                label == "Abrir visor PDF" -> openPdfPicker()
                label == "Agenda / notas del equipo" -> openAgenda()
                label == "Calendario" -> openCalendar()
                label == "Reuniones" -> openMeetings()
                label == "Interconexión solo lectura" ->
                    findNavController().navigate(com.nexusmount.app.R.id.interconnectFragment)
                label == "Unirse con código" -> joinWithCode()
                label == "Cómo funciona" -> showHelp()
                label == "Nueva reunión / agenda" -> addMeeting()
                label == "Sin reuniones" -> addMeeting()
                label.startsWith("📅") -> showMeeting(label.removePrefix("📅 ").trim())
                label == "Visor por IP (solo lectura)" -> {
                    try { findNavController().navigate(R.id.interconnectFragment) } catch (e: Exception) { toast("${e.message}") }
                }
                label == "Abrir documento con apps" -> openLocalWithApps()
                label == "Tu IP Tailscale" -> {
                    val text = TailscaleUtil.statusSummary(requireContext())
                    AlertDialog.Builder(requireContext())
                        .setTitle("Tailscale")
                        .setMessage(text)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun createSpace() {
        val nameIn = EditText(requireContext()).apply { hint = "Nombre del espacio (ej. Proyecto Casa)" }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
            addView(nameIn)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Nuevo espacio colaborativo")
            .setView(box)
            .setPositiveButton("Crear") { _, _ ->
                val name = nameIn.text.toString().trim()
                if (name.isEmpty()) {
                    toast("Nombre obligatorio")
                    return@setPositiveButton
                }
                val arr = loadSpaces()
                val invite = UUID.randomUUID().toString().take(8).uppercase()
                val obj = JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", name)
                    .put("role", "owner")
                    .put("invite", invite)
                    .put("members", JSONArray().put(JSONObject().put("name", "Tú").put("role", "owner")))
                    .put("resources", JSONArray())
                    .put("notes", "")
                    .put("created", System.currentTimeMillis())
                arr.put(obj)
                saveSpaces(arr)
                toast("Espacio creado · código $invite")
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openSpace(name: String) {
        val arr = loadSpaces()
        var idx = -1
        var obj: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("name") == name) {
                idx = i
                obj = o
                break
            }
        }
        if (obj == null) return
        val o = obj
        val invite = o.optString("invite")
        val members = o.optJSONArray("members") ?: JSONArray()
        val resources = o.optJSONArray("resources") ?: JSONArray()
        val memberLines = (0 until members.length()).joinToString("\n") {
            val m = members.getJSONObject(it)
            "• ${m.optString("name")} (${m.optString("role")})"
        }
        val resLines = if (resources.length() == 0) "(ninguno aún)"
        else (0 until resources.length()).joinToString("\n") {
            val r = resources.getJSONObject(it)
            "• ${r.optString("label")} → ${r.optString("path")}"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(
                "Código invitación: $invite\n\n" +
                    "Miembros:\n$memberLines\n\n" +
                    "Recursos compartidos:\n$resLines"
            )
            .setPositiveButton("Añadir recurso SMB") { _, _ -> addResource(idx) }
            .setNeutralButton("Copiar invitación") { _, _ ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val ips = TailscaleUtil.getTailscaleIpv4().firstOrNull() ?: "(sin IP Tailscale)"
                val text =
                    "NexusMount espacio «$name»\nCódigo: $invite\n" +
                        "Conéctate a Tailscale (misma cuenta) y usa este código en Colaborativo.\n" +
                        "IP de referencia: $ips"
                cm.setPrimaryClip(ClipData.newPlainText("invite", text))
                toast("Invitación copiada")
            }
            .setNegativeButton("Más…") { _, _ -> spaceMoreMenu(idx, name) }
            .show()
    }

    private fun spaceMoreMenu(idx: Int, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setItems(
                arrayOf(
                    "Añadir miembro (nombre)",
                    "Notas del espacio",
                    "Eliminar espacio"
                )
            ) { _, which ->
                when (which) {
                    0 -> addMember(idx)
                    1 -> editNotes(idx)
                    2 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("¿Eliminar $name?")
                            .setPositiveButton("Eliminar") { _, _ ->
                                val arr = loadSpaces()
                                val next = JSONArray()
                                for (i in 0 until arr.length()) if (i != idx) next.put(arr.get(i))
                                saveSpaces(next)
                                refresh()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun addResource(idx: Int) {
        val drives = try {
            (requireActivity().application as NexusApp).repository.getDrives()
                .filter { it.type == DriveType.SMB }
        } catch (_: Exception) {
            emptyList()
        }
        if (drives.isEmpty()) {
            toast("Primero conecta una unidad SMB en Mis Unidades")
            return
        }
        val labels = drives.map { "${it.name} (${it.path})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Vincular recurso al espacio")
            .setItems(labels) { _, which ->
                val d = drives[which]
                val arr = loadSpaces()
                val o = arr.getJSONObject(idx)
                val res = o.optJSONArray("resources") ?: JSONArray()
                res.put(
                    JSONObject()
                        .put("label", d.name)
                        .put("path", d.path)
                        .put("id", d.id)
                )
                o.put("resources", res)
                arr.put(idx, o)
                // rewrite array cleanly
                val next = JSONArray()
                for (i in 0 until arr.length()) {
                    next.put(if (i == idx) o else arr.getJSONObject(i))
                }
                saveSpaces(next)
                toast("Recurso añadido al espacio")
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addMember(idx: Int) {
        val input = EditText(requireContext()).apply { hint = "Nombre del miembro" }
        AlertDialog.Builder(requireContext())
            .setTitle("Añadir miembro")
            .setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val arr = loadSpaces()
                val o = arr.getJSONObject(idx)
                val members = o.optJSONArray("members") ?: JSONArray()
                members.put(JSONObject().put("name", name).put("role", "editor"))
                o.put("members", members)
                val next = JSONArray()
                for (i in 0 until arr.length()) next.put(if (i == idx) o else arr.getJSONObject(i))
                saveSpaces(next)
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editNotes(idx: Int) {
        val arr = loadSpaces()
        val o = arr.getJSONObject(idx)
        val input = EditText(requireContext()).apply {
            setText(o.optString("notes"))
            hint = "Notas / instrucciones para el equipo"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Notas")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                o.put("notes", input.text.toString())
                val next = JSONArray()
                for (i in 0 until arr.length()) next.put(if (i == idx) o else arr.getJSONObject(i))
                saveSpaces(next)
                toast("Notas guardadas")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun joinWithCode() {
        val input = EditText(requireContext()).apply { hint = "Código de 8 caracteres" }
        AlertDialog.Builder(requireContext())
            .setTitle("Unirse a un espacio")
            .setMessage(
                "En esta versión el código se registra localmente. " +
                    "Para compartir archivos de verdad: misma cuenta Tailscale + la unidad SMB del anfitrión."
            )
            .setView(input)
            .setPositiveButton("Unirse") { _, _ ->
                val code = input.text.toString().trim().uppercase()
                if (code.length < 4) {
                    toast("Código inválido")
                    return@setPositiveButton
                }
                val arr = loadSpaces()
                val obj = JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", "Espacio $code")
                    .put("role", "editor")
                    .put("invite", code)
                    .put("members", JSONArray().put(JSONObject().put("name", "Tú").put("role", "editor")))
                    .put("resources", JSONArray())
                    .put("notes", "Unido con código $code")
                    .put("created", System.currentTimeMillis())
                arr.put(obj)
                saveSpaces(arr)
                toast("Te has unido (registro local)")
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cómo colaborar")
            .setMessage(
                "1. Todos en la misma cuenta Tailscale (Connected).\n" +
                    "2. El anfitrión comparte carpetas por Samba en el PC/NAS.\n" +
                    "3. Crea un espacio y copia la invitación (código + IP).\n" +
                    "4. Vincula unidades SMB al espacio (Añadir recurso).\n" +
                    "5. Los demás: Mis Unidades → conectar al mismo //IP/share.\n\n" +
                    "Los espacios organizan el equipo; el acceso real a archivos es Tailscale + SMB."
            )
            .setPositiveButton("OK", null)
            .show()
    }


    private fun loadMeetings(): JSONArray {
        return try { JSONArray(prefs().getString("meetings", "[]")) } catch (_: Exception) { JSONArray() }
    }

    private fun saveMeetings(arr: JSONArray) {
        prefs().edit().putString("meetings", arr.toString()).apply()
    }

    private fun addMeeting() {
        val title = EditText(requireContext()).apply { hint = "Título" }
        val whenEt = EditText(requireContext()).apply { hint = "Cuándo (ej. 2026-08-22 18:00)" }
        val place = EditText(requireContext()).apply { hint = "Lugar o enlace" }
        val notes = EditText(requireContext()).apply { hint = "Notas" }
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
            addView(title); addView(whenEt); addView(place); addView(notes)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Nueva reunión / agenda")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val arr = loadMeetings()
                arr.put(
                    JSONObject()
                        .put("title", title.text.toString().ifBlank { "Reunión" })
                        .put("when", whenEt.text.toString())
                        .put("place", place.text.toString())
                        .put("notes", notes.text.toString())
                )
                saveMeetings(arr)
                toast("Guardado")
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showMeeting(title: String) {
        val arr = loadMeetings()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            if (m.optString("title") == title) {
                AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage("Cuándo: ${m.optString("when")}\nLugar: ${m.optString("place")}\n\n${m.optString("notes")}")
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Eliminar") { _, _ ->
                        val next = JSONArray()
                        for (j in 0 until arr.length()) if (j != i) next.put(arr.get(j))
                        saveMeetings(next)
                        refresh()
                    }
                    .show()
                return
            }
        }
    }

    private fun openLocalWithApps() {
        val root = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        val files = root.walkTopDown().maxDepth(3).filter { it.isFile }.take(40).toList()
        if (files.isEmpty()) {
            toast("No hay archivos en la carpeta de la app. Copia alguno antes.")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Abrir con WPS / PDF /…")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val f = files[which]
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(), requireContext().packageName + ".fileprovider", f
                    )
                    val ext = f.extension.lowercase()
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: "application/octet-stream"
                    startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Abrir ${f.name}"
                        )
                    )
                } catch (e: Exception) {
                    toast("Instala WPS Office o un visor PDF: ${e.message}")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }



    private fun openApp(packages: List<String>, label: String) {
        for (pkg in packages) {
            val launch = requireContext().packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch)
                return
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(label)
            .setMessage("No se encontró $label instalado. Instálalo desde Play Store para abrir documentos Office.")
            .setPositiveButton("Play Store") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=WPS Office")))
                } catch (_: Exception) {}
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "application/pdf"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(intent, "Abrir PDF con"))
        } catch (_: Exception) {
            toast("Instala un lector PDF (Google PDF, Adobe, WPS…)")
        }
    }

    private fun openCalendar() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/")))
            } catch (_: Exception) {
                toast("No hay calendario disponible")
            }
        }
    }

    private fun openAgenda() {
        val p = prefs()
        val input = EditText(requireContext()).apply {
            setText(p.getString("team_agenda", ""))
            hint = "Notas de agenda del equipo"
            minLines = 4
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Agenda / notas")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                p.edit().putString("team_agenda", input.text.toString()).apply()
                toast("Agenda guardada")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun openMeetings() {
        val p = prefs()
        val arr = try { org.json.JSONArray(p.getString("meetings", "[]")) } catch (_: Exception) { org.json.JSONArray() }
        val lines = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            lines.add("${o.optString("when")} · ${o.optString("title")} · ${o.optString("link")}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reuniones")
            .setMessage(if (lines.isEmpty()) "Sin reuniones aún" else lines.joinToString("\n\n"))
            .setPositiveButton("Nueva") { _, _ ->
                val title = EditText(requireContext()).apply { hint = "Título" }
                val whenE = EditText(requireContext()).apply { hint = "Fecha/hora (ej. 2026-08-22 18:00)" }
                val link = EditText(requireContext()).apply { hint = "Enlace Meet/Teams/Zoom" }
                val notes = EditText(requireContext()).apply { hint = "Notas" }
                val box = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 16, 40, 8)
                    addView(title); addView(whenE); addView(link); addView(notes)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Nueva reunión")
                    .setView(box)
                    .setPositiveButton("Guardar") { _, _ ->
                        arr.put(org.json.JSONObject()
                            .put("title", title.text.toString())
                            .put("when", whenE.text.toString())
                            .put("link", link.text.toString())
                            .put("notes", notes.text.toString()))
                        p.edit().putString("meetings", arr.toString()).apply()
                        toast("Reunión guardada")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
