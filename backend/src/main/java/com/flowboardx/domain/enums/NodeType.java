package com.flowboardx.domain.enums;

public enum NodeType {
    MANUAL_TRIGGER,
    SCHEDULER_TRIGGER,
    WEBHOOK_TRIGGER,
    CONDITION,
    TRANSFORM,
    DELAY,
    AGGREGATOR,
    HTTP_REQUEST,
    POSTGRES_QUERY,
    REDIS_PUBLISH,
    EMAIL,
    PARALLEL_SPLIT,
    MERGE,
    RETRY,
    APPROVAL
}
