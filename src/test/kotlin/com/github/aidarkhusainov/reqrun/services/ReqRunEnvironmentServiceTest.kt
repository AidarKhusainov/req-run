package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.core.AuthScheme
import com.github.aidarkhusainov.reqrun.core.AuthType
import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.intellij.notification.NotificationType
import com.intellij.testFramework.PlatformTestUtil
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
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            basePath.resolve("http-client.private.env.json"),
            """
            {
              "local": { "token": "private", "secret": 42 },
              "dev": { "flag": true }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
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
            StandardCharsets.UTF_8,
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
            notifications.single().content,
        )
    }

    fun testLoadsAuthConfigsFromSecuritySection() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            """
            {
              "local": {
                "baseUrl": "https://shared",
                "Security": {
                  "Auth": {
                    "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "{{token}}" },
                    "basic": { "Type": "Static", "Scheme": "Basic", "Username": "user", "Password": "pass" }
                  }
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            basePath.resolve("http-client.private.env.json"),
            """
            {
              "local": {
                "Security": {
                  "Auth": {
                    "bearer": { "Type": "Static", "Scheme": "Bearer", "Token": "private" },
                    "api": { "Type": "Static", "Scheme": "ApiKey", "Token": "secret" }
                  }
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val file = myFixture.configureByText("test.http", "GET {{baseUrl}}").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        service.setSelectedEnvironment("local")
        val configs = service.loadAuthConfigsForFile(file)

        val bearer = configs["bearer"]
        assertEquals(AuthType.STATIC, bearer?.type)
        assertEquals(AuthScheme.BEARER, bearer?.scheme)
        assertEquals("private", bearer?.token)
        val basic = configs["basic"]
        assertEquals(AuthScheme.BASIC, basic?.scheme)
        assertEquals("user", basic?.username)
        val api = configs["api"]
        assertEquals(AuthScheme.API_KEY, api?.scheme)
        assertEquals("secret", api?.token)
    }

    fun testParsesApiKeySchemeVariants() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            """
            {
              "local": {
                "Security": {
                  "Auth": {
                    "api1": { "Type": "Static", "Scheme": "ApiKey", "Token": "a" },
                    "api2": { "Type": "Static", "Scheme": "api-key", "Token": "b" },
                    "api3": { "Type": "Static", "Scheme": "API_KEY", "Token": "c" }
                  }
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        service.setSelectedEnvironment("local")
        val configs = service.loadAuthConfigsForFile(file)

        assertEquals(AuthScheme.API_KEY, configs["api1"]?.scheme)
        assertEquals(AuthScheme.API_KEY, configs["api2"]?.scheme)
        assertEquals(AuthScheme.API_KEY, configs["api3"]?.scheme)
    }

    fun testNoGitignoreWarningWhenAlreadyIgnored() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve(".gitignore"),
            "http-client.private.env.json\n",
            StandardCharsets.UTF_8,
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        clearReqRunNotifications(project)
        service.getEnvironmentNames(file)

        val notifications = collectReqRunNotifications(project)
        assertTrue(notifications.isEmpty())
    }

    fun testWarnsOnInvalidEnvJson() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            "{ invalid json }",
            StandardCharsets.UTF_8,
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        clearReqRunNotifications(project)
        service.getEnvironmentNames(file)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals(
            "Failed to parse http-client.env.json. Check JSON syntax.",
            notifications.single().content,
        )
    }

    fun testWarnsOnAuthConfigConflict() {
        val basePath = projectBasePath()
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            """
            {
              "local": {
                "Security": {
                  "Auth": {
                    "basic": {
                      "Type": "Static",
                      "Scheme": "Basic",
                      "Token": "abc",
                      "Username": "user"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val service = project.getService(ReqRunEnvironmentService::class.java)

        service.setSelectedEnvironment("local")
        clearReqRunNotifications(project)
        service.loadAuthConfigsForFile(file)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals(
            "Auth config 'basic' in env 'local' (http-client.env.json) mixes Token with Username/Password.",
            notifications.single().content,
        )
    }

    private fun projectBasePath(): Path {
        val basePath = Path.of(project.basePath!!)
        Files.createDirectories(basePath)
        return basePath
    }
}
