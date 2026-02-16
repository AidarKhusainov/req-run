package com.github.aidarkhusainov.reqrun.uitest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object UiTestProject {
    private val templateRoot =
        Paths
            .get(System.getProperty("user.dir"))
            .resolve("src")
            .resolve("uiTest")
            .resolve("testData")
            .resolve("project")

    private val projectRoot =
        Paths.get(
            System.getProperty("ui.test.project.path")
                ?: Paths.get(System.getProperty("user.dir"), "build", "uiTest", "project").toString(),
        )

    val requestsFile: Path = projectRoot.resolve("requests.http")
    val notesFile: Path = projectRoot.resolve("notes.txt")
    val sharedEnvFile: Path = projectRoot.resolve("http-client.env.json")
    val privateEnvFile: Path = projectRoot.resolve("http-client.private.env.json")
    val altSharedEnvFile: Path = projectRoot.resolve("http-client.alt.env.json")
    val altPrivateEnvFile: Path = projectRoot.resolve("http-client.alt.private.env.json")

    fun root(): Path = projectRoot

    fun reset(
        baseUrl: String,
        devFormat: String = "json",
        devError: String = "error500",
        prodFormat: String = "html",
        prodError: String = "error500",
        altFormat: String = "xml",
        altError: String = "error500",
    ) {
        ensureProjectRoot()
        copyTemplate("requests.http", requestsFile)
        copyTemplate("notes.txt", notesFile)
        writeSharedEnv(baseUrl, devFormat, devError, prodFormat, prodError)
        writePrivateEnv()
        writeAltEnv(baseUrl, altFormat, altError)
        writeAltPrivateEnv()
    }

    fun writeSharedEnv(
        baseUrl: String,
        devFormat: String,
        devError: String,
        prodFormat: String,
        prodError: String,
    ) {
        writeTemplate(
            templateRoot.resolve("http-client.env.json"),
            sharedEnvFile,
            mapOf(
                "__BASE_URL__" to baseUrl,
                "__DEV_FORMAT__" to devFormat,
                "__DEV_ERROR__" to devError,
                "__PROD_FORMAT__" to prodFormat,
                "__PROD_ERROR__" to prodError,
            ),
        )
    }

    fun writePrivateEnv() {
        copyTemplate("http-client.private.env.json", privateEnvFile)
    }

    fun writeAltEnv(
        baseUrl: String,
        altFormat: String,
        altError: String,
    ) {
        writeTemplate(
            templateRoot.resolve("http-client.alt.env.json"),
            altSharedEnvFile,
            mapOf(
                "__BASE_URL__" to baseUrl,
                "__ALT_FORMAT__" to altFormat,
                "__ALT_ERROR__" to altError,
            ),
        )
    }

    fun writeAltPrivateEnv() {
        copyTemplate("http-client.alt.private.env.json", altPrivateEnvFile)
    }

    private fun ensureProjectRoot() {
        Files.createDirectories(projectRoot)
    }

    private fun copyTemplate(
        name: String,
        target: Path,
    ) {
        val source = templateRoot.resolve(name)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun writeTemplate(
        source: Path,
        target: Path,
        replacements: Map<String, String>,
    ) {
        var content = Files.readString(source, StandardCharsets.UTF_8)
        for ((key, value) in replacements) {
            content = content.replace(key, value)
        }
        Files.writeString(target, content, StandardCharsets.UTF_8)
    }
}
