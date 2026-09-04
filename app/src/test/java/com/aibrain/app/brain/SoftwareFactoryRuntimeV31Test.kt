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
