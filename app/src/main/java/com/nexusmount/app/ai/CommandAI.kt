package com.nexusmount.app.ai

import java.io.File

/**
 * Nexus Command AI — parser local de lenguaje natural (ES/EN)
 * que traduce a acciones reales de archivos.
 */
object CommandAI {

    sealed class Action {
        data class ListDir(val path: String?) : Action()
        data class Search(val query: String) : Action()
        data class Delete(val name: String) : Action()
        data class Mkdir(val name: String) : Action()
        data class Zip(val name: String) : Action()
        data class Unzip(val name: String) : Action()
        data class Backup(val path: String?) : Action()
        data class Help(val text: String) : Action()
        data class Unknown(val text: String) : Action()
    }

    fun parse(input: String): Action {
        val t = input.trim().lowercase()
        if (t.isBlank()) return Action.Help(helpText())

        // Spanish + English patterns
        when {
            t.matches(Regex(".*(ayuda|help|comandos).*")) ->
                return Action.Help(helpText())

            t.matches(Regex(".*(lista|listar|mostrar|list).*carpetas?.*")) ||
                t.matches(Regex(".*(list|show).*folder.*")) ->
                return Action.ListDir(null)

            t.matches(Regex(".*busca(r)? (.+)")) -> {
                val q = Regex(".*busca(r)? (.+)").find(t)?.groupValues?.getOrNull(2)?.trim()
                return Action.Search(q ?: t)
            }
            t.matches(Regex(".*search (.+)")) -> {
                val q = Regex(".*search (.+)").find(t)?.groupValues?.getOrNull(1)?.trim()
                return Action.Search(q ?: t)
            }

            t.matches(Regex(".*crea(r)? carpeta (.+)")) -> {
                val n = Regex(".*crea(r)? carpeta (.+)").find(t)?.groupValues?.getOrNull(2)?.trim()
                return Action.Mkdir(n ?: "nueva")
            }
            t.matches(Regex(".*mkdir (.+)")) -> {
                val n = Regex(".*mkdir (.+)").find(t)?.groupValues?.getOrNull(1)?.trim()
                return Action.Mkdir(n ?: "nueva")
            }

            t.matches(Regex(".*borra(r)? (.+)")) || t.matches(Regex(".*elimina(r)? (.+)")) -> {
                val n = Regex(".*(borra(r)?|elimina(r)?) (.+)").find(t)?.groupValues?.last()?.trim()
                return Action.Delete(n ?: "")
            }
            t.matches(Regex(".*delete (.+)")) -> {
                val n = Regex(".*delete (.+)").find(t)?.groupValues?.getOrNull(1)?.trim()
                return Action.Delete(n ?: "")
            }

            t.matches(Regex(".*comprim(e|ir)? (.+)")) || t.contains("zip ") -> {
                val n = Regex(".*(comprim(e|ir)?|zip) (.+)").find(t)?.groupValues?.last()?.trim()
                return Action.Zip(n ?: "")
            }

            t.matches(Regex(".*descomprim(e|ir)? (.+)")) || t.contains("unzip ") -> {
                val n = Regex(".*(descomprim(e|ir)?|unzip) (.+)").find(t)?.groupValues?.last()?.trim()
                return Action.Unzip(n ?: "")
            }

            t.contains("backup") || t.contains("copia de seguridad") ->
                return Action.Backup(null)

            else -> return Action.Unknown(
                "No entendí: \"$input\". Escribe «ayuda» para ver comandos."
            )
        }
    }

    fun helpText() = """
        Comandos disponibles:
        • lista carpetas
        • busca [nombre]
        • crear carpeta [nombre]
        • borrar [nombre]
        • comprimir [archivo/carpeta]
        • descomprimir [archivo.zip]
        • backup
        • ayuda
    """.trimIndent()

    fun executeList(dir: File): String {
        val files = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return "Sin acceso o vacía"
        if (files.isEmpty()) return "Carpeta vacía"
        return files.take(50).joinToString("\n") {
            (if (it.isDirectory) "📁 " else "📄 ") + it.name
        }
    }
}
