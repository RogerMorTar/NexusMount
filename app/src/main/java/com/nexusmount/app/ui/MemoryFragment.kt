package com.nexusmount.app.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nexusmount.app.databinding.FragmentMemoryBinding
import java.io.File
import java.util.Locale

/**
 * Análisis de RAM y almacenamiento con barras/gráficos.
 */
class MemoryFragment : Fragment() {
    private var _binding: FragmentMemoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentMemoryBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.btnRefresh.setOnClickListener { refresh() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val ctx = requireContext()

        // --- RAM ---
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramTotal = mi.totalMem
        val ramAvail = mi.availMem
        val ramUsed = (ramTotal - ramAvail).coerceAtLeast(0)
        val ramPct = if (ramTotal > 0) ((ramUsed * 100) / ramTotal).toInt() else 0
        binding.ramBar.progress = ramPct
        binding.ramText.text = buildString {
            append("Usada: ${fmt(ramUsed)}  ·  Libre: ${fmt(ramAvail)}\n")
            append("Total: ${fmt(ramTotal)}  ·  $ramPct%")
            if (mi.lowMemory) append("  ·  ⚠ Memoria baja")
        }

        // --- Storage ---
        val path = primaryStoragePath()
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val total = stat.blockCountLong * blockSize
        val free = stat.availableBlocksLong * blockSize
        val used = (total - free).coerceAtLeast(0)
        val storPct = if (total > 0) ((used * 100) / total).toInt() else 0
        binding.storageBar.progress = storPct
        binding.storageText.text =
            "Usado: ${fmt(used)}  ·  Libre: ${fmt(free)}\nTotal: ${fmt(total)}  ·  $storPct%\nRuta: $path"

        // Stacked bar (used vs free)
        binding.stackBar.removeAllViews()
        val usedW = storPct.coerceIn(1, 99)
        val freeW = (100 - usedW).coerceAtLeast(1)
        binding.stackBar.addView(colorSeg(0xFF3B82F6.toInt(), usedW))
        binding.stackBar.addView(colorSeg(0xFF22C55E.toInt(), freeW))
        binding.stackLegend.text =
            "Azul ${storPct}% usado · Verde ${100 - storPct}% libre"

        // --- App storage ---
        val appCache = sizeOf(ctx.cacheDir) + sizeOf(ctx.externalCacheDir)
        val appFiles = sizeOf(ctx.filesDir) + sizeOf(ctx.getExternalFilesDir(null))
        val appTotal = appCache + appFiles
        val appPctOfStorage = if (total > 0) ((appTotal * 100) / total).toInt().coerceIn(0, 100) else 0
        binding.appBar.progress = appPctOfStorage.coerceAtLeast(if (appTotal > 0) 1 else 0)
        binding.appText.text =
            "Caché: ${fmt(appCache)}  ·  Datos: ${fmt(appFiles)}\nTotal app: ${fmt(appTotal)}"

        // Runtime memory for this process
        val rt = Runtime.getRuntime()
        val heapMax = rt.maxMemory()
        val heapTotal = rt.totalMemory()
        val heapFree = rt.freeMemory()
        val heapUsed = heapTotal - heapFree

        binding.detailText.text = buildString {
            appendLine("RAM sistema")
            appendLine("  total=${fmt(ramTotal)} avail=${fmt(ramAvail)} threshold=${fmt(mi.threshold)}")
            appendLine()
            appendLine("Almacenamiento")
            appendLine("  path=$path")
            appendLine("  blocks=${stat.blockCountLong} size=$blockSize")
            appendLine()
            appendLine("Heap de NexusMount (JVM)")
            appendLine("  used=${fmt(heapUsed)} total=${fmt(heapTotal)} max=${fmt(heapMax)}")
            appendLine()
            appendLine("Consejo: usa «Limpieza inteligente» si la caché de la app es alta.")
        }
    }

    private fun colorSeg(color: Int, weight: Int): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight.toFloat())
            setBackgroundColor(color)
        }
    }

    private fun primaryStoragePath(): String {
        val emulated = File("/storage/emulated/0")
        if (emulated.exists()) return emulated.absolutePath
        return Environment.getDataDirectory().absolutePath
    }

    private fun sizeOf(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().maxDepth(6).forEach { f ->
            if (f.isFile) sum += f.length()
        }
        return sum
    }

    private fun fmt(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0)
        if (b < 1024 * 1024 * 1024L) return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
        return String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
