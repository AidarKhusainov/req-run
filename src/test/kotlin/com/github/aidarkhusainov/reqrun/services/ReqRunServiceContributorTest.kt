package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReqRunServiceContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(ReqRunExecutionService::class.java).clearAll()
    }

    fun testDescriptorIsCachedAndEvicted() {
        val service = project.getService(ReqRunExecutionService::class.java)
        val exec = service.addExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload("HTTP/1.1 200 OK", emptyMap(), "ok", 1),
            error = null
        )
        val contributor = ReqRunServiceContributor()

        val first = contributor.getServiceDescriptor(project, exec)
        val second = contributor.getServiceDescriptor(project, exec)
        assertSame(first, second)

        ReqRunServiceContributor.evict(project, listOf(exec.id))
        val third = contributor.getServiceDescriptor(project, exec)
        assertNotSame(first, third)
    }

    fun testDescriptorProvidesNavigatableAndRemover() {
        val file = myFixture.configureByText("test.http", "GET https://example.com").virtualFile
        val source = ReqRunRequestSource(file, 0)
        val service = project.getService(ReqRunExecutionService::class.java)
        val exec = service.addExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = null,
            error = "boom",
            source = source
        )
        val contributor = ReqRunServiceContributor()
        val descriptor = contributor.getServiceDescriptor(project, exec)

        val navigatable = descriptor.getNavigatable()
        assertNotNull(navigatable)
        assertTrue(navigatable is OpenFileDescriptor)

        descriptor.getRemover()?.run()
        assertTrue(service.list().isEmpty())
    }
}
