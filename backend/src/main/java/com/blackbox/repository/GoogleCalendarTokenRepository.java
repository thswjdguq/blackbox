package com.blackbox.repository;

import com.blackbox.entity.GoogleCalendarToken;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleCalendarTokenRepository extends JpaRepository<GoogleCalendarToken, UUID> {
    Optional<GoogleCalendarToken> findByUser(User user);
    List<GoogleCalendarToken> findByUserIdIn(List<UUID> userIds);
    boolean existsByUser(User user);
}
