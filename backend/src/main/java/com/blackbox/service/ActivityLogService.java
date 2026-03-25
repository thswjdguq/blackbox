package com.blackbox.service;

import com.blackbox.entity.ActivityLog;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * 플랫폼 이벤트를 activity_logs에 기록.
     * 호출 측 트랜잭션에 참여하거나 새 트랜잭션을 시작.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Project project, User user, String actionType, String metadata) {
        ActivityLog log = new ActivityLog();
        log.setProject(project);
        log.setUser(user);
        log.setSource("PLATFORM");
        log.setActionType(actionType);
        log.setMetadata(metadata);
        log.setTrustLevel(BigDecimal.ONE);
        log.setOccurredAt(OffsetDateTime.now());
        activityLogRepository.save(log);
    }
}
