package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.core.AuthConfig
import com.github.aidarkhusainov.reqrun.core.AuthScheme
import com.github.aidarkhusainov.reqrun.core.AuthType
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.github.aidarkhusainov.reqrun.settings.ReqRunEnvPathSettings
import com.github.aidarkhusainov.reqrun.settings.ReqRunProjectEnvPathSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
@State(name = "ReqRunEnvironmentState", storages = [Storage("reqrun-environment.xml")])
class ReqRunEnvironmentService(private val project: Project) : PersistentStateComponent<ReqRunEnvironmentService.State> {
    data class State(
        var envName: String? = null,
        var privateIgnoreSuggested: Boolean = false
    )

    private val log = logger<ReqRunEnvironmentService>()
    private var state = State()
    private val jsonErrorNotified = HashSet<Path>()
    private val authConfigWarningKeys = HashSet<String>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getSelectedEnvironment(): String? = state.envName

    fun setSelectedEnvironment(envName: String?) {
        state.envName = envName
    }

    fun getEnvironmentNames(file: VirtualFile?): List<String> {
        val paths = resolveEnvFiles(file)
        maybeSuggestGitignore(paths.privatePath)
        val merged = loadMergedEnvironments(paths)
        return merged.keys.sorted()
    }

    fun loadVariablesForFile(file: VirtualFile?): Map<String, String> {
        val envName = state.envName ?: return emptyMap()
        val paths = resolveEnvFiles(file)
        maybeSuggestGitignore(paths.privatePath)
        val merged = loadMergedEnvironments(paths)
        return merged[envName] ?: emptyMap()
    }

    fun loadAuthConfigsForFile(file: VirtualFile?): Map<String, AuthConfig> {
        val envName = state.envName ?: return emptyMap()
        val paths = resolveEnvFiles(file)
        maybeSuggestGitignore(paths.privatePath)
        val shared = paths.sharedPath?.let { loadAuthConfigs(it, envName) } ?: emptyMap()
        val privateAuth = paths.privatePath?.let { loadAuthConfigs(it, envName) } ?: emptyMap()
        if (shared.isEmpty()) return privateAuth
        if (privateAuth.isEmpty()) return shared
        val result = LinkedHashMap(shared)
        result.putAll(privateAuth)
        return result
    }

    fun describeEnvPaths(file: VirtualFile?): String {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val scope = if (projectSettings.state.useProjectPaths) "Project" else "Global"
        val paths = resolveEnvFiles(file)
        val shared = paths.sharedPath?.toString() ?: "default search"
        val privatePath = paths.privatePath?.toString() ?: "default search"
        return "<html>Shared ($scope): $shared<br>Private ($scope): $privatePath</html>"
    }

    fun ensureEnvFile(file: VirtualFile?, isPrivate: Boolean): Path? {
        val paths = resolveEnvFiles(file)
        val target = if (isPrivate) paths.privatePath else paths.sharedPath
        val resolved = target ?: run {
            val envDir = resolveEnvironmentDir(file) ?: return null
            envDir.resolve(if (isPrivate) PRIVATE_ENV_FILE else SHARED_ENV_FILE)
        }
        val finalTarget = resolved
        if (Files.exists(finalTarget)) return finalTarget
        return try {
            finalTarget.parent?.let { Files.createDirectories(it) }
            Files.writeString(finalTarget, "{\n  \"local\": {}\n}\n", StandardCharsets.UTF_8)
            if (isPrivate) {
                maybeSuggestGitignore(finalTarget)
            }
            finalTarget
        } catch (t: Throwable) {
            log.warn("ReqRun: failed to create ${finalTarget.fileName}", t)
            null
        }
    }

    private fun loadMergedEnvironments(paths: EnvPaths): Map<String, Map<String, String>> {
        val shared = paths.sharedPath?.let { loadEnvFile(it) } ?: emptyMap()
        val privateEnv = paths.privatePath?.let { loadEnvFile(it) } ?: emptyMap()
        if (shared.isEmpty()) return privateEnv
        if (privateEnv.isEmpty()) return shared
        val result = LinkedHashMap<String, MutableMap<String, String>>()
        for ((env, vars) in shared) {
            result[env] = LinkedHashMap(vars)
        }
        for ((env, vars) in privateEnv) {
            val target = result.getOrPut(env) { LinkedHashMap() }
            target.putAll(vars)
        }
        return result
    }

