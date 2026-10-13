package com.blackbox.agent.proposal;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 등록된 카드 종류를 이름으로 찾는다. 이름이 겹치면 서버가 뜨지 않는다. */
@Component
public class ProposalKinds {
    private final Map<String, ProposalKind> kinds;

    public ProposalKinds(List<ProposalKind> kinds) {
        this.kinds = kinds.stream().collect(Collectors.toMap(ProposalKind::name, Function.identity()));
    }

    public ProposalKind get(String name) {
        ProposalKind kind = kinds.get(name);
        if (kind == null) throw new IllegalArgumentException("등록되지 않은 카드 종류입니다: " + name);
        return kind;
    }
}
