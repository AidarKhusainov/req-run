package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.intellij.execution.services.ServiceEventListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.atomic.AtomicInteger

class ReqRunExecutionServiceTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(ReqRunExecutionService::class.java).clearAll()
    }

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

    fun testClearAll() {
        val service = project.getService(ReqRunExecutionService::class.java)
        val request = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )

        repeat(3) {
            service.addExecution(request, null, "boom")
        }
        assertEquals(3, service.list().size)

        val removed = service.clearAll()
        assertEquals(3, removed)
        assertEquals(0, service.list().size)
    }

    fun testHistoryLimit() {
        val service = project.getService(ReqRunExecutionService::class.java)
        val request = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )

        val first = service.addExecution(request, null, "first")
        repeat(205) {
            service.addExecution(request, null, "item-$it")
        }
        val list = service.list()
        assertEquals(200, list.size)
        assertFalse(list.any { it.id == first.id })
    }

    fun testServiceEventsAreDispatched() {
        val service = project.getService(ReqRunExecutionService::class.java)
        val eventCount = AtomicInteger(0)
        project.messageBus.connect(testRootDisposable).subscribe(
            ServiceEventListener.TOPIC,
            ServiceEventListener { eventCount.incrementAndGet() }
        )

        val request = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )
        val exec = service.addExecution(request, null, "boom")
        waitForEventCount(eventCount, 1)

        val removed = service.removeExecution(exec.id)
        assertTrue(removed)
        waitForEventCount(eventCount, 2)

        service.addExecution(request, null, "boom")
        waitForEventCount(eventCount, 3)
        service.clearAll()
        waitForEventCount(eventCount, 4)
    }

    private fun waitForEventCount(counter: AtomicInteger, expected: Int) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            if (counter.get() >= expected) return
            Thread.sleep(25)
        }
        assertEquals(expected, counter.get())
    }
}