    private fun loadEnvFile(path: Path): Map<String, Map<String, String>> {
        if (!Files.exists(path)) return emptyMap()
        val json = readJson(path) ?: return emptyMap()
        val result = LinkedHashMap<String, Map<String, String>>()
        for ((envName, envValue) in json.entrySet()) {
            if (!envValue.isJsonObject) continue
            val envObj = envValue.asJsonObject
            val vars = LinkedHashMap<String, String>()
            for ((key, value) in envObj.entrySet()) {
                vars[key] = stringify(value)
            }
            result[envName] = vars
        }
        return result
    }

    private fun loadAuthConfigs(path: Path, envName: String): Map<String, AuthConfig> {
        if (!Files.exists(path)) return emptyMap()
        val json = readJson(path) ?: return emptyMap()
        val envValue = json.get(envName) ?: return emptyMap()
        if (!envValue.isJsonObject) return emptyMap()
        val envObj = envValue.asJsonObject
        val security = envObj.getAsJsonObject("Security") ?: return emptyMap()
        val auth = security.getAsJsonObject("Auth") ?: return emptyMap()
        val result = LinkedHashMap<String, AuthConfig>()
        for ((id, value) in auth.entrySet()) {
            if (!value.isJsonObject) continue
            val config = parseAuthConfig(value.asJsonObject) ?: continue
            validateAuthConfig(path, envName, id, config)
            result[id] = config
        }
        return result
    }

    private fun parseAuthConfig(obj: JsonObject): AuthConfig? {
        val type = obj.get("Type")?.asString ?: return null
        if (!type.equals("Static", ignoreCase = true)) return null
        val schemeRaw = obj.get("Scheme")?.asString ?: return null
        val scheme = parseScheme(schemeRaw) ?: return null
        val token = obj.get("Token")?.let { stringify(it) }
        val username = obj.get("Username")?.let { stringify(it) }
        val password = obj.get("Password")?.let { stringify(it) }
        val header = obj.get("Header")?.let { stringify(it) }
        return AuthConfig(
            type = AuthType.STATIC,
            scheme = scheme,
            token = token,
            username = username,
            password = password,
            header = header,
        )
    }

    private fun parseScheme(raw: String): AuthScheme? {
        return when (raw.trim().lowercase()) {
            "bearer" -> AuthScheme.BEARER
            "basic" -> AuthScheme.BASIC
            "apikey", "api-key", "api_key" -> AuthScheme.API_KEY
            else -> null
        }
    }

    private fun stringify(value: JsonElement): String {
        return if (value.isJsonPrimitive) {
            value.asJsonPrimitive.asString
        } else {
            value.toString()
        }
    }

    private fun readJson(path: Path): JsonObject? {
        val content = readText(path) ?: return null
        val parsed = try {
            JsonParser.parseString(content)
        } catch (t: Throwable) {
            log.warn("ReqRun: failed to parse ${path.fileName}", t)
            notifyJsonError(path, "Failed to parse ${path.fileName}. Check JSON syntax.")
            return null
        }
        if (!parsed.isJsonObject) {
            log.warn("ReqRun: invalid json root in ${path.fileName}")
            notifyJsonError(path, "Invalid JSON in ${path.fileName}. Expected object at root.")
            return null
        }
        clearJsonError(path)
        return parsed.asJsonObject
    }

