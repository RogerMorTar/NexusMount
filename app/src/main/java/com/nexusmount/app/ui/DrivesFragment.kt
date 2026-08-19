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
        binding.subtitleText.text = "SMB real • Almacenamiento local"
        binding.primaryAction.text = "Añadir Samba / SMB"
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
            it.name to "${it.type} • ${it.status} • ${it.path}"
        })
        binding.emptyText.visibility = if (drives.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = getString(com.nexusmount.app.R.string.no_drives)
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
        val host = field("Servidor / IP (ej: 100.64.0.10 o 192.168.1.50)")
        val share = field("Recurso (share)", "share")
        val user = field("Usuario")
        val pass = field("Contraseña").apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val name = field("Nombre (opcional)")

        AlertDialog.Builder(ctx)
            .setTitle("Conectar Samba/SMB")
            .setView(layout)
            .setPositiveButton("Probar y guardar") { _, _ ->
                val h = host.text.toString().trim()
                val s = share.text.toString().trim()
                val u = user.text.toString().trim()
                val p = pass.text.toString()
                if (h.isEmpty() || s.isEmpty()) {
                    Toast.makeText(ctx, "Host y share obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(ctx, "Conectando…", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = SmbHelper.testConnection(h, s, u, p)
                    if (result.success) {
                        val drive = SmbHelper.createDriveFromSmb(
                            name.text.toString().ifBlank { "SMB $h" }, h, s
                        ).copy(status = DriveStatus.ONLINE)
                        (requireActivity().application as NexusApp).repository.addDrive(drive)
                        // Guardar credenciales de forma simple (mejorar con EncryptedSharedPreferences en producción)
                        ctx.getSharedPreferences("smb_creds", 0).edit()
                            .putString("${drive.id}_user", u)
                            .putString("${drive.id}_pass", p)
                            .apply()
                        Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                        refresh()
                    } else {
                        Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
