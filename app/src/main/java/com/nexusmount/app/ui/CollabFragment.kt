package com.nexusmount.app.ui

import android.app.AlertDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
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
                label == "Unirse con código" -> joinWithCode()
                label == "Cómo funciona" -> showHelp()
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

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
