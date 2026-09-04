package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoftwareFactoryRuntimeV31Test {
    @Test
    fun classifyTestSeparatesDependencyEnvironmentAndCode() {
        val runtime = SoftwareFactoryRuntimeV31()
        assertEquals(SoftwareFactoryRuntimeV31.TestOutcome.DEPENDENCY_FAILURE, runtime.classifyTest("build", 1, "Could not resolve kotlin-stdlib").outcome)
        assertEquals(SoftwareFactoryRuntimeV31.TestOutcome.ENVIRONMENT_FAILURE, runtime.classifyTest("test", 1, "adb device offline").outcome)
        assertEquals(SoftwareFactoryRuntimeV31.TestOutcome.CODE_FAILURE, runtime.classifyTest("test", 1, "AssertionError: expected true").outcome)
    }

    @Test
    fun repairCycleStopsAtConfiguredLimitAndExtractsLocation() {
        val runtime = SoftwareFactoryRuntimeV31(SoftwareFactoryRuntimeV31.Limits(maxRepairAttempts = 2))
        val first = runtime.repairCycle("p1", "AssertionError at src/A.kt:42 in A.run", 1)
        val third = runtime.repairCycle("p1", "AssertionError at src/A.kt:42 in A.run", 3)
        assertTrue(first.allowed)
        assertEquals(42, first.task.diagnosis.location.line)
        assertEquals("src/A.kt", first.task.diagnosis.location.path)
        assertTrue(!third.allowed)
    }

    @Test
    fun contractGraphExposesConsumersAndImports() {
        val runtime = SoftwareFactoryRuntimeV31()
        val graph = runtime.contractGraph(mapOf(
            "Api.kt" to "import x.Dep\ninterface Api { fun run(): String }",
            "Consumer.kt" to "class Consumer { fun use(api: Api) { api.run() } }"
        ))
        val api = graph.nodes.first { it.symbol.name == "Api" }
        assertTrue(api.symbol.dependencies.contains("Dep"))
        assertTrue(api.consumers.contains("Consumer.kt"))
    }

    @Test
    fun mergeMaterializesOwnedFilesAndStoreKeepsEvidence() {
        val root = createTempDir(prefix = "v31-workspace")
        val zip = zip("src/A.kt", "class A")
        val artifact = ZipIntegrationEngine.Artifact("a", "implementation", "ia", zip)
        val analysis = ZipIntegrationEngine.analyze(listOf(artifact))
        val runtime = SoftwareFactoryRuntimeV31()
        val result = runtime.merge(root, analysis, listOf(artifact), mapOf("implementation" to setOf("src/A.kt")))
        assertTrue(result.snapshot != null)
        assertTrue(File(root, "src/A.kt").isFile)
        val store = SoftwareFactoryRuntimeV31.Store()
        store.save(result.snapshot!!)
        assertEquals(1, store.size())
        zip.delete()
        root.deleteRecursively()
    }

    @Test
    fun failedMaterializationExecutesPhysicalRollback() {
        val root = createTempDir(prefix = "v31-rollback")
        File(root, "keep.txt").writeText("before")
        File(root, "src").writeText("not-a-directory")
        val zip = zip("src/A.kt", "class A")
        val artifact = ZipIntegrationEngine.Artifact("a", "implementation", "ia", zip)
        val runtime = SoftwareFactoryRuntimeV31()
        val result = runtime.merge(root, ZipIntegrationEngine.analyze(listOf(artifact)), listOf(artifact), emptyMap())
        assertTrue(result.rolledBack)
        assertEquals("before", File(root, "keep.txt").readText())
        assertTrue(File(root, "src").isFile)
        zip.delete()
        root.deleteRecursively()
    }

    @Test
    fun baseModifiedPolicyBlocksByDefaultAndAllowsOnlyExplicitOverride() {
        val root = createTempDir(prefix = "v31-base")
        val zip = zip("A.kt", "class A")
        val artifact = ZipIntegrationEngine.Artifact("a", "implementation", "ia", zip)
        val analysis = ZipIntegrationEngine.analyze(listOf(artifact), mapOf("A.kt" to "different"))
        val runtime = SoftwareFactoryRuntimeV31()
        assertTrue(runtime.merge(root, analysis, listOf(artifact), mapOf("implementation" to setOf("A.kt"))).snapshot != null)
        val allowed = runtime.merge(root, analysis, listOf(artifact), mapOf("implementation" to setOf("A.kt")), baseModifiedPolicy = SoftwareFactoryRuntimeV31.BaseModifiedPolicy.ALLOW)
        assertEquals(SoftwareFactoryIntelligenceV31.MergeDecision.SAFE_MERGE, allowed.assessment.decision)
        zip.delete()
        root.deleteRecursively()
    }

    @Test
    fun persistentStoreSurvivesNewInstanceAndPipelineEmitsProjectId() {
        val journal = File.createTempFile("v31-store", ".log")
        val first = SoftwareFactoryRuntimeV31.Store(journal)
        val report = SoftwareFactoryRuntimeV31.TestReport("test", 0, "ok", SoftwareFactoryRuntimeV31.TestOutcome.PASSED)
        first.save("report-1", report)
        val second = SoftwareFactoryRuntimeV31.Store(journal)
        assertTrue(second.contains("report", "report-1"))
        assertEquals(1, second.size())
        FactoryTelemetry.clear()
        val plan = ProjectWorkPlanner.build(ProjectBuilder.build("pipeline"))
        val workspace = createTempDir(prefix = "v31-pipeline")
        val pipeline = SoftwareFactoryPipeline(runtime = SoftwareFactoryRuntimeV31(), projectId = "project-42", workspace = workspace)
        val item = plan.workItems.first()
        val zip = zip("analysis.txt", "ok")
        pipeline.assign(item, "ia")
        pipeline.markInProgress(item.taskId)
        pipeline.receiveZip(plan, SoftwareFactoryPipeline.Submission(item.taskId, item.functionId, "ia", zip), declaredFilesByFunction = mapOf(item.functionId to setOf("analysis.txt")))
        assertTrue(File(workspace, "analysis.txt").isFile)
        assertTrue(FactoryTelemetry.forProject("project-42").isNotEmpty())
        zip.delete()
        workspace.deleteRecursively()
        journal.delete()
    }

    private fun zip(path: String, content: String): File {
        val file = File.createTempFile("runtime-v31", ".zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(path))
            out.write(content.toByteArray())
            out.closeEntry()
        }
        return file
    }
}
