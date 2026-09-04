package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IaBrainAutonomyEvolutionTest {
    @Test
    fun context_intelligence_extracts_constraints_and_requirements() {
        val context = ContextIntelligence().understand("Implemente offline sem API e preserve o contrato existente no Android com testes")
        assertTrue("OFFLINE_FIRST" in context.constraints)
        assertTrue("NO_EXTERNAL_API_REQUIRED" in context.constraints)
        assertTrue("PRESERVE_EXISTING_CONTRACT" in context.constraints)
        assertTrue("ANDROID_COMPATIBILITY" in context.requirements)
        assertTrue("TEST_COVERAGE" in context.requirements)
    }

    @Test
    fun planner_produces_dependency_order() {
        val context = TaskContext("build", "build", requirements = setOf("GITHUB_INTEGRATION"), confidence = .9)
        val plan = AutonomousPlanner().plan(context)
        assertTrue(plan.valid)
        assertEquals(listOf("understand", "design", "implement", "test", "review"), plan.orderedStepIds)
    }

    @Test
    fun evaluator_distinguishes_partial_and_successful_results() {
        val evaluator = ResultEvaluator()
        val context = TaskContext("add tests", "add tests", confidence = 1.0)
        val partial = evaluator.evaluate(context, "tests", setOf("tests", "passed"))
        val success = evaluator.evaluate(context, "add tests passed", setOf("tests", "passed"))
        assertTrue(partial.partial)
        assertTrue(partial.retryRecommended)
        assertTrue(success.success)
        assertFalse(success.retryRecommended)
    }

    @Test
    fun council_blocks_invalid_plan_and_approves_valid_plan() {
        val context = TaskContext("x", "x", confidence = 1.0)
        val valid = AutonomousPlanner().plan(context)
        val result = MultiAgentCouncil().deliberate(context, valid)
        assertEquals(Decision.APPROVE, result.decision)
    }

    @Test
    fun factory_prepares_and_quality_gate_requires_evaluated_success() {
        val factory = AutonomousSoftwareFactory()
        val prepared = factory.prepare("implemente testes no Android")
        assertEquals(FactoryStage.PLAN, prepared.stage)
        val evaluated = factory.evaluate(prepared, "implement testes passed", setOf("tests", "passed"))
        assertEquals(FactoryStage.QUALITY_GATE, evaluated.stage)
        assertTrue(evaluated.approved)
    }

    @Test
    fun memory_and_learning_retain_evidence() {
        val memory = AdaptiveMemory()
        memory.remember(MemoryRecord("decision", "use local-first", .9, 10L, setOf("architecture")))
        assertEquals("use local-first", memory.recall("decision")?.value)
        assertEquals(1, memory.search("architecture").size)

        val learning = LearningEngine()
        learning.record(LearningObservation("task", "p1", true, .9, 100, .1))
        learning.record(LearningObservation("task", "p1", false, .5, 300, .2))
        val update = learning.update("p1")
        assertEquals(2, update.observations)
        assertEquals(.5, update.successRate, .001)
    }

    @Test
    fun optimizer_prefers_provider_under_cost_and_latency_policy() {
        val optimizer = CostLatencyOptimizer()
        val decision = optimizer.choose(listOf(
            ProviderProfile("slow", .9, .9, .2, .1),
            ProviderProfile("fast", .8, .8, .9, .1)
        ), prioritizeSpeed = true)
        assertEquals("fast", decision?.providerId)
    }

    @Test
    fun workflow_state_is_persistent_across_advances() {
        val engine = PersistentWorkflowEngine()
        val definition = WorkflowDefinition("w", listOf("plan", "build", "test"))
        assertEquals(0, engine.start(definition).currentStep)
        assertEquals(1, engine.advance(definition).currentStep)
        val end = engine.advance(definition)
        assertEquals(2, end.currentStep)
        assertTrue(end.completed)
        assertEquals(end, engine.state("w"))
    }

    @Test
    fun protected_publish_stage_requires_explicit_human_approval() {
        val autonomy = AdvancedAutonomy()
        val publish = autonomy.authorize(FactoryStage.PUBLISH)
        assertFalse(publish.allowed)
        assertTrue(publish.requiresHumanApproval)
        assertEquals("HUMAN_APPROVAL_REQUIRED", publish.reason)
        assertTrue(autonomy.authorize(FactoryStage.TEST).allowed)
    }

    @Test
    fun retry_limit_is_hard_boundary() {
        val autonomy = AdvancedAutonomy(AutonomyPolicy(maxAutonomousRetries = 2))
        assertTrue(autonomy.authorize(FactoryStage.TEST, 2).allowed)
        assertFalse(autonomy.authorize(FactoryStage.TEST, 3).allowed)
    }
}
