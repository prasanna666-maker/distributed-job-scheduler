package com.codity.jobscheduler.service;

import com.codity.jobscheduler.enums.JobStatus;
import com.codity.jobscheduler.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the job state machine.
 * Verifies all valid transitions and rejects invalid ones.
 */
class JobStateMachineTest {

    private JobStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new JobStateMachine();
    }

    @ParameterizedTest
    @DisplayName("Valid transitions should pass")
    @CsvSource({
        "QUEUED, CLAIMED",
        "SCHEDULED, QUEUED",
        "CLAIMED, RUNNING",
        "RUNNING, COMPLETED",
        "RUNNING, RETRYING",
        "RUNNING, FAILED",
        "RETRYING, QUEUED",
        "FAILED, DEAD",
        "DEAD, QUEUED"
    })
    void validTransitions(JobStatus from, JobStatus to) {
        assertDoesNotThrow(() -> stateMachine.validateTransition(from, to));
        assertTrue(stateMachine.isValidTransition(from, to));
    }

    @ParameterizedTest
    @DisplayName("Invalid transitions should throw")
    @CsvSource({
        "QUEUED, RUNNING",
        "QUEUED, COMPLETED",
        "QUEUED, FAILED",
        "CLAIMED, COMPLETED",
        "CLAIMED, FAILED",
        "RUNNING, QUEUED",
        "RUNNING, CLAIMED",
        "COMPLETED, QUEUED",
        "COMPLETED, RUNNING",
        "DEAD, RUNNING",
        "DEAD, COMPLETED"
    })
    void invalidTransitions(JobStatus from, JobStatus to) {
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(from, to));
        assertFalse(stateMachine.isValidTransition(from, to));
    }

    @Test
    @DisplayName("COMPLETED is a terminal state — no transitions out")
    void completedIsTerminal() {
        assertTrue(stateMachine.getValidNextStatuses(JobStatus.COMPLETED).isEmpty());
    }

    @Test
    @DisplayName("RUNNING has exactly 3 possible next states")
    void runningHasThreeNextStates() {
        var nextStates = stateMachine.getValidNextStatuses(JobStatus.RUNNING);
        assertEquals(3, nextStates.size());
        assertTrue(nextStates.contains(JobStatus.COMPLETED));
        assertTrue(nextStates.contains(JobStatus.RETRYING));
        assertTrue(nextStates.contains(JobStatus.FAILED));
    }
}
