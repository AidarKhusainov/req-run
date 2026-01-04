package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.model.RequestBodySpec
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReqRunRunnerTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            project.getService(ReqRunRunner::class.java).setExecutorForTests(null)
        } finally {
            super.tearDown()
        }
    }

    fun testRunSuccess() {
        val runner = project.getService(ReqRunRunner::class.java)
        runner.setExecutorForTests { _, _ ->
            HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = emptyMap<String, List<String>>(),
                body = "ok",
                durationMillis = 1,
            )
        }
        val spec = requestSpec()

        val result = runner.run(spec, null, null)

        assertEquals(ReqRunRunner.Status.SUCCESS, result.status)
        assertNotNull(result.execution.response)
        assertNull(result.execution.error)
    }

    fun testRunCancelled() {
        val runner = project.getService(ReqRunRunner::class.java)
        runner.setExecutorForTests { _, _ -> throw ProcessCanceledException() }
        val spec = requestSpec()

        val ex =
            assertThrows(ProcessCanceledException::class.java) {
                runner.run(spec, null, null)
            }
        assertNotNull(ex)
    }

    fun testRunError() {
        val runner = project.getService(ReqRunRunner::class.java)
        runner.setExecutorForTests { _, _ -> throw IllegalStateException("boom") }
        val spec = requestSpec()

        val result = runner.run(spec, null, null)

        assertEquals(ReqRunRunner.Status.ERROR, result.status)
        assertNull(result.execution.response)
        assertEquals("boom", result.execution.error)
    }

    private fun requestSpec(): HttpRequestSpec =
        HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap<String, String>(),
            body = null as RequestBodySpec?,
        )
}
