package com.aibrain.app.brain

/**
 * Coordinates the complete v3 lifecycle while keeping the integration boundary explicit.
 * It is intentionally provider-agnostic: an AI still returns an artifact and IaBrain decides
 * whether that artifact may progress.
 */
class SoftwareFactoryCoordinator(
    private val maxRepairCycles: Int = 3
) {
    enum class State {
        PLANNED, ARTIFACTS_RECEIVED, INTEGRATED, VALIDATING, REPAIR_REQUIRED, READY_FOR_PR, MERGED
    }

    data class Run(
        val projectId: String,
        val objective: String,
        val state: State = State.PLANNED,
        val repairCycles: Int = 0,
        val history: List<State> = listOf(State.PLANNED),
        val lastFailure: String? = null
    )

    fun receiveArtifacts(run: Run, snapshotCount: Int): Run {
        require(snapshotCount > 0) { "At least one AI artifact is required" }
        return transition(run, State.ARTIFACTS_RECEIVED)
    }

    fun integrate(run: Run, mergeSafe: Boolean): Run =
        if (mergeSafe) transition(run, State.INTEGRATED)
        else transition(run, State.REPAIR_REQUIRED, "Artifact integration blocked")

    fun startValidation(run: Run): Run {
        check(run.state == State.INTEGRATED) { "Validation requires an integrated workspace" }
        return transition(run, State.VALIDATING)
    }

    fun validationPassed(run: Run): Run {
        check(run.state == State.VALIDATING) { "Validation result received in invalid state" }
        return transition(run, State.READY_FOR_PR)
    }

    fun validationFailed(run: Run, reason: String): Run {
        check(run.state == State.VALIDATING) { "Failure received in invalid state" }
        check(run.repairCycles < maxRepairCycles) { "Maximum autonomous repair cycles reached" }
        return transition(run, State.REPAIR_REQUIRED, reason, run.repairCycles + 1)
    }

    fun repairSubmitted(run: Run, newArtifactCount: Int): Run {
        check(run.state == State.REPAIR_REQUIRED) { "Repair is not currently required" }
        require(newArtifactCount > 0) { "Repair must produce at least one artifact" }
        return transition(run, State.ARTIFACTS_RECEIVED)
    }

    fun mergeApproved(run: Run): Run {
        check(run.state == State.READY_FOR_PR) { "Merge requires an approved PR state" }
        return transition(run, State.MERGED)
    }

    private fun transition(run: Run, target: State, failure: String? = null, repairCycles: Int = run.repairCycles): Run {
        val allowed = when (run.state) {
            State.PLANNED -> target == State.ARTIFACTS_RECEIVED
            State.ARTIFACTS_RECEIVED -> target == State.INTEGRATED || target == State.REPAIR_REQUIRED
            State.INTEGRATED -> target == State.VALIDATING
            State.VALIDATING -> target == State.READY_FOR_PR || target == State.REPAIR_REQUIRED
            State.REPAIR_REQUIRED -> target == State.ARTIFACTS_RECEIVED
            State.READY_FOR_PR -> target == State.MERGED
            State.MERGED -> false
        }
        check(allowed) { "Invalid factory transition: ${run.state} -> $target" }
        return run.copy(
            state = target,
            repairCycles = repairCycles,
            history = run.history + target,
            lastFailure = failure
        )
    }
}
