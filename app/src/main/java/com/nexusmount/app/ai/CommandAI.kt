package com.nexusmount.app.ai

import java.io.File

/**
 * Rigo — asistente NexusMount con control amplio de la app.
 */
object CommandAI {

    const val ASSISTANT_NAME = "Rigo"

    sealed class Action {
        data class ListDir(val path: String?) : Action()
        data class Search(val query: String) : Action()
        data class Delete(val name: String) : Action()
        data class Mkdir(val name: String) : Action()
        data class Zip(val name: String) : Action()
        data class Unzip(val name: String) : Action()
        data class Backup(val path: String?) : Action()
        data class OpenSection(val section: String) : Action()
        data class Status(val text: String) : Action()
        data class Help(val text: String) : Action()
        data class Chat(val text: String) : Action()
        object ListDrives : Action()
        object TransferHint : Action()
        object InterconnectHint : Action()
        object CollabHint : Action()
        object TailscaleStatus : Action()
        object StorageInfo : Action()
        data class CountFiles(val path: String?) : Action()
        data class LargestFiles(val n: Int = 10) : Action()
        data class RecentFiles(val n: Int = 10) : Action()
        object FreeSpace : Action()
        data class Unknown(val text: String) : Action()
    }

    fun greeting(): String = """
        Hola, soy $ASSISTANT_NAME.
        
        Puedo ayudarte con archivos, unidades SMB, backups, transferencias,
        interconexión, colaborativo y Tailscale.
        
        Escribe «ayuda» para ver comandos, o prueba:
        • estado · unidades · espacio libre
        • lista carpetas · busca pdf
        • backup · abrir transferencias
        • interconexión · colaborativo · tailscale
    """.trimIndent()

    fun helpText() = """
        ══ Archivos ══
        • lista carpetas
        • busca [nombre]
        • crear carpeta [nombre]
        • borrar [nombre]
        • comprimir / descomprimir [archivo]
        • archivos grandes · archivos recientes
        • contar archivos · espacio libre
        
        ══ Unidades y red ══
        • unidades / smb
        • tailscale
        • interconexión (visor sin Samba)
        • transferencias
        
        ══ App ══
        • backup
        • colaborativo · limpieza · anuncios
        • abrir [dashboard|unidades|explorador|seguridad|ajustes|…]
        • estado · ayuda · hola
    """.trimIndent()

    fun parse(input: String): Action {
        val t = input.trim().lowercase()
        if (t.isBlank()) return Action.Help(helpText())

        when {
            t.matches(Regex(".*(hola|hey|buenas|hi|hello|qué tal|que tal).*")) ->
                return Action.Chat(greeting())

            t.matches(Regex(".*(quién eres|quien eres|qué eres|que eres|what are you).*")) ->
                return Action.Chat(
                    "Soy $ASSISTANT_NAME, el asistente de NexusMount. " +
                        "Controlo archivos, unidades, backups e interconexión bajo tu solicitud. " +
                        "Por defecto trabajo en local (sin nube)."
                )

            t.matches(Regex(".*(ayuda|help|comandos|qué puedes|que puedes).*")) ->
                return Action.Help(helpText())

            t.matches(Regex(".*(estado|status|salud|resumen).*")) ->
                return Action.Status("ok")

            t.matches(Regex(".*(unidades|drives|smb|mis unidades).*")) ->
                return Action.ListDrives

            t.matches(Regex(".*(transferencia|transferencias|copiar archivo).*")) ->
                return Action.TransferHint

            t.matches(Regex(".*(interconex|interconnex|visor|exponer disco).*")) ->
                return Action.InterconnectHint

            t.matches(Regex(".*(colaborat|espacio de trabajo|invitar).*")) ->
                return Action.CollabHint

            t.matches(Regex(".*(tailscale|vpn|100\\.).*")) ->
                return Action.TailscaleStatus

            t.matches(Regex(".*(espacio libre|free space|almacenamiento disponible).*")) ->
                return Action.FreeSpace

            t.matches(Regex(".*(archivos grandes|más grandes|mas grandes|largest).*")) ->
                return Action.LargestFiles(10)

            t.matches(Regex(".*(archivos recientes|últimos archivos|ultimos archivos|recent).*")) ->
                return Action.RecentFiles(10)

            t.matches(Regex(".*(contar|cuántos|cuantos).*archivo.*")) ->
                return Action.CountFiles(null)

            t.matches(Regex(".*(info|información).*almacenamiento.*")) ||
                t.matches(Regex(".*almacenamiento.*")) ->
                return Action.StorageInfo

            // Navegación
            sectionMatch(t, "unidades", "drives", "smb") -> return Action.OpenSection("drives")
            sectionMatch(t, "explorador", "archivos", "files") -> return Action.OpenSection("files")
            sectionMatch(t, "seguridad", "security", "2fa") -> return Action.OpenSection("security")
            sectionMatch(t, "tailscale", "vpn") -> return Action.OpenSection("tailscale")
            sectionMatch(t, "backup", "backups") -> return Action.OpenSection("backup")
            sectionMatch(t, "ajustes", "settings", "config") -> return Action.OpenSection("settings")
            sectionMatch(t, "módulos", "modulos", "modules") -> return Action.OpenSection("modules")
            sectionMatch(t, "samba", "red") -> return Action.OpenSection("samba")
            sectionMatch(t, "logs", "registro") -> return Action.OpenSection("logs")
            sectionMatch(t, "dashboard", "inicio") -> return Action.OpenSection("dashboard")
            sectionMatch(t, "transferencias", "transfers") -> return Action.OpenSection("transfers")
            sectionMatch(t, "interconex", "interconnex") -> return Action.OpenSection("interconnect")
            sectionMatch(t, "colaborat") -> return Action.OpenSection("collab")
            sectionMatch(t, "limpieza", "limpiar", "cleanup") -> return Action.OpenSection("cleanup")
            sectionMatch(t, "memoria", "ram", "memory", "almacenamiento") -> return Action.OpenSection("memory")
            sectionMatch(t, "anuncios", "adblock", "bloqueo") -> return Action.OpenSection("adblock")

            t.matches(Regex(".*(lista|listar|mostrar|list).*carpetas?.*")) ||
                t == "ls" || t == "dir" ->
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

            t.matches(Regex(".*comprim(e|ir)? (.+)")) || (t.contains("zip ") && !t.contains("unzip")) -> {
                val n = Regex(".*(comprim(e|ir)?|zip) (.+)").find(t)?.groupValues?.last()?.trim()
                return Action.Zip(n ?: "")
            }

            t.matches(Regex(".*descomprim(e|ir)? (.+)")) || t.contains("unzip ") -> {
                val n = Regex(".*(descomprim(e|ir)?|unzip) (.+)").find(t)?.groupValues?.last()?.trim()
                return Action.Unzip(n ?: "")
            }

            t.contains("backup") || t.contains("copia de seguridad") ->
                return Action.Backup(null)

            t.contains("rigo") || t.contains("asistente") ->
                return Action.Chat(greeting())

            else -> return Action.Unknown(
                "No entendí: «$input».\nEscribe «ayuda» para ver lo que puedo hacer."
            )
        }
    }

