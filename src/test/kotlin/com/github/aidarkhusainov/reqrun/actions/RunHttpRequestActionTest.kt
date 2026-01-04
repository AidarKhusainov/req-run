package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.services.ReqRunExecutionService
import com.github.aidarkhusainov.reqrun.services.ReqRunRunner
import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.createActionEvent
import com.intellij.notification.NotificationType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class RunHttpRequestActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(ReqRunExecutionService::class.java).clearAll()
    }

    override fun tearDown() {
        try {
            project.getService(ReqRunRunner::class.java).setExecutorForTests(null)
            clearReqRunNotifications(project)
        } finally {
            super.tearDown()
        }
    }

    fun testWarnsOnNonHttpFile() {
        myFixture.configureByText("test.txt", "GET https://example.com")
        val action = RunHttpRequestAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("ReqRun works with .http files", notifications.single().content)
    }

    fun testWarnsWhenNoRequestBlock() {
        myFixture.configureByText("test.http", "")
        val action = RunHttpRequestAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals(
            "Place the caret inside a request block or select text to run.",
            notifications.single().content,
        )
    }

    fun testRunsAndStoresExecution() {
        val runner = project.getService(ReqRunRunner::class.java)
        runner.setExecutorForTests { _, _ ->
            HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = emptyMap<String, List<String>>(),
                body = "ok",
                durationMillis = 1,
            )
        }
        myFixture.configureByText("test.http", "GET https://example.com")
        val action = RunHttpRequestAction()

        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val execService = project.getService(ReqRunExecutionService::class.java)
        waitForExecutionCount(execService, 1)
        val execution = execService.list().single()
        assertNotNull(execution.response)
        assertNull(execution.error)
    }

    fun testWarnsOnUnresolvedVariables() {
        myFixture.configureByText("test.http", "GET {{missing}}/v1")
        val action = RunHttpRequestAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Unresolved variables: missing", notifications.single().content)
    }

    fun testWarnsOnMissingAuthConfig() {
        myFixture.configureByText(
            "test.http",
            """
            GET https://example.com
            Authorization: Bearer {{${'$'}auth.token("bearer")}}
            """.trimIndent(),
        )
        val action = RunHttpRequestAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Missing auth config: bearer", notifications.single().content)
    }

    fun testWarnsOnIncompleteAuthConfig() {
        val basePath = Path.of(project.basePath!!)
        Files.createDirectories(basePath)
        Files.writeString(
            basePath.resolve("http-client.env.json"),
            """
            {
              "local": {
                "Security": {
                  "Auth": {
                    "basic": { "Type": "Static", "Scheme": "Basic", "Username": "user" }
                  }
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        myFixture.configureByText(
            "test.http",
            """
            GET https://example.com
            Authorization: Basic {{${'$'}auth.token("basic")}}
            """.trimIndent(),
        )
        project
            .getService(com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService::class.java)
            .setSelectedEnvironment("local")
        val action = RunHttpRequestAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Auth config 'basic' Password is missing.", notifications.single().content)
    }

    private fun waitForExecutionCount(
        service: ReqRunExecutionService,
        expected: Int,
    ) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (service.list().size >= expected) return
            Thread.sleep(25)
        }
        assertEquals(expected, service.list().size)
    }
}
