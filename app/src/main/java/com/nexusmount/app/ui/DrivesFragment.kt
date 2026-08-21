package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.NexusApp
import com.nexusmount.app.R
import com.nexusmount.app.data.DriveStatus
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.launch

class DrivesFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SimpleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Mis Unidades"
        binding.subtitleText.text = "SMB robusto · Diagnóstico · Listar shares"
        binding.primaryAction.text = "Añadir / listar Samba"
        binding.primaryAction.setOnClickListener { showAddSmbDialog() }

        adapter = SimpleAdapter(emptyList()) { pos ->
            val drives = (requireActivity().application as NexusApp).repository.getDrives()
            if (pos in drives.indices) {
                val d = drives[pos]
                AlertDialog.Builder(requireContext())
                    .setTitle(d.name)
                    .setMessage("${d.type}\n${d.path}\n${d.status}")
                    .setPositiveButton("Cerrar", null)
                    .setNegativeButton("Eliminar") { _, _ ->
                        (requireActivity().application as NexusApp).repository.removeDrive(d.id)
                        refresh()
                    }
                    .show()
            }
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
        val host = field("IP Tailscale o local (ej: 100.x.x.x)")
        val user = field("Usuario Windows/NAS")
        val pass = field("Contraseña").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val domain = field("Dominio (déjalo vacío si no usas dominio)")
        val shareManual = field("Share manual (ej: share) si no lista")

        AlertDialog.Builder(ctx)
            .setTitle("Samba / SMB")
            .setView(layout)
            .setPositiveButton("Listar recursos") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) {
                    Toast.makeText(ctx, "IP obligatoria", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(ctx, "Conectando a $h…", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = SmbHelper.listShares(
                        h,
                        user.text.toString(),
                        pass.text.toString(),
                        domain.text.toString()
                    )
                    if (!result.success || result.shares.isEmpty()) {
                        showError("No se listaron shares", result.message)
                        val sm = shareManual.text.toString().trim()
                        if (sm.isNotEmpty()) {
                            connectAndSave(h, sm, user.text.toString(), pass.text.toString(), domain.text.toString())
                        }
                    } else {
                        showSharePicker(
                            h,
                            user.text.toString(),
                            pass.text.toString(),
                            domain.text.toString(),
                            result.shares
                        )
                    }
                }
            }
            .setNeutralButton("Diagnóstico") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) {
                    Toast.makeText(ctx, "Pon la IP primero", Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val r = SmbHelper.diagnose(h)
                    showError("Diagnóstico", r.message)
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

    private fun showSharePicker(
        host: String,
        user: String,
        pass: String,
        domain: String,
        shares: List<String>
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("Recursos en $host")
            .setItems(shares.toTypedArray()) { _, which ->
                connectAndSave(host, shares[which], user, pass, domain)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun connectAndSave(
        host: String,
        share: String,
        user: String,
        pass: String,
        domain: String
    ) {
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
            } else {
                showError("Error de conexión", result.message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
