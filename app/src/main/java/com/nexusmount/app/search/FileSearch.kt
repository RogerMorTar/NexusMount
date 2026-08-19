package com.nexusmount.app.search

import java.io.File
import java.util.Date

data class SearchQuery(
    val nameContains: String? = null,
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val extensions: List<String>? = null,
    val pathContains: String? = null
)

data class SearchHit(
    val file: File,
    val path: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean
)

object FileSearch {

    fun search(root: File, query: SearchQuery, maxResults: Int = 200): List<SearchHit> {
        val results = mutableListOf<SearchHit>()
        fun walk(dir: File) {
            if (results.size >= maxResults) return
            val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
            for (f in files) {
                if (results.size >= maxResults) return
                if (matches(f, query)) {
                    results.add(
                        SearchHit(
                            file = f,
                            path = f.absolutePath,
                            size = if (f.isFile) f.length() else 0,
                            modified = f.lastModified(),
                            isDir = f.isDirectory
                        )
                    )
                }
                if (f.isDirectory) walk(f)
            }
        }
        if (root.exists()) walk(root)
        return results
    }

    private fun matches(f: File, q: SearchQuery): Boolean {
        if (!q.nameContains.isNullOrBlank() &&
            !f.name.contains(q.nameContains, ignoreCase = true)
        ) return false
        if (!q.pathContains.isNullOrBlank() &&
            !f.absolutePath.contains(q.pathContains, ignoreCase = true)
        ) return false
        val mod = f.lastModified()
        if (q.modifiedAfter != null && mod < q.modifiedAfter) return false
        if (q.modifiedBefore != null && mod > q.modifiedBefore) return false
        if (f.isFile) {
            val size = f.length()
            if (q.minSize != null && size < q.minSize) return false
            if (q.maxSize != null && size > q.maxSize) return false
            if (!q.extensions.isNullOrEmpty()) {
                val ext = f.extension.lowercase()
                if (q.extensions.none { it.equals(ext, true) }) return false
            }
        }
        return true
    }
}
