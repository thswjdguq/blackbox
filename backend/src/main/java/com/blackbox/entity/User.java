package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // 현재 주소를 확인한 시각(K-41 9·10장). NULL이면 미확인. EmailVerificationService만 SQL로 쓴다.
    // JPA에서는 읽기만 해서, 확인 전에 읽어 둔 엔티티를 저장해도 확인 시각을 지우지 않는다
    @Column(name = "email_verified_at", insertable = false, updatable = false)
    private OffsetDateTime emailVerifiedAt;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // String 타입 유지 — ddl-auto:validate 환경에서 enum 마찰 방지
    // 값: STUDENT | PROFESSOR | TA
    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "avatar_url")
    private String avatarUrl;

    // DB DEFAULT NOW() 사용 — INSERT/UPDATE에서 제외
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "notion_api_key", length = 500)
    private String notionApiKey;

    @Column(name = "notion_page_id", length = 255)
    private String notionPageId;

    @Column(name="university_id", length=64)
    private String universityId;
    @Column(name="university_name", length=150)
    private String universityName;
    @Column(name="department_id", length=64)
    private String departmentId;
    @Column(name="department_name", length=150)
    private String departmentName;
    @Column(name="affiliation_source", length=20)
    private String affiliationSource;
    @Column(name="affiliation_catalog_year")
    private Integer affiliationCatalogYear;

    // ── UserDetails ──────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
