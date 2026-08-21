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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.R
import com.nexusmount.app.data.SmbHelper
import com.nexusmount.app.databinding.FragmentListBinding
import kotlinx.coroutines.launch

/**
 * Interconexión por IP local o Tailscale:
 * solo visualizar y copiar. No eliminar ni modificar.
 * Para edición completa usar SMB en Mis Unidades.
 */
class InterconnectFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.titleText.text = "Interconexión (visor)"
        binding.subtitleText.text = "Solo lectura · ver y copiar · IP local o Tailscale"
        binding.primaryAction.text = "Conectar a dispositivo"
        binding.primaryAction.setOnClickListener { connectDialog() }
        showHome()
    }

    private fun showHome() {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            listOf(
                "Qué es" to "Introduce la IP de un PC/tablet/NAS que comparta carpetas por SMB. Solo podrás ver y copiar.",
                "No permite" to "Eliminar, renombrar ni editar en remoto (para eso: Mis Unidades + SMB completo).",
                "Tailscale" to "Usa la IP 100.x del otro dispositivo (app Tailscale → Machines).",
                "LAN" to "Usa 192.168.x.x si estáis en la misma Wi‑Fi.",
                "Conectar a dispositivo" to "Abrir formulario",
                "Ir a Mis Unidades (SMB completo)" to "Edición y borrado si el share lo permite"
            )
        ) { pos ->
            when (pos) {
                4 -> connectDialog()
                5 -> findNavController().navigate(R.id.drivesFragment)
            }
        }
    }

    private fun connectDialog() {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        fun field(h: String) = EditText(requireContext()).apply {
            hint = h
            setTextColor(0xFFDAE2FD.toInt())
            setHintTextColor(0xFF8C909F.toInt())
            box.addView(this)
        }
        val host = field("IP (100.x o 192.168.x)")
        val user = field("Usuario (si hace falta)")
        val pass = field("Contraseña").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val share = field("Nombre del share (ej. share, public, Users)")
        AlertDialog.Builder(requireContext())
            .setTitle("Interconexión solo lectura")
            .setView(box)
            .setPositiveButton("Abrir visor") { _, _ ->
                val h = host.text.toString().trim()
                val s = share.text.toString().trim()
                if (h.isEmpty() || s.isEmpty()) {
                    Toast.makeText(requireContext(), "IP y share obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(requireContext(), "Conectando en modo visor…", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val r = SmbHelper.testConnection(
                        h, s, user.text.toString(), pass.text.toString(), ""
                    )
                    if (!r.success) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("No se pudo conectar")
                            .setMessage(r.message)
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }
                    val args = SmbBrowserFragment.args(
                        h, s, user.text.toString(), pass.text.toString(), ""
                    ).apply { putBoolean("readOnly", true) }
                    findNavController().navigate(R.id.smbBrowserFragment, args)
                }
            }
            .setNeutralButton("Listar shares") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) return@setNeutralButton
                viewLifecycleOwner.lifecycleScope.launch {
                    val r = SmbHelper.listShares(h, user.text.toString(), pass.text.toString(), "")
                    if (r.success && r.shares.isNotEmpty()) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Shares en $h")
                            .setItems(r.shares.toTypedArray()) { _, i ->
                                share.setText(r.shares[i])
                            }
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        Toast.makeText(requireContext(), r.message, Toast.LENGTH_LONG).show()
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
