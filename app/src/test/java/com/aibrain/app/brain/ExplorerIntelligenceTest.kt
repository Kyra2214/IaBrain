package com.aibrain.app.brain

class ExplorerIntelligenceTest {
    private val pipeline = ExplorerIntelligencePipeline()

    private fun candidate(
        id: String,
        name: String,
        region: ExplorerRegion = ExplorerRegion.CHINA,
        type: ExplorerItemType = ExplorerItemType.AI,
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
        type = type,
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

    fun phase1_chinaPriorityIsVisible() {
        val china = candidate("china", "China AI")
        val global = candidate("global", "Global AI", ExplorerRegion.UNITED_STATES)
        val ranked = pipeline.rankRadar(listOf(global, china), NOW)
        check(ranked.first().candidate.region == ExplorerRegion.CHINA)
        check(ranked.first().reasons.contains("prioridade do radar chinês"))
    }

    fun phase2_duplicateOfficialUrlAndNameCollapses() {
        val a = candidate("same-a", "Same", firstSeen = NOW - DAY)
        val b = a.copy(id = "same-b")
        val ranked = pipeline.rankRadar(listOf(a, b), NOW)
        check(ranked.size == 1)
    }

    fun phase3_privateOrCredentialUrlIsBlocked() {
        val unsafe = candidate("unsafe", "Unsafe").copy(officialUrl = "https://user:secret@localhost/tool")
        val result = pipeline.validateSecurity(unsafe)
        check(!result.safe)
        check(result.blockers.isNotEmpty())
    }

    fun phase4_unknownLicenseRequiresReview() {
        val unknown = candidate("unknown-license", "Unknown License", license = ExplorerLicense.UNKNOWN, licenseVerified = false)
        val result = pipeline.licenseReview(unknown)
        check(result.decision == ReuseDecision.REVIEW_REQUIRED)
    }

    fun phase5_workspaceOpensAndMinimizes() {
        val ai = candidate("workspace", "Workspace AI")
        val opened = pipeline.openWindow(WorkspaceState(), ai)
        check(opened.activeWindowId == "explorer-workspace")
        val minimized = pipeline.minimizeWindow(opened, "explorer-workspace")
        check(minimized.windows.single().state == WorkspaceWindowState.MINIMIZED)
    }

    fun phase6_browserIsFirstChannel() {
        val decision = pipeline.chooseChannel(
            ConnectorProfile("kimi", setOf(ExplorerChannel.API, ExplorerChannel.BROWSER), setOf("agent"), apiAvailable = true, browserAvailable = true)
        )
        check(decision.selected == ExplorerChannel.BROWSER)
        check(decision.alternatives.contains(ExplorerChannel.API))
    }

    fun phase7_evaluationKeepsEvidence() {
        val evaluation = pipeline.evaluate("model", "coding task", 8.5, 1200, "measured test run")
        check(evaluation.score == 8.5)
        check(evaluation.latencyMs == 1200L)
        check(evaluation.evidence == "measured test run")
    }

    fun phase8_closedProjectCannotBeReused() {
        val closed = candidate("closed", "Closed", openSource = OpenSourceStatus.CLOSED)
        check(pipeline.licenseReview(closed).decision == ReuseDecision.REJECT)
    }

    fun phase9_brainHandoffCarriesOnlyKnownFacts() {
        val result = pipeline.run(NOW, listOf(ExplorerSource("official", "Official", ExplorerRegion.CHINA, 90, true)), listOf(candidate("handoff", "Handoff AI")))
        val handoff = result.brainHandoffs.single()
        check(handoff.facts["name"] == "Handoff AI")
        check(handoff.facts["region"] == ExplorerRegion.CHINA.name)
        check(handoff.capabilities.contains("agent"))
    }

    fun phase10_weeklyRadarIsDeterministic() {
        val result = pipeline.run(
            weekEpochMs = NOW,
            sources = listOf(ExplorerSource("official", "Official", ExplorerRegion.CHINA, 90, true)),
            candidates = listOf(candidate("weekly", "Weekly AI"))
        )
        check(result.radar.weekKey == ExplorerIntelligencePipeline.weekKey(NOW))
        check(result.radar.discovered == 1)
        check(result.radar.accepted == 1)
    }

    fun allPhasesPass() {
        phase1_chinaPriorityIsVisible()
        phase2_duplicateOfficialUrlAndNameCollapses()
        phase3_privateOrCredentialUrlIsBlocked()
        phase4_unknownLicenseRequiresReview()
        phase5_workspaceOpensAndMinimizes()
        phase6_browserIsFirstChannel()
        phase7_evaluationKeepsEvidence()
        phase8_closedProjectCannotBeReused()
        phase9_brainHandoffCarriesOnlyKnownFacts()
        phase10_weeklyRadarIsDeterministic()
    }

    companion object {
        private const val DAY = 86_400_000L
        private const val NOW = 1_800_000_000_000L
    }
}
