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
import com.nexusmount.app.data.DriveStatus
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Mis Unidades"
        binding.subtitleText.text = "Toca una unidad SMB para explorar · menú ⋮ para borrar"
        binding.primaryAction.text = "Añadir Samba (auto-listar)"
        binding.primaryAction.setOnClickListener { showAddSmbDialog() }

        adapter = SimpleAdapter(emptyList()) { pos ->
            val drives = (requireActivity().application as NexusApp).repository.getDrives()
            if (pos !in drives.indices) return@SimpleAdapter
            val d = drives[pos]
            showDriveInfo(d.id, d.name, d.type.name, d.path, d.status.name)
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        refresh()
    }

    private fun refresh() {
        val drives = (requireActivity().application as NexusApp).repository.getDrives()
        adapter.submit(drives.map {
            it.name to "${it.type} · ${it.status} · ${it.path}"
        })
        binding.emptyText.visibility = if (drives.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = getString(R.string.no_drives)
    }

    /** Ventana de info: Abrir / Cerrar. Eliminar y Actualizar en menú oculto ⋮ */
    private fun showDriveInfo(id: String, name: String, type: String, path: String, status: String) {
        val ctx = requireContext()
        val msg = "$type\n$path\n$status"
        AlertDialog.Builder(ctx)
            .setTitle(name)
            .setMessage(msg)
            .setPositiveButton("Abrir / explorar") { _, _ ->
                openDrive(id, path)
            }
            .setNeutralButton("⋮ Más") { _, _ ->
                showHiddenMenu(id, name, path)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showHiddenMenu(id: String, name: String, path: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Opciones · $name")
            .setItems(arrayOf("Actualizar conexión", "Eliminar unidad")) { _, which ->
                when (which) {
                    0 -> {
                        // Re-test SMB if path looks like //host/share
                        val m = Regex("""^//([^/]+)/(.+)$""").find(path)
                        if (m != null) {
                            val host = m.groupValues[1]
                            val share = m.groupValues[2]
                            val prefs = requireContext().getSharedPreferences("smb_creds", 0)
                            val user = prefs.getString("${id}_user", "") ?: ""
                            val pass = prefs.getString("${id}_pass", "") ?: ""
                            val domain = prefs.getString("${id}_domain", "") ?: ""
                            Toast.makeText(requireContext(), "Comprobando…", Toast.LENGTH_SHORT).show()
                            viewLifecycleOwner.lifecycleScope.launch {
                                val r = SmbHelper.testConnection(host, share, user, pass, domain)
                                Toast.makeText(requireContext(), r.message, Toast.LENGTH_LONG).show()
                                if (r.success) {
                                    (requireActivity().application as NexusApp).repository.updateDrive(id) {
                                        it.copy(status = DriveStatus.ONLINE)
                                    }
                                    refresh()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "Solo para unidades SMB", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("¿Eliminar $name?")
                            .setMessage("Se quitará de la lista (no borra datos del servidor).")
                            .setPositiveButton("Eliminar") { _, _ ->
                                (requireActivity().application as NexusApp).repository.removeDrive(id)
                                requireContext().getSharedPreferences("smb_creds", 0).edit()
                                    .remove("${id}_user").remove("${id}_pass")
                                    .remove("${id}_domain").remove("${id}_host").remove("${id}_share")
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

    private fun openDrive(id: String, path: String) {
        val m = Regex("""^//([^/]+)/(.+)$""").find(path)
        if (m == null) {
            // Local path → files fragment
            findNavController().navigate(R.id.filesFragment)
            return
        }
        val host = m.groupValues[1]
        val share = m.groupValues[2]
        val prefs = requireContext().getSharedPreferences("smb_creds", 0)
        val user = prefs.getString("${id}_user", "") ?: ""
        val pass = prefs.getString("${id}_pass", "") ?: ""
        val domain = prefs.getString("${id}_domain", "") ?: ""
        val args = SmbBrowserFragment.args(host, share, user, pass, domain)
        findNavController().navigate(R.id.smbBrowserFragment, args)
    }

    private fun showAddSmbDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        fun field(hint: String, value: String = ""): EditText {
            return EditText(ctx).apply {
                this.hint = hint
                setText(value)
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
            setText("Escribe la IP: al pausar se listarán los shares solos")
            isEnabled = false
            setTextColor(0xFF8C909F.toInt())
            layout.addView(this)
        }
        val shareManual = field("Share manual (si hace falta)")

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
                            h,
                            user.text.toString(),
                            pass.text.toString(),
                            domain.text.toString()
                        )
                        if (!isAdded) return@launch
                        if (result.success && result.shares.isNotEmpty()) {
                            status.setText("Encontrados: ${result.shares.joinToString()}")
                            // Auto show picker
                            showSharePicker(
                                h,
                                user.text.toString(),
                                pass.text.toString(),
                                domain.text.toString(),
                                result.shares
                            )
                        } else {
                            status.setText(result.message.take(120))
                        }
                    }
                }
                debounce = r
                handler.postDelayed(r, 1200)
            }
        })

        AlertDialog.Builder(ctx)
            .setTitle("Samba / SMB")
            .setView(layout)
            .setPositiveButton("Listar ahora") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) {
                    Toast.makeText(ctx, "IP obligatoria", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = SmbHelper.listShares(
                        h, user.text.toString(), pass.text.toString(), domain.text.toString()
                    )
                    if (result.success && result.shares.isNotEmpty()) {
                        showSharePicker(h, user.text.toString(), pass.text.toString(), domain.text.toString(), result.shares)
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
                    Toast.makeText(ctx, "IP y share obligatorios", Toast.LENGTH_SHORT).show()
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
        AlertDialog.Builder(requireContext())
            .setTitle("Compartidos en $host")
            .setItems(shares.toTypedArray()) { _, which ->
                connectAndSave(host, shares[which], user, pass, domain)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun connectAndSave(host: String, share: String, user: String, pass: String, domain: String) {
        val ctx = requireContext()
        Toast.makeText(ctx, "Conectando //$host/$share …", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SmbHelper.testConnection(host, share, user, pass, domain)
            if (result.success) {
                val drive = SmbHelper.createDriveFromSmb("SMB $host/$share", host, share)
                    .copy(status = DriveStatus.ONLINE)
                (requireActivity().application as NexusApp).repository.addDrive(drive)
                ctx.getSharedPreferences("smb_creds", 0).edit()
                    .putString("${drive.id}_user", user)
                    .putString("${drive.id}_pass", pass)
                    .putString("${drive.id}_domain", domain)
                    .putString("${drive.id}_host", host)
                    .putString("${drive.id}_share", share)
                    .apply()
                Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                refresh()
                // Open browser immediately
                val args = SmbBrowserFragment.args(host, share, user, pass, domain)
                findNavController().navigate(R.id.smbBrowserFragment, args)
            } else {
                showError("Error de conexión", result.message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listJob?.cancel()
        _binding = null
    }
}
