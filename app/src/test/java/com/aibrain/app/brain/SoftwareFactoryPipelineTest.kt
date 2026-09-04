package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoftwareFactoryPipelineTest {
    @Test
    fun plannerCreatesDeterministicTaskContracts() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("app de exemplo"))
        assertEquals(4, plan.workItems.size)
        assertTrue(plan.workItems.all { it.taskId.startsWith("task-app-de-exemplo-") })
        assertTrue(plan.workItems.all { it.acceptanceCriteria.isNotEmpty() })
        assertTrue(ProjectQualityGate.validate(plan).passed)
    }

    @Test
    fun zipIntegrationBlocksConflictingSamePath() {
        val first = createZip("one.txt", "one")
        val second = createZip("one.txt", "two")
        val analysis = ZipIntegrationEngine.analyze(
            listOf(
                ZipIntegrationEngine.Artifact("a", "implementation", "ia1", first),
                ZipIntegrationEngine.Artifact("b", "validation", "ia2", second)
            )
        )
        assertTrue(analysis.conflicts.any { it.type == ZipIntegrationEngine.ConflictType.CROSS_ARTIFACT_MODIFIED })
        assertTrue(!analysis.safe)
    }

    @Test
    fun zipIntegrationRejectsTraversal() {
        val zip = File.createTempFile("unsafe", ".zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escape.txt"))
            out.write("x".toByteArray())
            out.closeEntry()
        }
        val analysis = ZipIntegrationEngine.analyze(
            listOf(ZipIntegrationEngine.Artifact("a", "implementation", "ia1", zip))
        )
        assertTrue(analysis.conflicts.any { it.type == ZipIntegrationEngine.ConflictType.PATH_TRAVERSAL })
        assertTrue(!analysis.safe)
        zip.delete()
    }

    @Test
    fun pipelineBlocksMergeUntilGateAndApproval() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("app de exemplo"))
        val item = plan.workItems.first()
        val pipeline = SoftwareFactoryPipeline()
        pipeline.assign(item, "ia-teste")
        pipeline.markInProgress(item.taskId)
        val zip = createZip("result.txt", "ok")
        val reviewed = pipeline.receiveZip(plan, SoftwareFactoryPipeline.Submission(item.taskId, item.functionId, "ia-teste", zip))
        assertEquals(SoftwareFactoryPipeline.Stage.REVIEWED, reviewed.stage)
        val gated = pipeline.runQualityGate(item.taskId, plan)
        assertEquals(SoftwareFactoryPipeline.Stage.QUALITY_GATE, gated.stage)
        val approved = pipeline.approve(item.taskId)
        assertEquals(SoftwareFactoryPipeline.Stage.APPROVED, approved.stage)
        val merged = pipeline.markMerged(item.taskId)
        assertEquals(SoftwareFactoryPipeline.Stage.MERGED, merged.stage)
        zip.delete()
    }

    @Test
    fun processOptimizerPrefersReliableAi() {
        repeat(5) { ProcessOptimizer.record(ProcessOptimizer.Observation("implementation", "ia-a", true, 1000)) }
        repeat(5) { ProcessOptimizer.record(ProcessOptimizer.Observation("implementation", "ia-b", false, 1000, regressions = 1)) }
        assertEquals("ia-a", ProcessOptimizer.recommend("implementation").first().aiId)
    }

    private fun createZip(path: String, content: String): File {
        val zip = File.createTempFile("artifact", ".zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(path))
            out.write(content.toByteArray())
            out.closeEntry()
        }
        return zip
    }
}
