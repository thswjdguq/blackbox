package com.blackbox.dto;

import java.util.List;

public record CalendarRecommendResponse(List<Recommendation> recommendations) {
    public record Recommendation(String time, int durationMinutes, String reason, int rank) {}
}
