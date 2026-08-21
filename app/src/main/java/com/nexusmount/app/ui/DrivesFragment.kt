package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.data.DriveItem
import com.nexusmount.app.data.DriveStatus
import com.nexusmount.app.data.DriveType
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DrivesFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SimpleAdapter
    private var listJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private var drivesCache: List<DriveItem> = emptyList()

    private val repo get() = (requireActivity().application as NexusApp).repository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "Mis Unidades"
        binding.primaryAction.text = "+ Añadir otra carpeta Samba"
        binding.primaryAction.setOnClickListener { showAddSmbDialog() }

        adapter = SimpleAdapter(emptyList()) { pos ->
            if (pos !in drivesCache.indices) return@SimpleAdapter
            showDriveInfo(drivesCache[pos])
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        drivesCache = repo.getDrives()
        val smb = drivesCache.count { it.type == DriveType.SMB }
        val local = drivesCache.count { it.type == DriveType.LOCAL }
        binding.subtitleText.text =
            "Vista general: $smb Samba · $local local · ${drivesCache.size} en total\n" +
                "Toca una para abrir · ⋮ Más para actualizar/eliminar"

        // Orden: SMB primero, luego local
        val ordered = drivesCache.sortedWith(
            compareBy<DriveItem> { if (it.type == DriveType.SMB) 0 else 1 }
                .thenBy { it.name.lowercase() }
        )
        drivesCache = ordered

        adapter.submit(ordered.map { d ->
            val tag = when (d.type) {
                DriveType.SMB -> "📁 SMB"
                DriveType.LOCAL -> "📱 Local"
                else -> d.type.name
            }
            "$tag  ${d.name}" to "${d.status} · ${d.path}"
        })
        binding.emptyText.visibility = if (ordered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = "No hay unidades.\nPulsa «+ Añadir otra carpeta Samba»."
    }

    private fun showDriveInfo(d: DriveItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(d.name)
            .setMessage("${d.type}\n${d.path}\n${d.status}")
            .setPositiveButton("Abrir / explorar") { _, _ -> openDrive(d) }
            .setNeutralButton("⋮ Más") { _, _ -> showHiddenMenu(d) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showHiddenMenu(d: DriveItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Opciones · ${d.name}")
            .setItems(arrayOf("Actualizar conexión", "Eliminar de la lista")) { _, which ->
                when (which) {
                    0 -> {
                        val m = Regex("""^//([^/]+)/(.+)$""").find(d.path)
                        if (m == null) {
                            toast("Solo para unidades SMB")
                            return@setItems
                        }
                        val host = m.groupValues[1]
                        val share = m.groupValues[2]
                        val prefs = requireContext().getSharedPreferences("smb_creds", 0)
                        val user = prefs.getString("${d.id}_user", "") ?: ""
                        val pass = prefs.getString("${d.id}_pass", "") ?: ""
                        val domain = prefs.getString("${d.id}_domain", "") ?: ""
                        toast("Comprobando…")
                        viewLifecycleOwner.lifecycleScope.launch {
                            val r = SmbHelper.testConnection(host, share, user, pass, domain)
                            toast(r.message)
                            if (r.success) {
                                repo.updateDrive(d.id) { it.copy(status = DriveStatus.ONLINE) }
                                refresh()
                            }
                        }
                    }
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("¿Quitar ${d.name}?")
                            .setMessage("Solo se elimina de la lista del teléfono, no del servidor.")
                            .setPositiveButton("Eliminar") { _, _ ->
                                repo.removeDrive(d.id)
                                requireContext().getSharedPreferences("smb_creds", 0).edit()
                                    .remove("${d.id}_user").remove("${d.id}_pass")
                                    .remove("${d.id}_domain").remove("${d.id}_host").remove("${d.id}_share")
                                    .apply()
                                refresh()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun openDrive(d: DriveItem) {
        val m = Regex("""^//([^/]+)/(.+)$""").find(d.path)
        if (m == null) {
            findNavController().navigate(R.id.filesFragment)
            return
        }
        val host = m.groupValues[1]
        val share = m.groupValues[2]
        val prefs = requireContext().getSharedPreferences("smb_creds", 0)
        val user = prefs.getString("${d.id}_user", "") ?: ""
        val pass = prefs.getString("${d.id}_pass", "") ?: ""
        val domain = prefs.getString("${d.id}_domain", "") ?: ""
        findNavController().navigate(
            R.id.smbBrowserFragment,
            SmbBrowserFragment.args(host, share, user, pass, domain)
        )
    }

    private fun showAddSmbDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        fun field(hint: String): EditText {
            return EditText(ctx).apply {
                this.hint = hint
                setTextColor(0xFFDAE2FD.toInt())
                setHintTextColor(0xFF8C909F.toInt())
                layout.addView(this)
            }
        }
        val host = field("IP Tailscale o local")
        val user = field("Usuario")
        val pass = field("Contraseña").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val domain = field("Dominio (opcional)")
        val status = EditText(ctx).apply {
            setText("Puedes añadir varias carpetas. Cada share = una entrada en la lista.")
            isEnabled = false
            setTextColor(0xFF8C909F.toInt())
            layout.addView(this)
        }
        val shareManual = field("Share manual (si no lista)")

        var debounce: Runnable? = null
        host.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounce?.let { handler.removeCallbacks(it) }
                val h = s?.toString()?.trim() ?: return
                if (h.length < 7) return
                val r = Runnable {
                    status.setText("Buscando shares en $h…")
                    listJob?.cancel()
                    listJob = viewLifecycleOwner.lifecycleScope.launch {
                        val result = SmbHelper.listShares(
                            h, user.text.toString(), pass.text.toString(), domain.text.toString()
                        )
                        if (!isAdded) return@launch
                        if (result.success && result.shares.isNotEmpty()) {
                            status.setText("Encontrados: ${result.shares.joinToString()}")
                            showSharePicker(
                                h,
                                user.text.toString(),
                                pass.text.toString(),
                                domain.text.toString(),
                                result.shares
                            )
                        } else {
                            status.setText(result.message.take(140))
                        }
                    }
                }
                debounce = r
                handler.postDelayed(r, 1200)
            }
        })

        AlertDialog.Builder(ctx)
            .setTitle("Añadir carpeta Samba")
            .setView(layout)
            .setPositiveButton("Listar shares") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) {
                    toast("IP obligatoria")
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = SmbHelper.listShares(
                        h, user.text.toString(), pass.text.toString(), domain.text.toString()
                    )
                    if (result.success && result.shares.isNotEmpty()) {
                        showSharePicker(
                            h, user.text.toString(), pass.text.toString(),
                            domain.text.toString(), result.shares
                        )
                    } else {
                        showError("Listado", result.message)
                        val sm = shareManual.text.toString().trim()
                        if (sm.isNotEmpty()) {
                            connectAndSave(h, sm, user.text.toString(), pass.text.toString(), domain.text.toString())
                        }
                    }
                }
            }
            .setNeutralButton("Diagnóstico") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) return@setNeutralButton
                viewLifecycleOwner.lifecycleScope.launch {
                    showError("Diagnóstico", SmbHelper.diagnose(h).message)
                }
            }
            .setNegativeButton("Conectar share") { _, _ ->
                val h = host.text.toString().trim()
                val s = shareManual.text.toString().trim()
                if (h.isEmpty() || s.isEmpty()) {
                    toast("IP y share obligatorios")
                    return@setNegativeButton
                }
                connectAndSave(h, s, user.text.toString(), pass.text.toString(), domain.text.toString())
            }
            .show()
    }

    private fun showError(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSharePicker(host: String, user: String, pass: String, domain: String, shares: List<String>) {
        // Multi-select style: pick one, then offer add another
        AlertDialog.Builder(requireContext())
            .setTitle("Elige carpeta compartida ($host)")
            .setItems(shares.toTypedArray()) { _, which ->
                connectAndSave(host, shares[which], user, pass, domain)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun connectAndSave(host: String, share: String, user: String, pass: String, domain: String) {
        toast("Conectando //$host/$share …")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SmbHelper.testConnection(host, share, user, pass, domain)
            if (!result.success) {
                showError("Error de conexión", result.message)
                return@launch
            }
            val drive = SmbHelper.createDriveFromSmb("SMB $host/$share", host, share)
                .copy(status = DriveStatus.ONLINE)
            val added = repo.addDrive(drive)
            if (!added) {
                toast("Esa carpeta ya estaba en la lista")
                refresh()
            } else {
                requireContext().getSharedPreferences("smb_creds", 0).edit()
                    .putString("${drive.id}_user", user)
                    .putString("${drive.id}_pass", pass)
                    .putString("${drive.id}_domain", domain)
                    .putString("${drive.id}_host", host)
                    .putString("${drive.id}_share", share)
                    .apply()
                toast("Añadida: //$host/$share")
                refresh()
            }
            // Preguntar: explorar o añadir más (no fuerza salir de la lista)
            AlertDialog.Builder(requireContext())
                .setTitle("Carpeta conectada")
                .setMessage("//$host/$share\n\n¿Qué quieres hacer?")
                .setPositiveButton("Explorar ahora") { _, _ ->
                    findNavController().navigate(
                        R.id.smbBrowserFragment,
                        SmbBrowserFragment.args(host, share, user, pass, domain)
                    )
                }
                .setNeutralButton("Añadir otra") { _, _ ->
                    showAddSmbDialog()
                }
                .setNegativeButton("Ver lista") { _, _ -> refresh() }
                .show()
        }
    }

    private fun toast(m: String) =
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        listJob?.cancel()
        _binding = null
    }
}
