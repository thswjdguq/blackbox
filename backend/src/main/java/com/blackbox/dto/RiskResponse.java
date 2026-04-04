package com.blackbox.dto;

import java.util.List;

public record RiskResponse(
        int     riskScore,      // 0~100
        String  level,          // LOW / MEDIUM / HIGH / CRITICAL
        int     totalTasks,
        int     doneTasks,
        int     overdueTasks,
        int     completionRate, // %
        Integer daysRemaining,  // null = 마감일 없음
        List<String> reasons
) {}
