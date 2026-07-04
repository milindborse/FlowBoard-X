package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter @Builder
public class AnalyticsResponse {
    private List<Map<String, Object>> runsPerDay;
    private List<Map<String, Object>> successRateTrend;
    private List<Map<String, Object>> avgDurationPerWorkflow;
    private List<Map<String, Object>> nodeFailureFrequency;
}
