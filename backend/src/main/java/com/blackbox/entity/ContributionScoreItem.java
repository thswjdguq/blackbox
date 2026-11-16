package com.blackbox.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** K-40의 저장된 항목 값. 같은 값을 JSONB와 응답에 사용하며 code는 이후 확장을 허용한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContributionScoreItem(String code, String label, String description, long count, Long total,
                                    boolean passed, boolean countsTowardLevel, List<Detail> details) {
    public ContributionScoreItem {
        details = details == null ? null : List.copyOf(details);
    }
    public record Detail(String code, String label, long count) {}
}
