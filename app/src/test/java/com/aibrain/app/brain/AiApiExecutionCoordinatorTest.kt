package com.aibrain.app.brain

import org.junit.Assert.*
import org.junit.Test

class AiApiExecutionCoordinatorTest {
    private val qwen=AiApiModel("qwen","qwen","Qwen",setOf("chat","coding"),AiApiAccess.FREE_TIER,"https://example.com/qwen","https://example.com/qwen-docs")
    private val glm=AiApiModel("glm","glm","GLM",setOf("chat","coding"),AiApiAccess.FREE_TIER,"https://example.com/glm","https://example.com/glm-docs")
    private val verified=listOf(AiApiVerification("qwen","qwen",AiApiAvailability.VERIFIED,200,20,1L),AiApiVerification("glm","glm",AiApiAvailability.VERIFIED,200,30,1L))

    @Test fun quotaCanSwitchToAnotherVerifiedApi(){
        var calls=0
        val out=AiApiExecutionCoordinator().execute(listOf(qwen,glm),verified,setOf("chat"),{"key"},"hello",AiApiProjectPolicy.AUTO_SWITCH_COMPATIBLE,false,AiApiExecutor{model,_,_->calls++;if(model.modelId=="qwen")AiApiExecutionResult(false,failure=AiApiFailure.QUOTA) else AiApiExecutionResult(true,"IaBrain OK")})
        assertTrue(out.result.success)
        assertTrue(out.switched)
        assertEquals("glm",out.model?.modelId)
        assertEquals(2,calls)
    }

    @Test fun askPolicyStopsBeforeSwitch(){
        val out=AiApiExecutionCoordinator().execute(listOf(qwen,glm),verified,setOf("chat"),{"key"},"hello",AiApiProjectPolicy.ASK_BEFORE_SWITCH,false,AiApiExecutor{_,_,_->AiApiExecutionResult(false,failure=AiApiFailure.QUOTA)})
        assertTrue(out.requiresUserConfirmation)
        assertFalse(out.switched)
    }
}