    private fun sectionMatch(t: String, vararg keys: String): Boolean {
        if (!t.contains("abrir") && !t.startsWith("ir a") && !t.startsWith("ve a") && !t.startsWith("ir al")) {
            // permitir "abrir X" principalmente
            if (keys.any { t == it || t == "abrir $it" || t == "ir a $it" || t == "ir al $it" }) return true
            return false
        }
        return keys.any { t.contains(it) }
    }

    fun executeList(dir: File): String {
        val files = dir.listFiles()?.sortedBy { it.name.lowercase() }
            ?: return "Sin acceso o vacía: ${dir.absolutePath}"
        if (files.isEmpty()) return "Carpeta vacía"
        return files.take(60).joinToString("\n") {
            (if (it.isDirectory) "📁 " else "📄 ") + it.name
        } + if (files.size > 60) "\n… y ${files.size - 60} más" else ""
    }

    fun countFiles(dir: File): String {
        var files = 0
        var dirs = 0
        fun walk(f: File, depth: Int) {
            if (depth > 4) return
            val kids = f.listFiles() ?: return
            for (k in kids) {
                if (k.isDirectory) {
                    dirs++
                    walk(k, depth + 1)
                } else files++
            }
        }
        walk(dir, 0)
        return "En ${dir.name} (hasta 4 niveles):\n• Archivos: $files\n• Carpetas: $dirs"
    }

    fun largestFiles(dir: File, n: Int): String {
        val found = mutableListOf<File>()
        fun walk(f: File, depth: Int) {
            if (depth > 3) return
            val kids = f.listFiles() ?: return
            for (k in kids) {
                if (k.isFile) found.add(k)
                else if (k.isDirectory) walk(k, depth + 1)
            }
        }
        walk(dir, 0)
        val top = found.sortedByDescending { it.length() }.take(n)
        if (top.isEmpty()) return "No se encontraron archivos"
        return top.joinToString("\n") {
            "📄 ${it.name} — ${formatSize(it.length())}"
        }
    }

    fun recentFiles(dir: File, n: Int): String {
        val found = mutableListOf<File>()
        fun walk(f: File, depth: Int) {
            if (depth > 3) return
            val kids = f.listFiles() ?: return
            for (k in kids) {
                if (k.isFile) found.add(k)
                else if (k.isDirectory) walk(k, depth + 1)
            }
        }
        walk(dir, 0)
        val top = found.sortedByDescending { it.lastModified() }.take(n)
        if (top.isEmpty()) return "No se encontraron archivos"
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return top.joinToString("\n") {
            "📄 ${it.name} — ${fmt.format(java.util.Date(it.lastModified()))}"
        }
    }

    fun freeSpace(dir: File): String {
        val free = dir.freeSpace
        val total = dir.totalSpace
        val used = total - free
        return "Almacenamiento (${dir.absolutePath}):\n" +
            "• Libre: ${formatSize(free)}\n" +
            "• Usado: ${formatSize(used)}\n" +
            "• Total: ${formatSize(total)}"
    }

    private fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1024 * 1024) return "${b / 1024} KB"
        if (b < 1024 * 1024 * 1024L) return "${b / (1024 * 1024)} MB"
        return "${"%.1f".format(b / (1024.0 * 1024 * 1024))} GB"
    }
}
