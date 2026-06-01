package com.blackbox.dto;

import java.util.List;

public record CalendarRecommendResponse(List<Recommendation> recommendations, String message) {

    /** 기존 호환 생성자 — 기존 호출부 변경 불필요 */
    public CalendarRecommendResponse(List<Recommendation> recommendations) {
        this(recommendations, null);
    }

    public record Recommendation(
            String time,
            int durationMinutes,
            String reason,
            int rank,
            int score,
            List<String> softBlockMembers,
            boolean needsConfirm
    ) {
        /** 기존 호환 생성자 (fallback / parseJsonManually 용) */
        public Recommendation(String time, int durationMinutes, String reason, int rank) {
            this(time, durationMinutes, reason, rank, 100, List.of(), false);
        }
    }
}
