package com.nexusmount.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.databinding.FragmentListBinding
import com.nexusmount.app.data.SmbHelper
import kotlinx.coroutines.launch

/**
 * Explorador de un share SMB (navegar carpetas).
 * Args: host, share, user, pass, domain, path
 */
class SmbBrowserFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private var host = ""
    private var share = ""
    private var user = ""
    private var pass = ""
    private var domain = ""
    private var path = ""
    private var pathStack = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            host = it.getString("host") ?: ""
            share = it.getString("share") ?: ""
            user = it.getString("user") ?: ""
            pass = it.getString("pass") ?: ""
            domain = it.getString("domain") ?: ""
            path = it.getString("path") ?: ""
            if (path.isNotEmpty()) pathStack.addAll(path.split("/").filter { p -> p.isNotEmpty() })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleText.text = "//$host/$share"
        binding.primaryAction.text = "Subir nivel"
        binding.primaryAction.setOnClickListener {
            if (pathStack.isNotEmpty()) {
                pathStack.removeAt(pathStack.lastIndex)
                load()
            } else {
                Toast.makeText(requireContext(), "Ya estás en la raíz del share", Toast.LENGTH_SHORT).show()
            }
        }
        load()
    }

    private fun currentPath(): String = pathStack.joinToString("/")

    private fun load() {
        val remote = currentPath()
        binding.subtitleText.text = if (remote.isEmpty()) "/" else "/$remote"
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Cargando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SmbHelper.listFiles(host, share, user, pass, remote, domain)
            if (!result.success) {
                binding.emptyText.text = result.message
                binding.recycler.adapter = SimpleAdapter(emptyList())
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                return@launch
            }
            val files = result.files
            if (files.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.text = "Carpeta vacía"
            } else {
                binding.emptyText.visibility = View.GONE
            }
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
            binding.recycler.adapter = SimpleAdapter(
                files.map {
                    it.name to if (it.isDirectory) "Carpeta" else formatSize(it.sizeBytes)
                }
            ) { pos ->
                val f = files[pos]
                if (f.isDirectory) {
                    pathStack.add(f.name)
                    load()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle(f.name)
                        .setMessage("Tamaño: ${formatSize(f.sizeBytes)}\nRuta: ${f.path}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${b / (1024 * 1024 * 1024)} GB"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun args(host: String, share: String, user: String, pass: String, domain: String) =
            Bundle().apply {
                putString("host", host)
                putString("share", share)
                putString("user", user)
                putString("pass", pass)
                putString("domain", domain)
            }
    }
}
