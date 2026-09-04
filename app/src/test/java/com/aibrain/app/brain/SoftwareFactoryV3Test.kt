package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoftwareFactoryV3Test {

    @Test
    fun inspectZip_isDeterministic_andRejectsTraversal() {
        val zip = File.createTempFile("iabrain-factory", ".zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("src/Main.kt"))
            out.write("fun hello() = 1".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("../evil.txt"))
            out.write("blocked".toByteArray())
            out.closeEntry()
        }
        val snapshot = SoftwareFactoryV3.inspectZip(zip.absolutePath, "a1", "task-1")
        assertEquals(listOf("src/Main.kt"), snapshot.files.map { it.path })
        assertFalse(snapshot.safe)
        assertTrue(snapshot.findings.any { it.contains("Path traversal") })
        zip.delete()
    }

    @Test
    fun mergePlan_blocks_divergent_samePath() {
        val a = SoftwareFactoryV3.ZipSnapshot(
            "a", "t", listOf(SoftwareFactoryV3.ZipFileInfo("A.kt", 1, "111", "Kotlin", false)),
            listOf("A.kt"), emptyList(), emptyList(), emptyList(), true
        )
        val b = a.copy(artifactId = "b", files = listOf(a.files.first().copy(sha256 = "222")))
        val plan = SoftwareFactoryV3.planMerge(listOf(a, b))
        assertFalse(plan.safeToMerge)
        assertTrue(plan.rollbackRequired)
        assertEquals(1, plan.conflicts.size)
    }

    @Test
    fun coordinator_requires_validation_before_pr_and_supports_bounded_repair() {
        val coordinator = SoftwareFactoryCoordinator(maxRepairCycles = 2)
        var run = SoftwareFactoryCoordinator.Run("p1", "objective")
        run = coordinator.receiveArtifacts(run, 2)
        run = coordinator.integrate(run, true)
        run = coordinator.startValidation(run)
        run = coordinator.validationFailed(run, "regression")
        assertEquals(1, run.repairCycles)
        run = coordinator.repairSubmitted(run, 1)
        run = coordinator.integrate(run, true)
        run = coordinator.startValidation(run)
        run = coordinator.validationPassed(run)
        run = coordinator.mergeApproved(run)
        assertEquals(SoftwareFactoryCoordinator.State.MERGED, run.state)
    }

    @Test
    fun router_and_memory_are_stable() {
        val profiles = listOf(
            SoftwareFactoryV3.AiProfile("slow-good", "CODE", 10, 1, 0, 30.0, .95),
            SoftwareFactoryV3.AiProfile("fast-average", "CODE", 8, 2, 1, 5.0, .75)
        )
        assertEquals("slow-good", SoftwareFactoryV3.rankAIs("CODE", profiles).first().aiId)
        val memory = SoftwareFactoryV3.MemorySnapshot(emptySet(), emptyList(), emptySet(), emptySet(), emptyList(), profiles)
        val updated = SoftwareFactoryV3.updateMemory(memory, SoftwareFactoryV3.Review(90, emptyList(), true), emptyList())
        assertTrue(updated.decisions.last().contains("review-score=90"))
    }
}
