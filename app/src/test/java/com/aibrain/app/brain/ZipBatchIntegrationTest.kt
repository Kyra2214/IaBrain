package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBatchIntegrationTest {
    @Test
    fun twoIndependentZipsCanBeIntegratedAsOneSnapshot() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("app integrado"))
        val pipeline = SoftwareFactoryPipeline()
        val first = plan.workItems[0]
        val second = plan.workItems[1]
        pipeline.assign(first, "ia-1")
        pipeline.assign(second, "ia-2")
        pipeline.markInProgress(first.taskId)
        pipeline.markInProgress(second.taskId)
        val a = zip("analysis.txt", "a")
        val b = zip("architecture.txt", "b")
        val states = pipeline.integrateBatch(
            plan,
            listOf(
                SoftwareFactoryPipeline.Submission(first.taskId, first.functionId, "ia-1", a),
                SoftwareFactoryPipeline.Submission(second.taskId, second.functionId, "ia-2", b)
            )
        )
        assertEquals(2, states.count { it.stage == SoftwareFactoryPipeline.Stage.INTEGRATED })
        assertTrue(states.all { it.review?.approved == true })
        a.delete()
        b.delete()
    }

    @Test
    fun conflictingZipsBlockTheWholeSnapshot() {
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("app integrado"))
        val pipeline = SoftwareFactoryPipeline()
        val first = plan.workItems[0]
        val second = plan.workItems[1]
        pipeline.assign(first, "ia-1")
        pipeline.assign(second, "ia-2")
        pipeline.markInProgress(first.taskId)
        pipeline.markInProgress(second.taskId)
        val a = zip("shared.txt", "a")
        val b = zip("shared.txt", "b")
        val states = pipeline.integrateBatch(
            plan,
            listOf(
                SoftwareFactoryPipeline.Submission(first.taskId, first.functionId, "ia-1", a),
                SoftwareFactoryPipeline.Submission(second.taskId, second.functionId, "ia-2", b)
            )
        )
        assertTrue(states.all { it.stage == SoftwareFactoryPipeline.Stage.BLOCKED })
        a.delete()
        b.delete()
    }

    private fun zip(path: String, content: String): File {
        val file = File.createTempFile("factory", ".zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(path))
            out.write(content.toByteArray())
            out.closeEntry()
        }
        return file
    }
}
