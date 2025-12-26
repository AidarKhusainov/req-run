package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.intellij.notification.NotificationType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ReqRunEnvironmentServiceTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            clearReqRunNotifications(project)
        } finally {
            super.tearDown()
        }
    }

    fun testMergesSharedAndPrivateEnvironments() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            """
                {
                  "local": { "baseUrl": "https://shared", "token": "shared" },
                  "stage": { "baseUrl": "https://stage" }
                }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        Files.writeString(
            basePath.resolve("http-client.private.env.json"),
            """
                {
                  "local": { "token": "private", "secret": 42 },
                  "dev": { "flag": true }
                }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        val file = myFixture.configureByText("test.http", "GET {{baseUrl}}").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        service.setSelectedEnvironment("local")
        val vars = service.loadVariablesForFile(file)

        assertEquals("https://shared", vars["baseUrl"])
        assertEquals("private", vars["token"])
        assertEquals("42", vars["secret"])

        val names = service.getEnvironmentNames(file)
        assertEquals(listOf("dev", "local", "stage"), names)
    }

    fun testSuggestsGitignoreForPrivateEnv() {
        val basePath = projectBasePath()
        Files.writeString(basePath.resolve(".gitignore"), "build/\n", StandardCharsets.UTF_8)
        Files.writeString(
            basePath.resolve("http-client.private.env.json"),
            "{ \"local\": {} }\n",
            StandardCharsets.UTF_8
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        clearReqRunNotifications(project)
        service.getEnvironmentNames(file)

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals(
            "Consider adding http-client.private.env.json to .gitignore",
            notifications.single().content
        )
    }

    fun testNoGitignoreWarningWhenAlreadyIgnored() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve(".gitignore"),
            "http-client.private.env.json\n",
            StandardCharsets.UTF_8
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        clearReqRunNotifications(project)
        service.getEnvironmentNames(file)

        val notifications = collectReqRunNotifications(project)
        assertTrue(notifications.isEmpty())
    }

    private fun projectBasePath(): Path {
        val basePath = Path.of(project.basePath!!)
        Files.createDirectories(basePath)
        return basePath
    }
}
