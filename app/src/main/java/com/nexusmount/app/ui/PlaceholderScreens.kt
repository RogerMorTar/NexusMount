package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.R
import com.nexusmount.app.databinding.FragmentListBinding

/** Pantallas del diseño original que completan el sidebar. */

class AddConnectionFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Añadir conexión"
        b.subtitleText.text = "Multi-fuente: SMB, cloud, local"
        b.primaryAction.text = "Ir a Mis Unidades (SMB)"
        b.primaryAction.setOnClickListener { findNavController().navigate(R.id.drivesFragment) }
        val items = listOf(
            "Samba / SMB" to "IP Tailscale o LAN → listar shares",
            "Google Drive" to "OAuth (Google Cloud) → Módulos",
            "OneDrive" to "Azure Client ID → Módulos",
            "Amazon S3" to "Access Key + bucket → Módulos",
            "WebDAV" to "URL + usuario → Módulos",
            "USB / local" to "Explorador Pro + permiso todos los archivos",
            "NFS" to "Requiere root o app del sistema (limitado en Android)",
            "Tailscale Network" to "Activa Tailscale y usa IP 100.x en SMB"
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(items) { pos ->
            when (pos) {
                0 -> findNavController().navigate(R.id.drivesFragment)
                1, 2, 3, 4 -> findNavController().navigate(R.id.modulesConfigFragment)
                5 -> findNavController().navigate(R.id.filesFragment)
                7 -> findNavController().navigate(R.id.tailscaleFragment)
                else -> Toast.makeText(requireContext(), items[pos].second, Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class DeviceSyncFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Sync Web ↔ Teléfono"
        b.subtitleText.text = "Estado de dispositivos NexusMount"
        b.primaryAction.text = "Sincronizar ahora"
        val p = requireContext().getSharedPreferences("nexus_sync", 0)
        b.primaryAction.setOnClickListener {
            p.edit().putLong("last_sync", System.currentTimeMillis()).apply()
            Toast.makeText(requireContext(), "Sync local registrada. Con Tailscale + mismo backend se reflejaría en web.", Toast.LENGTH_LONG).show()
            refresh()
        }
        refresh()
    }
    private fun refresh() {
        val p = requireContext().getSharedPreferences("nexus_sync", 0)
        val last = p.getLong("last_sync", 0L)
        val lastStr = if (last == 0L) "Nunca" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Este teléfono" to "Online · última sync: $lastStr",
            "App web / PC" to "Requiere misma red Tailscale o backend",
            "Cómo funciona" to "Unidades y ajustes se guardan en el móvil; la web PWA usa su propio storage salvo backend común",
            "Tailscale" to "Abre sección Tailscale para ver IPs reales"
        )) { pos -> if (pos == 3) findNavController().navigate(R.id.tailscaleFragment) }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class BackupConfigFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private fun p() = requireContext().getSharedPreferences("nexus_backup_cfg", 0)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Configuración de backups"
        b.subtitleText.text = "Programación e integridad"
        b.primaryAction.text = "Guardar / alternar auto"
        b.primaryAction.setOnClickListener {
            val on = !p().getBoolean("auto", true)
            p().edit().putBoolean("auto", on).apply()
            Toast.makeText(requireContext(), if (on) "Auto ON" else "Auto OFF", Toast.LENGTH_SHORT).show()
            refresh()
        }
        refresh()
    }
    private fun refresh() {
        val auto = p().getBoolean("auto", true)
        val freq = p().getString("freq", "daily") ?: "daily"
        val hour = p().getString("hour", "03:00") ?: "03:00"
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Backup automático" to if (auto) "Activado" else "Desactivado",
            "Frecuencia" to freq,
            "Hora" to hour,
            "Verificación de integridad" to "ZIP al completar",
            "Ejecutar / ver instantáneas" to "Ir a Backups",
            "Cambiar frecuencia" to "Tocar para ciclar hourly → daily → weekly"
        )) { pos ->
            when (pos) {
                4 -> findNavController().navigate(R.id.backupFragment)
                5 -> {
                    val next = when (freq) {
                        "hourly" -> "every6h"
                        "every6h" -> "daily"
                        "daily" -> "weekly"
                        else -> "hourly"
                    }
                    p().edit().putString("freq", next).apply()
                    refresh()
                }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class IntrusionFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Alertas de intrusión"
        b.subtitleText.text = "Fuerza bruta · rutas sensibles · IPs"
        b.primaryAction.text = "Ver logs de seguridad"
        b.primaryAction.setOnClickListener { findNavController().navigate(R.id.logsFragment) }
        val mon = com.nexusmount.app.security.SecurityMonitor(requireContext())
        val events = mon.getEvents().filter { it.severity == "warning" || it.severity == "critical" }
        val lines = if (events.isEmpty()) {
            listOf("Sin alertas" to "Los fallos 2FA y rutas sensibles aparecerán aquí", "Simular alerta" to "Ir a Seguridad")
        } else events.map { "${it.severity} ${it.type}" to "${mon.formatTime(it.time)} · ${it.detail}" }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(lines) {
            if (events.isEmpty()) findNavController().navigate(R.id.securityFragment)
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class PermissionsFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Control de acceso (ACL)"
        b.subtitleText.text = "Permisos app + nota NAS"
        b.primaryAction.visibility = View.GONE
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Almacenamiento" to "Explorador → permiso todos los archivos",
            "Red / Internet" to "Concedido (SMB, Tailscale)",
            "ACL en el teléfono" to "Limitado al sandbox de Android",
            "ACL reales (NAS)" to "Configurar en Samba del PC/NAS: read/write por usuario",
            "Roles colaborativos" to "Owner / editor / viewer en sección Colaborativo"
        ))
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class AiAutomationFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private fun p() = requireContext().getSharedPreferences("nexus_modules", 0)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Automatización IA (recetas)"
        b.subtitleText.text = "Disparadores · Rigo"
        b.primaryAction.text = "Nueva receta"
        b.primaryAction.setOnClickListener {
            val input = EditText(requireContext()).apply {
                hint = "Ej: comprimir archivos >30 días en Download"
                setText(p().getString("recipes", ""))
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Receta")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    p().edit().putString("recipes", input.text.toString()).apply()
                    Toast.makeText(requireContext(), "Receta guardada", Toast.LENGTH_SHORT).show()
                    refresh()
                }.setNegativeButton("Cancelar", null).show()
        }
        refresh()
    }
    private fun refresh() {
        val r = p().getString("recipes", null)
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Receta actual" to (r ?: "Ninguna"),
            "Hablar con Rigo" to "Ejecutar acciones bajo solicitud",
            "Ejemplos" to "backup diario · zip carpetas grandes · buscar duplicados"
        )) { pos -> if (pos == 1) findNavController().navigate(R.id.aiFragment) }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class AiPrivacyFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private fun p() = requireContext().getSharedPreferences("nexus_modules", 0)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Privacidad de IA"
        b.subtitleText.text = "PII · local vs cloud"
        b.primaryAction.text = "Alternar anonimización PII"
        b.primaryAction.setOnClickListener {
            val n = !p().getBoolean("pii_anon", true)
            p().edit().putBoolean("pii_anon", n).apply()
            Toast.makeText(requireContext(), if (n) "PII ON" else "PII OFF", Toast.LENGTH_SHORT).show()
            refresh()
        }
        refresh()
    }
    private fun refresh() {
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Rigo (local)" to "No envía datos a la nube",
            "Anonimización PII" to if (p().getBoolean("pii_anon", true)) "Activada" else "Desactivada",
            "IA Cloud" to if (p().getBoolean("ai_cloud", false)) "Clave configurada" else "Desactivada",
            "Auditoría de prompts" to "Logs técnicos cuando uses cloud",
            "Configurar API keys" to "Módulos → IA Cloud"
        )) { pos -> if (pos == 4) findNavController().navigate(R.id.modulesConfigFragment) }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class TerminalFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Terminal / consola"
        b.subtitleText.text = "Comandos vía Rigo"
        b.primaryAction.text = "Abrir Rigo"
        b.primaryAction.setOnClickListener { findNavController().navigate(R.id.aiFragment) }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Shell del sistema" to "No disponible sin root (política Android)",
            "Consola Rigo" to "Lenguaje natural → acciones de la app",
            "Ejemplos" to "lista carpetas · estado · backup · abrir unidades"
        ))
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class RecoveryFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Recuperación de desastres"
        b.subtitleText.text = "Instantáneas históricas"
        b.primaryAction.text = "Ver instantáneas ZIP"
        b.primaryAction.setOnClickListener { findNavController().navigate(R.id.backupFragment) }
        val snaps = com.nexusmount.app.backup.BackupManager(requireContext()).listSnapshots()
        val lines = if (snaps.isEmpty()) listOf("Sin instantáneas" to "Ejecuta un backup primero")
        else snaps.map { it.name to "${it.size / 1024} KB · restaurar desde Backups" }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(lines)
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class LanguageFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private fun p() = requireContext().getSharedPreferences("nexus_lang", 0)
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.titleText.text = "Idioma / Language"
        b.subtitleText.text = "ES · EN"
        b.primaryAction.visibility = View.GONE
        val lang = p().getString("lang", "es") ?: "es"
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = SimpleAdapter(listOf(
            "Español" to if (lang == "es") "Seleccionado" else "Tocar para elegir",
            "English" to if (lang == "en") "Selected" else "Tap to select",
            "Nota" to "La UI base está en castellano; la preferencia se guarda para futuras strings"
        )) { pos ->
            when (pos) {
                0 -> { p().edit().putString("lang", "es").apply(); Toast.makeText(requireContext(), "Español", Toast.LENGTH_SHORT).show() }
                1 -> { p().edit().putString("lang", "en").apply(); Toast.makeText(requireContext(), "English saved", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
