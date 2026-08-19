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
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusmount.app.cloud.CloudConnectors
import com.nexusmount.app.databinding.FragmentListBinding

/**
 * Páginas de configuración de módulos pendientes / avanzados
 * con requisitos explícitos para cada uno.
 */
class ModulesConfigFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    data class Module(
        val title: String,
        val status: String,
        val requirements: String,
        val configAction: (() -> Unit)? = null
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.text = "Módulos y requisitos"
        binding.subtitleText.text = "Configuración de lo pendiente / avanzado"
        binding.primaryAction.text = "Ver todos los requisitos"
        binding.primaryAction.setOnClickListener { showAllRequirements() }

        val modules = buildModules()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = SimpleAdapter(
            modules.map { it.title to it.status }
        ) { pos ->
            val m = modules[pos]
            AlertDialog.Builder(requireContext())
                .setTitle(m.title)
                .setMessage(m.requirements)
                .setPositiveButton("Configurar") { _, _ ->
                    m.configAction?.invoke()
                        ?: Toast.makeText(requireContext(), "Solo informativo", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    private fun prefs() = requireContext().getSharedPreferences("nexus_modules", 0)

    private fun buildModules(): List<Module> {
        val p = prefs()
        return listOf(
            Module(
                "Google Drive",
                if (p.getBoolean("gdrive_configured", false)) "Credenciales guardadas" else "No configurado",
                """
                REQUISITOS:
                • Proyecto en Google Cloud Console
                • Google Drive API activada
                • OAuth 2.0 Client ID tipo Android
                • SHA-1 del keystore de la app
                • package: com.nexusmount.app
                
                ESTADO: Estructura lista. Sin Client ID no hay API real.
                """.trimIndent()
            ) {
                saveTextConfig(
                    "Google Drive",
                    listOf("Client ID OAuth" to "gdrive_client_id", "Cuenta email" to "gdrive_email")
                ) { p.edit().putBoolean("gdrive_configured", true).apply() }
            },
            Module(
                "Amazon S3 / compatible",
                if (p.getBoolean("s3_configured", false)) "Configurado" else "No configurado",
                """
                REQUISITOS:
                • Access Key ID
                • Secret Access Key
                • Region (ej: eu-west-1)
                • Bucket name
                • Endpoint opcional (MinIO, Wasabi, R2)
                
                Validación local de campos incluida.
                """.trimIndent()
            ) {
                configS3()
            },
            Module(
                "OneDrive",
                if (p.getBoolean("onedrive_configured", false)) "Configurado" else "No configurado",
                """
                REQUISITOS:
                • App registration en Azure AD
                • Client ID + redirect URI
                • Permisos Microsoft Graph: Files.ReadWrite
                • Librería MSAL en la app
                """.trimIndent()
            ) {
                saveTextConfig(
                    "OneDrive",
                    listOf("Azure Client ID" to "onedrive_client_id")
                ) { p.edit().putBoolean("onedrive_configured", true).apply() }
            },
            Module(
                "WebDAV",
                if (p.getBoolean("webdav_configured", false)) "Configurado" else "No configurado",
                """
                REQUISITOS:
                • URL (https://cloud…/remote.php/dav)
                • Usuario y contraseña
                • HTTPS recomendado
                """.trimIndent()
            ) {
                saveTextConfig(
                    "WebDAV",
                    listOf(
                        "URL" to "webdav_url",
                        "Usuario" to "webdav_user",
                        "Contraseña" to "webdav_pass"
                    )
                ) { p.edit().putBoolean("webdav_configured", true).apply() }
            },
            Module(
                "IA Cloud (Gemini / OpenAI)",
                if (p.getBoolean("ai_cloud", false)) "Clave guardada" else "Desactivado",
                """
                REQUISITOS:
                • API Key de OpenAI o Google AI Studio (Gemini)
                • Conexión a Internet
                • Aceptar envío de prompts (posible PII)
                
                PRIVACIDAD: activa anonimización en Ajustes si no quieres datos personales.
                La IA local (Command AI) no requiere clave.
                """.trimIndent()
            ) {
                saveTextConfig(
                    "IA Cloud",
                    listOf(
                        "Proveedor (openai/gemini)" to "ai_provider",
                        "API Key" to "ai_api_key"
                    )
                ) { p.edit().putBoolean("ai_cloud", true).apply() }
            },
            Module(
                "Anonimización PII",
                if (p.getBoolean("pii_anon", true)) "Activada (default)" else "Desactivada",
                """
                REQUISITOS: ninguno.
                Enmascara emails, IPs y rutas antes de enviar prompts a la nube.
                """.trimIndent()
            ) {
                val next = !p.getBoolean("pii_anon", true)
                p.edit().putBoolean("pii_anon", next).apply()
                Toast.makeText(requireContext(), if (next) "PII ON" else "PII OFF", Toast.LENGTH_SHORT).show()
            },
            Module(
                "Recetas / automatización IA",
                if (p.getString("recipes", null) != null) "Hay recetas" else "Sin recetas",
                """
                REQUISITOS:
                • Permiso de almacenamiento
                • Opcional: WorkManager (ya en dependencias)
                
                Ejemplo: «comprimir archivos >30 días en Download»
                """.trimIndent()
            ) {
                val input = EditText(requireContext()).apply {
                    hint = "Ej: zip archivos antiguos en Download"
                    setText(p.getString("recipes", ""))
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Nueva receta")
                    .setView(input)
                    .setPositiveButton("Guardar") { _, _ ->
                        p.edit().putString("recipes", input.text.toString()).apply()
                        Toast.makeText(requireContext(), "Receta guardada", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            Module(
                "YubiKey / llave hardware",
                "No disponible en esta build",
                """
                REQUISITOS (futuro):
                • Dispositivo con NFC o USB OTG
                • Librería Yubico Android
                • Credencial FIDO2/WebAuthn o OTP
                
                Ahora: usa TOTP en módulo Seguridad.
                """.trimIndent()
            ),
            Module(
                "ACL usuarios/grupos",
                "Limitado por Android",
                """
                REQUISITOS en NAS/Linux:
                • Sistema de archivos con ACL (ext4, XFS…)
                • Control remoto vía SSH/SMB no expuesto por Android
                
                En el teléfono solo se aplican permisos del sandbox de la app.
                Para ACL reales configúralos en el NAS/servidor Samba.
                """.trimIndent()
            ),
            Module(
                "TAR / formatos técnicos",
                "ZIP sí · TAR pendiente de lib",
                """
                REQUISITOS para TAR:
                • Librería apache commons-compress o similar
                
                Actualmente: ZIP crear/extraer en Explorador Pro.
                """.trimIndent()
            ),
            Module(
                "Renombrado masivo",
                "Configurable",
                """
                REQUISITOS: permiso de escritura en la carpeta.
                Patrón: prefijo, sufijo o reemplazo de texto.
                """.trimIndent()
            ) {
                val folder = EditText(requireContext()).apply { hint = "Carpeta absoluta" }
                val pattern = EditText(requireContext()).apply { hint = "Buscar texto" }
                val replace = EditText(requireContext()).apply { hint = "Reemplazar por" }
                val box = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 20, 40, 10)
                    addView(folder); addView(pattern); addView(replace)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Renombrado masivo")
                    .setView(box)
                    .setPositiveButton("Aplicar") { _, _ ->
                        massRename(
                            folder.text.toString(),
                            pattern.text.toString(),
                            replace.text.toString()
                        )
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            Module(
                "Instalador modular",
                "Todos los módulos en la APK",
                """
                En esta versión nativa los módulos van en la misma APK.
                El «instalador» de la web era un asistente visual.
                
                Para excluir módulos en compilación: productFlavors en Gradle.
                """.trimIndent()
            )
        )
    }

    private fun configS3() {
        val keys = listOf(
            "Access Key" to "s3_key",
            "Secret Key" to "s3_secret",
            "Region" to "s3_region",
            "Bucket" to "s3_bucket",
            "Endpoint (opcional)" to "s3_endpoint"
        )
        saveTextConfig("S3", keys) {
            val err = CloudConnectors.validateS3(
                prefs().getString("s3_key", "") ?: "",
                prefs().getString("s3_secret", "") ?: "",
                prefs().getString("s3_bucket", "") ?: "",
                prefs().getString("s3_region", "") ?: ""
            )
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            } else {
                prefs().edit().putBoolean("s3_configured", true).apply()
                Toast.makeText(requireContext(), "S3 guardado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTextConfig(
        title: String,
        fields: List<Pair<String, String>>,
        onSave: () -> Unit
    ) {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val edits = fields.map { (hint, key) ->
            EditText(requireContext()).apply {
                this.hint = hint
                setText(prefs().getString(key, ""))
                box.addView(this)
                key to this
            }.let { key to it }
        }
        // fix pairing
        val pairs = fields.mapIndexed { i, (hint, key) ->
            val e = EditText(requireContext()).apply {
                this.hint = hint
                setText(prefs().getString(key, "") ?: "")
            }
            box.addView(e)
            key to e
        }
        // clear duplicate views if any - rebuild clean
        box.removeAllViews()
        val inputs = fields.map { (hint, key) ->
            val e = EditText(requireContext()).apply {
                this.hint = hint
                setText(prefs().getString(key, "") ?: "")
                setTextColor(0xFFDAE2FD.toInt())
                setHintTextColor(0xFF8C909F.toInt())
            }
            box.addView(e)
            key to e
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val ed = prefs().edit()
                inputs.forEach { (key, edit) -> ed.putString(key, edit.text.toString()) }
                ed.apply()
                onSave()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun massRename(folderPath: String, find: String, replace: String) {
        if (folderPath.isBlank() || find.isBlank()) {
            Toast.makeText(requireContext(), "Carpeta y texto a buscar obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = java.io.File(folderPath)
        if (!dir.isDirectory) {
            Toast.makeText(requireContext(), "Carpeta no válida", Toast.LENGTH_SHORT).show()
            return
        }
        var n = 0
        dir.listFiles()?.forEach { f ->
            if (f.name.contains(find)) {
                val neu = f.name.replace(find, replace)
                if (f.renameTo(java.io.File(dir, neu))) n++
            }
        }
        Toast.makeText(requireContext(), "Renombrados: $n", Toast.LENGTH_LONG).show()
    }

    private fun showAllRequirements() {
        val text = buildModules().joinToString("\n\n————\n\n") {
            "• ${it.title}\n${it.requirements}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Requisitos de todos los módulos")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
