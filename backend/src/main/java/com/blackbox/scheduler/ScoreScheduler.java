package com.blackbox.scheduler;

import com.blackbox.service.ScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScoreScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScoreScheduler.class);

    private final ScoreService scoreService;

    public ScoreScheduler(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * 30분마다 전체 프로젝트 기여도 점수 재계산.
     * fixedRateString 으로 환경변수 오버라이드 가능.
     */
    @Scheduled(fixedRateString = "${app.score.scheduler-interval-ms:1800000}")
    public void recalculateAll() {
        log.info("[ScoreScheduler] 기여도 점수 재계산 시작");
        try {
            scoreService.recalculateAll();
            log.info("[ScoreScheduler] 재계산 완료");
        } catch (Exception e) {
            log.error("[ScoreScheduler] 재계산 중 오류 발생", e);
        }
    }
}
