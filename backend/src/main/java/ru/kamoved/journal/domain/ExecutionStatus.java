package ru.kamoved.journal.domain;

public enum ExecutionStatus {
    NEW,
    ORDERED_FACTORY,
    IN_PRODUCTION,
    READY_FACTORY,
    IN_TRANSIT_TO_WAREHOUSE,
    AT_WAREHOUSE,
    OUT_FOR_DELIVERY,
    COMPLETED,
    CANCELLED
}

