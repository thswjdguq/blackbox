package com.blackbox.repository;

import com.blackbox.entity.DiscordSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscordSettingsRepository extends JpaRepository<DiscordSettings, UUID> {
}