    private fun readText(path: Path): String? {
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        val documentText = if (vFile != null) {
            ReadAction.compute<String?, RuntimeException> {
                val document = FileDocumentManager.getInstance().getDocument(vFile)
                if (document != null && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
                    document.text
                } else {
                    null
                }
            }
        } else {
            null
        }
        if (documentText != null) return documentText
        return try {
            Files.readString(path, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun validateAuthConfig(path: Path, envName: String, id: String, config: AuthConfig) {
        val hasToken = !config.token.isNullOrBlank()
        val hasUser = !config.username.isNullOrBlank()
        val hasPass = !config.password.isNullOrBlank()
        when (config.scheme) {
            AuthScheme.BASIC -> {
                if (hasToken && (hasUser || hasPass)) {
                    warnOnce(
                        key = "auth:${path.normalize()}:$envName:$id:basic",
                        message = "Auth config '$id' in env '$envName' (${path.fileName}) mixes Token with Username/Password."
                    )
                }
            }
            AuthScheme.BEARER, AuthScheme.API_KEY -> {
                if (hasUser || hasPass) {
                    warnOnce(
                        key = "auth:${path.normalize()}:$envName:$id:${config.scheme.name.lowercase()}",
                        message = "Auth config '$id' in env '$envName' (${path.fileName}) ignores Username/Password for ${config.scheme.name.lowercase()}."
                    )
                }
            }
        }
    }

    private fun notifyJsonError(path: Path, message: String) {
        if (!jsonErrorNotified.add(path.normalize())) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ReqRunNotifier.warn(project, message)
        }
    }

    private fun clearJsonError(path: Path) {
        jsonErrorNotified.remove(path.normalize())
    }

    private fun warnOnce(key: String, message: String) {
        if (!authConfigWarningKeys.add(key)) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ReqRunNotifier.warn(project, message)
        }
    }

    private fun resolveEnvironmentDir(file: VirtualFile?): Path? {
        val basePath = project.basePath ?: return null
        val base = Path.of(basePath).normalize()
        var current = Path.of(file?.parent?.path ?: basePath).normalize()
        if (!current.startsWith(base)) {
            current = base
        }
        while (true) {
            if (Files.exists(current.resolve(SHARED_ENV_FILE)) || Files.exists(current.resolve(PRIVATE_ENV_FILE))) {
                return current
            }
            if (current == base) break
            current = current.parent ?: break
        }
        return base
    }

    private fun maybeSuggestGitignore(privatePath: Path?) {
        if (privatePath == null) return
        if (!Files.exists(privatePath)) return
        if (state.privateIgnoreSuggested) return
        val basePath = project.basePath ?: return
        val base = Path.of(basePath).normalize()
        if (!privatePath.normalize().startsWith(base)) return
        val gitignore = Path.of(basePath, ".gitignore")
        if (!Files.exists(gitignore)) return
        val content = try {
            Files.readString(gitignore, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            return
        }
        if (content.contains(privatePath.fileName.toString())) {
            state.privateIgnoreSuggested = true
            return
        }
        state.privateIgnoreSuggested = true
        ReqRunNotifier.warn(project, "Consider adding ${privatePath.fileName} to .gitignore")
    }

    private data class EnvPaths(val sharedPath: Path?, val privatePath: Path?)

    private fun resolveEnvFiles(file: VirtualFile?): EnvPaths {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val appSettings = ApplicationManager.getApplication().getService(ReqRunEnvPathSettings::class.java)
        val basePath = project.basePath

        val sharedProject = if (projectSettings.state.useProjectPaths) projectSettings.state.sharedPath else null
        val privateProject = if (projectSettings.state.useProjectPaths) projectSettings.state.privatePath else null

        val shared = resolvePath(sharedProject ?: appSettings.state.sharedPath, basePath)
        val privatePath = resolvePath(privateProject ?: appSettings.state.privatePath, basePath)
        if (shared != null || privatePath != null) {
            return EnvPaths(shared, privatePath)
        }

        val envDir = resolveEnvironmentDir(file) ?: return EnvPaths(null, null)
        return EnvPaths(
            envDir.resolve(SHARED_ENV_FILE),
            envDir.resolve(PRIVATE_ENV_FILE)
        )
    }

    private fun resolvePath(path: String?, basePath: String?): Path? {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val p = Path.of(trimmed)
        return if (p.isAbsolute) p.normalize() else {
            if (basePath.isNullOrBlank()) p.normalize() else Path.of(basePath, trimmed).normalize()
        }
    }

    companion object {
        private const val SHARED_ENV_FILE = "http-client.env.json"
        private const val PRIVATE_ENV_FILE = "http-client.private.env.json"
    }
}
