package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // ── 내 프로젝트 목록 ────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listMyProjects(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.listMyProjects(user));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(req, user));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.getProject(projectId, user));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.updateProject(projectId, req, user));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        projectService.deleteProject(projectId, user);
        return ResponseEntity.noContent().build();
    }

    // ── 초대 코드 ──────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/invite-code/regenerate")
    public ResponseEntity<ProjectResponse> regenerateInviteCode(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.regenerateInviteCode(projectId, user));
    }

    @PostMapping("/join")
    public ResponseEntity<MemberResponse> joinProject(
            @Valid @RequestBody JoinProjectRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.joinProject(req, user));
    }

    // ── 멤버 관리 ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<MemberResponse>> listMembers(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.listMembers(projectId, user));
    }

    @PatchMapping("/{projectId}/members/{memberId}/role")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.updateMemberRole(projectId, memberId, req, user));
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal User user) {
        projectService.kickMember(projectId, memberId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}/members/me")
    public ResponseEntity<Void> leaveProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        projectService.leaveProject(projectId, user);
        return ResponseEntity.noContent().build();
    }

    // ── 데이터 수집 동의 ────────────────────────────────────────────────────

    @PostMapping("/{projectId}/consent")
    public ResponseEntity<MemberResponse> recordConsent(
            @PathVariable UUID projectId,
            @Valid @RequestBody ConsentRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.recordConsent(projectId, req, user));
    }
}
