package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerIntelligenceTest {
    private val pipeline = ExplorerIntelligencePipeline()

    private fun candidate(
        id: String,
        name: String,
        region: ExplorerRegion = ExplorerRegion.CHINA,
        capabilities: Set<String> = setOf("agent", "coding"),
        firstSeen: Long = NOW - 2 * DAY,
        confidence: Double = .9,
        sourcePriority: Int = 90,
        openSource: OpenSourceStatus = OpenSourceStatus.OPEN_WEIGHT,
        license: ExplorerLicense = ExplorerLicense.MIT,
        licenseVerified: Boolean = true
    ) = ExplorerCandidate(
        id = id,
        name = name,
        type = ExplorerItemType.AI,
        region = region,
        officialUrl = "https://$id.example.com",
        description = "AI agent for coding and automation",
        capabilities = capabilities,
        openSource = openSource,
        license = license,
        licenseVerified = licenseVerified,
        firstSeenEpochMs = firstSeen,
        sourcePriority = sourcePriority,
        confidence = confidence
    )

    @Test
    fun phase1_chinaPriorityIsVisible() {
        val china = candidate("china", "China AI")
        val global = candidate("global", "Global AI", ExplorerRegion.UNITED_STATES)
        val ranked = pipeline.rankRadar(listOf(global, china), NOW)
        assertEquals(ExplorerRegion.CHINA, ranked.first().candidate.region)
        assertTrue(ranked.first().reasons.contains("prioridade do radar chinês"))
    }

    @Test
    fun phase2_duplicateOfficialUrlAndNameCollapses() {
        val a = candidate("same-a", "Same", firstSeen = NOW - DAY)
        val b = a.copy(id = "same-b")
        assertEquals(1, pipeline.rankRadar(listOf(a, b), NOW).size)
    }

    @Test
    fun phase3_privateOrCredentialUrlIsBlocked() {
        val unsafe = candidate("unsafe", "Unsafe").copy(officialUrl = "https://user:secret@localhost/tool")
        val result = pipeline.validateSecurity(unsafe)
        assertFalse(result.safe)
        assertTrue(result.blockers.isNotEmpty())
    }

    @Test
    fun phase4_unknownLicenseRequiresReview() {
        val unknown = candidate("unknown-license", "Unknown License", license = ExplorerLicense.UNKNOWN, licenseVerified = false)
        assertEquals(ReuseDecision.REVIEW_REQUIRED, pipeline.licenseReview(unknown).decision)
    }

    @Test
    fun phase5_workspaceOpensAndMinimizes() {
        val opened = pipeline.openWindow(WorkspaceState(), candidate("workspace", "Workspace AI"))
        assertEquals("explorer-workspace", opened.activeWindowId)
        val minimized = pipeline.minimizeWindow(opened, "explorer-workspace")
        assertEquals(WorkspaceWindowState.MINIMIZED, minimized.windows.single().state)
    }

    @Test
    fun phase6_browserIsFirstChannel() {
        val decision = pipeline.chooseChannel(
            ConnectorProfile("kimi", setOf(ExplorerChannel.API, ExplorerChannel.BROWSER), setOf("agent"), apiAvailable = true, browserAvailable = true)
        )
        assertEquals(ExplorerChannel.BROWSER, decision.selected)
        assertTrue(decision.alternatives.contains(ExplorerChannel.API))
    }

    @Test
    fun phase7_evaluationKeepsEvidence() {
        val evaluation = pipeline.evaluate("model", "coding task", 8.5, 1200, "measured test run")
        assertEquals(8.5, evaluation.score, 0.0)
        assertEquals(1200L, evaluation.latencyMs)
        assertEquals("measured test run", evaluation.evidence)
    }

    @Test
    fun phase8_closedProjectCannotBeReused() {
        val closed = candidate("closed", "Closed", openSource = OpenSourceStatus.CLOSED)
        assertEquals(ReuseDecision.REJECT, pipeline.licenseReview(closed).decision)
    }

    @Test
    fun phase9_brainHandoffCarriesOnlyKnownFacts() {
        val result = pipeline.run(
            NOW,
            listOf(ExplorerSource("official", "Official", ExplorerRegion.CHINA, 90, true)),
            listOf(candidate("handoff", "Handoff AI"))
        )
        val handoff = result.brainHandoffs.single()
        assertEquals("Handoff AI", handoff.facts["name"])
        assertEquals(ExplorerRegion.CHINA.name, handoff.facts["region"])
        assertTrue(handoff.capabilities.contains("agent"))
    }

    @Test
    fun phase10_weeklyRadarIsDeterministic() {
        val result = pipeline.run(
            NOW,
            listOf(ExplorerSource("official", "Official", ExplorerRegion.CHINA, 90, true)),
            listOf(candidate("weekly", "Weekly AI"))
        )
        assertEquals(ExplorerIntelligencePipeline.weekKey(NOW), result.radar.weekKey)
        assertEquals(1, result.radar.discovered)
        assertEquals(1, result.radar.accepted)
    }

    companion object {
        private const val DAY = 86_400_000L
        private const val NOW = 1_800_000_000_000L
    }
}
