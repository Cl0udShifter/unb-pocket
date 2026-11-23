package com.unbpocket.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class UnbHttpSessionPriorityTest {
    @Test
    fun interactiveRequestsPreemptQueuedBackgroundPreloadWhilePreservingMutualExclusion() = runTest {
        val session = UnbHttpSession.forTesting(setOf("sigaa.unb.br"))
        val inLockCount = AtomicInteger(0)
        val firstInteractiveEntered = CompletableDeferred<Unit>()
        val releaseFirstInteractive = CompletableDeferred<Unit>()
        val executionOrder = mutableListOf<String>()

        val firstInteractive = launch {
            session.withJsfLock(JsfPriority.INTERACTIVE) {
                assertEquals(1, inLockCount.incrementAndGet())
                executionOrder += "interactive-1"
                firstInteractiveEntered.complete(Unit)
                releaseFirstInteractive.await()
                inLockCount.decrementAndGet()
            }
        }

        firstInteractiveEntered.await()

        val queuedBackground = launch {
            session.withJsfLock(JsfPriority.BACKGROUND_PRELOAD) {
                assertEquals(1, inLockCount.incrementAndGet())
                executionOrder += "background-1"
                inLockCount.decrementAndGet()
            }
        }

        testScheduler.runCurrent()
        assertFalse("Background work must wait while interactive holds lock", executionOrder.contains("background-1"))

        val preemptingInteractive = launch {
            session.withJsfLock(JsfPriority.INTERACTIVE) {
                assertEquals(1, inLockCount.incrementAndGet())
                executionOrder += "interactive-2"
                inLockCount.decrementAndGet()
            }
        }

        testScheduler.runCurrent()
        releaseFirstInteractive.complete(Unit)

        firstInteractive.join()
        preemptingInteractive.join()
        queuedBackground.join()

        assertEquals(
            listOf("interactive-1", "interactive-2", "background-1"),
            executionOrder,
        )
    }
}
