package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReqRunExecutionServiceTest : BasePlatformTestCase() {
    fun testAddListRemoveExecution() {
        val service = project.getService(ReqRunExecutionService::class.java)
        assertEquals(0, service.list().size)

        val request = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )

        val exec = service.addExecution(request, null, "boom")
        val listAfterAdd = service.list()
        assertEquals(1, listAfterAdd.size)
        assertEquals(exec, listAfterAdd.first())

        val removed = service.removeExecution(exec.id)
        assertTrue(removed)
        assertEquals(0, service.list().size)

        val removedMissing = service.removeExecution(exec.id)
        assertFalse(removedMissing)
    }
}
