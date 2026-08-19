package com.nexusmount.app.ai

import java.io.File

/**
 * Rigo — asistente de NexusMount.
 * Control de la app por lenguaje natural (local, sin nube obligatoria).
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
        data class Unknown(val text: String) : Action()
    }

    fun greeting(): String = """
        Hola, soy $ASSISTANT_NAME, tu asistente de NexusMount.
        
        Puedo ayudarte bajo solicitud con archivos, backups, Samba y más.
        
        Prueba:
        • lista carpetas
        • busca fotos
        • crear carpeta Trabajo
        • backup
        • abrir unidades / seguridad / tailscale
        • estado
        • ayuda
    """.trimIndent()

    fun parse(input: String): Action {
        val t = input.trim().lowercase()
        if (t.isBlank()) return Action.Help(helpText())

        when {
            t.matches(Regex(".*(hola|hey|buenas|hi|hello).*")) ->
                return Action.Chat(greeting())

            t.matches(Regex(".*(quién eres|quien eres|quién sois|what are you).*")) ->
                return Action.Chat(
                    "Soy $ASSISTANT_NAME, el asistente de NexusMount. " +
                        "Controlo funciones de la app bajo tu solicitud (archivos, red, backups). " +
                        "La IA local no envía tus datos a la nube salvo que actives IA Cloud en Módulos."
                )

            t.matches(Regex(".*(ayuda|help|comandos).*")) ->
                return Action.Help(helpText())

            t.matches(Regex(".*(estado|status|salud).*")) ->
                return Action.Status("ok")

            t.matches(Regex(".*abrir (unidades|drives|smb).*")) || t.contains("mis unidades") ->
                return Action.OpenSection("drives")
            t.matches(Regex(".*abrir (explorador|archivos|files).*")) ->
                return Action.OpenSection("files")
            t.matches(Regex(".*abrir (seguridad|security|2fa).*")) ->
                return Action.OpenSection("security")
            t.matches(Regex(".*abrir (tailscale|vpn).*")) ->
                return Action.OpenSection("tailscale")
            t.matches(Regex(".*abrir (backup|backups).*")) ->
                return Action.OpenSection("backup")
            t.matches(Regex(".*abrir (ajustes|settings|config).*")) ->
                return Action.OpenSection("settings")
            t.matches(Regex(".*abrir (módulos|modulos|modules).*")) ->
                return Action.OpenSection("modules")
            t.matches(Regex(".*abrir (samba|red).*")) ->
                return Action.OpenSection("samba")
            t.matches(Regex(".*abrir (logs|registro).*")) ->
                return Action.OpenSection("logs")

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

            t.contains("rigo") || t.contains("asistente") ->
                return Action.Chat(greeting())

            else -> return Action.Unknown(
                "No entendí: \"$input\".\nEscribe «ayuda» o «hola» para hablar con $ASSISTANT_NAME."
            )
        }
    }

    fun helpText() = """
        Comandos de $ASSISTANT_NAME:
        • hola / quién eres
        • lista carpetas
        • busca [nombre]
        • crear carpeta [nombre]
        • borrar [nombre]
        • comprimir / descomprimir [archivo]
        • backup
        • estado
        • abrir unidades | explorador | seguridad | tailscale | backups | ajustes | módulos | samba | logs
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
