package com.codity.jobscheduler.exception;

import com.codity.jobscheduler.enums.JobStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(JobStatus from, JobStatus to) {
        super("Invalid state transition: " + from + " → " + to);
    }
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
