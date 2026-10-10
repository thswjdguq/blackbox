package com.blackbox.agent.tool;

import java.util.List;

/** 도구가 돌려준 목록이 모델에 전달되는 모양. 건수 상한을 넘으면 앞에서부터 자르고, 잘렸다는 것과 전체 건수를 함께 알린다. */
public record Listing<T>(List<T> items, int total, boolean truncated) {
    // ponytail: 전부 읽은 뒤 자른다. 기존 조회에 건수 인자가 없다. 프로젝트 하나의 기록이 수천 건이 되면 조회 쪽에 상한을 넣는다
    public static <T> Listing<T> of(List<T> all, int limit) {
        boolean truncated = all.size() > limit;
        return new Listing<>(truncated ? all.subList(0, limit) : all, all.size(), truncated);
    }
}
