package com.minimax.dualrecord.statemachine;

import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordingStateMachineTest {

    @Test
    void testValidForwardTransitions() {
        assertTrue(RecordingStateMachine.canTransition(RecordingState.INIT, RecordingState.IDENTITY_VERIFIED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.IDENTITY_VERIFIED, RecordingState.RISK_ASSESSED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.RISK_ASSESSED, RecordingState.SCRIPT_LOADED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.SCRIPT_LOADED, RecordingState.RECORDING));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.RECORDING, RecordingState.RECORDED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.RECORDED, RecordingState.AI_QA));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.AI_QA, RecordingState.AI_QA_PASSED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.AI_QA, RecordingState.AI_QA_FLAGGED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.HUMAN_REVIEWED, RecordingState.SIGNED));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.SIGNED, RecordingState.ARCHIVED));
    }

    @Test
    void testValidRollbackTransitions() {
        assertTrue(RecordingStateMachine.canTransition(RecordingState.RECORDING, RecordingState.ROLLED_BACK));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.AI_QA_FLAGGED, RecordingState.ROLLED_BACK));
        assertTrue(RecordingStateMachine.canTransition(RecordingState.HUMAN_REVIEWED, RecordingState.ROLLED_BACK));
    }

    @Test
    void testInvalidTransitions() {
        // 跳过中间状态
        assertFalse(RecordingStateMachine.canTransition(RecordingState.INIT, RecordingState.RECORDED));
        // 倒着走
        assertFalse(RecordingStateMachine.canTransition(RecordingState.RECORDED, RecordingState.RECORDING));
        // 终态不能转出
        assertFalse(RecordingStateMachine.canTransition(RecordingState.ARCHIVED, RecordingState.SIGNED));
        assertFalse(RecordingStateMachine.canTransition(RecordingState.ROLLED_BACK, RecordingState.INIT));
    }

    @Test
    void testTransitionThrowsExceptionOnIllegal() {
        IllegalStateTransitionException ex = assertThrows(
                IllegalStateTransitionException.class,
                () -> RecordingStateMachine.transition(RecordingState.INIT, RecordingState.ARCHIVED)
        );
        assertTrue(ex.getMessage().contains("非法状态转移"));
    }

    @Test
    void testSelfTransition() {
        assertTrue(RecordingStateMachine.canTransition(RecordingState.RECORDING, RecordingState.RECORDING));
    }

    @Test
    void testNextStates() {
        // INIT 可以走 IDENTITY_VERIFIED / RISK_ASSESSED / SCRIPT_LOADED / RECORDING / FAILED (5 路径)
        assertEquals(5, RecordingStateMachine.nextStates(RecordingState.INIT).size());
        assertTrue(RecordingStateMachine.nextStates(RecordingState.INIT).contains(RecordingState.IDENTITY_VERIFIED));
        assertTrue(RecordingStateMachine.nextStates(RecordingState.INIT).contains(RecordingState.FAILED));
    }
}
