package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoftwareFactoryIntelligenceV31Test {
    @Test
    fun ownershipProducesSafeOrAutoMergeOnlyWhenEveryPathHasOneOwner() {
        val zip = zip("src/A.kt", "class A")
        val analysis = ZipIntegrationEngine.analyze(listOf(ZipIntegrationEngine.Artifact("a", "implementation", "ia", zip)))
        val declared = mapOf("implementation" to setOf("src/A.kt"))
        assertEquals(SoftwareFactoryIntelligenceV31.MergeDecision.SAFE_MERGE, SoftwareFactoryIntelligenceV31.assessMerge(analysis, declared).decision)
        assertEquals(SoftwareFactoryIntelligenceV31.MergeDecision.AUTO_MERGE, SoftwareFactoryIntelligenceV31.assessMerge(analysis, declared, true).decision)
        zip.delete()
    }

    @Test
    fun unownedPathRequiresHumanAiReview() {
        val zip = zip("src/A.kt", "class A")
        val analysis = ZipIntegrationEngine.analyze(listOf(ZipIntegrationEngine.Artifact("a", "implementation", "ia", zip)))
        val result = SoftwareFactoryIntelligenceV31.assessMerge(analysis, emptyMap())
        assertEquals(SoftwareFactoryIntelligenceV31.MergeDecision.HUMAN_AI_REVIEW_REQUIRED, result.decision)
        assertEquals(listOf("src/A.kt"), result.unownedPaths)
        zip.delete()
    }

    @Test
    fun contractComparisonFindsSignatureBreakAndDependencyImports() {
        val base = SoftwareFactoryIntelligenceV31.analyzeContracts(mapOf("A.kt" to "import x.Y\nclass A { fun run(value: String): Int = 1 }"))
        val current = SoftwareFactoryIntelligenceV31.analyzeContracts(mapOf("A.kt" to "import x.Y\nclass A { fun run(value: Long): Int = 1 }"))
        val changes = SoftwareFactoryIntelligenceV31.compareContracts(base, current)
        assertTrue(base.single { it.name == "A" }.dependencies.contains("Y"))
        assertTrue(changes.any { it.kind == SoftwareFactoryIntelligenceV31.SymbolKind.FUNCTION && it.breaking })
    }

    @Test
    fun diagnosisClassifiesDependencyAndStopsAfterMaximumAttempts() {
        val diagnosis = SoftwareFactoryIntelligenceV31.diagnoseFailure("Could not resolve kotlin-stdlib; Received status code 403")
        assertEquals(SoftwareFactoryIntelligenceV31.FailureClass.DEPENDENCY, diagnosis.category)
        val first = SoftwareFactoryIntelligenceV31.planRepair(diagnosis, attempt = 1, maxAttempts = 3)
        val last = SoftwareFactoryIntelligenceV31.planRepair(diagnosis, attempt = 3, maxAttempts = 3)
        assertTrue(first.allowed)
        assertFalse(last.allowed)
    }

    private fun zip(path: String, content: String): File {
        val file = File.createTempFile("v31", ".zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(path))
            out.write(content.toByteArray())
            out.closeEntry()
        }
        return file
    }
}
