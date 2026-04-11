package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EvidencePackageService {

    private static final DateTimeFormatter FMT      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ProjectAccessChecker    accessChecker;
    private final MeetingRepository       meetingRepository;
    private final MeetingAttendeeRepository attendeeRepository;
    private final FileVaultRepository     vaultRepository;
    private final ProjectMemberRepository memberRepository;
    private final ReportService           reportService;

    public EvidencePackageService(ProjectAccessChecker accessChecker,
                                  MeetingRepository meetingRepository,
                                  MeetingAttendeeRepository attendeeRepository,
                                  FileVaultRepository vaultRepository,
                                  ProjectMemberRepository memberRepository,
                                  ReportService reportService) {
        this.accessChecker    = accessChecker;
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.vaultRepository  = vaultRepository;
        this.memberRepository = memberRepository;
        this.reportService    = reportService;
    }

    @Transactional(readOnly = true)
    public byte[] generatePackage(UUID projectId, User requester) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, requester);

        List<Meeting>       meetings = meetingRepository.findByProjectOrderByMeetingDateDesc(project);
        List<FileVault>     vaults   = vaultRepository.findByProjectOrderByUploadedAtDesc(project);
        List<ProjectMember> members  = memberRepository.findByProject(project);

        // PDF 리포트 생성
        byte[] pdfBytes = reportService.generateReport(projectId, requester);

        String generatedAt = OffsetDateTime.now().format(FMT);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // 1. README.txt
            addEntry(zos, "evidence-package/README.txt",
                    buildReadme(project, members.size(), meetings.size(), vaults.size(), generatedAt, requester));

            // 2. 회의록 (meetings/)
            for (Meeting m : meetings) {
                String dateStr = m.getMeetingDate() != null
                        ? m.getMeetingDate().format(DATE_FMT) : "unknown-date";
                String safeTitle = safeName(m.getTitle() != null ? m.getTitle() : "untitled");
                String entryName = "evidence-package/meetings/" + dateStr + "_" + safeTitle + ".txt";
                addEntry(zos, entryName, buildMeetingText(m));
            }

            // 3. Hash Vault 이력 CSV
            addEntry(zos, "evidence-package/vault-history.csv", buildVaultCsv(vaults));

            // 4. 기여도 무결성 PDF
            addBinaryEntry(zos, "evidence-package/contribution-report.pdf", pdfBytes);

            zos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("증거 패키지 생성 실패: " + e.getMessage(), e);
        }
    }

    // ── 파일 내용 빌더 ───────────────────────────────────────────────────────

    private String buildReadme(Project project, int memberCount, int meetingCount,
                               int vaultCount, String generatedAt, User requester) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("  Team Blackbox — 팀플 증거 패키지\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append("프로젝트명  : ").append(project.getName()).append("\n");
        if (project.getCourseName() != null)
            sb.append("과목명      : ").append(project.getCourseName()).append("\n");
        if (project.getSemester() != null)
            sb.append("학기        : ").append(project.getSemester()).append("\n");
        if (project.getStartDate() != null)
            sb.append("기간        : ").append(project.getStartDate())
              .append(" ~ ").append(project.getEndDate() != null ? project.getEndDate() : "진행 중").append("\n");
        sb.append("팀원 수     : ").append(memberCount).append("명\n");
        sb.append("\n");
        sb.append("생성 일시   : ").append(generatedAt).append("\n");
        sb.append("생성자      : ").append(requester.getName())
          .append(" (").append(requester.getEmail()).append(")\n");
        sb.append("\n");
        sb.append("━━━━━━━ 패키지 구성 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append("  meetings/           - 회의록 ").append(meetingCount).append("건 (텍스트 파일)\n");
        sb.append("  vault-history.csv   - Hash Vault 파일 이력 ").append(vaultCount).append("건\n");
        sb.append("  contribution-report.pdf - SHA-256 무결성 기여도 리포트\n");
        sb.append("\n");
        sb.append("━━━━━━━ 무결성 안내 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append("  • 이 패키지의 contribution-report.pdf 에는 리포트 데이터의 SHA-256 해시가\n");
        sb.append("    포함되어 있어 데이터 무결성을 검증할 수 있습니다.\n");
        sb.append("  • vault-history.csv 의 file_hash 열은 업로드 시점에 고정된\n");
        sb.append("    원본 파일의 SHA-256 해시값입니다.\n");
        sb.append("  • Team Blackbox 는 외부 시스템이 기록한 데이터를 읽기 전용으로 수집합니다.\n");
        sb.append("    데이터를 임의로 생성하거나 수정하지 않습니다.\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        return sb.toString();
    }

    private String buildMeetingText(Meeting m) {
        StringBuilder sb = new StringBuilder();
        sb.append("회의 제목   : ").append(orDash(m.getTitle())).append("\n");
        sb.append("일시        : ").append(
                m.getMeetingDate() != null ? m.getMeetingDate().format(FMT) : "-").append("\n");
        sb.append("목적        : ").append(orDash(m.getPurpose())).append("\n");
        sb.append("체크인 코드 : ").append(orDash(m.getCheckinCode())).append("\n");

        // 참석자
        List<MeetingAttendee> attendees = attendeeRepository.findByMeeting(m);
        long checkedIn = attendees.stream().filter(MeetingAttendee::isCheckedIn).count();
        sb.append("참석자      : ").append(checkedIn).append(" / ").append(attendees.size()).append("명 체크인\n");
        attendees.forEach(a -> sb.append("              ")
                .append(a.isCheckedIn() ? "✓" : "✗")
                .append(" ").append(a.getUser().getName())
                .append(" (").append(a.getUser().getEmail()).append(")")
                .append(a.isCheckedIn() && a.getCheckedAt() != null
                        ? "  [" + a.getCheckedAt().format(FMT) + "]" : "")
                .append("\n"));

        sb.append("\n");
        sb.append("─── 회의록 ──────────────────────────────────────────────────────────\n");
        sb.append(orDash(m.getNotes())).append("\n\n");
        sb.append("─── 결정 사항 ───────────────────────────────────────────────────────\n");
        sb.append(orDash(m.getDecisions())).append("\n\n");

        if (m.getAiSummary() != null) {
            sb.append("─── AI 요약 (Claude 생성) ────────────────────────────────────────────\n");
            sb.append(m.getAiSummary()).append("\n\n");
        }

        if (m.getNotionPageId() != null) {
            sb.append("─── Notion 동기화 ────────────────────────────────────────────────────\n");
            sb.append("URL: ").append(m.getNotionPageId()).append("\n");
            if (m.getNotionSyncedAt() != null)
                sb.append("동기화 일시: ").append(m.getNotionSyncedAt().format(FMT)).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildVaultCsv(List<FileVault> vaults) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,file_name,version,file_hash,file_size_bytes,uploader,uploaded_at\n");
        for (FileVault v : vaults) {
            sb.append(csv(v.getId().toString())).append(",");
            sb.append(csv(v.getFileName())).append(",");
            sb.append(v.getVersion()).append(",");
            sb.append(csv(v.getFileHash())).append(",");
            sb.append(v.getFileSize()).append(",");
            sb.append(csv(v.getUploader().getName())).append(",");
            sb.append(v.getUploadedAt() != null ? v.getUploadedAt().format(FMT) : "").append("\n");
        }
        return sb.toString();
    }

    // ── ZIP 헬퍼 ─────────────────────────────────────────────────────────────

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(bytes.length);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    private void addBinaryEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(bytes.length);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    // ── 문자열 유틸 ─────────────────────────────────────────────────────────

    private String orDash(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }

    /** CSV 필드 이스케이프 (쉼표·줄바꿈·따옴표 포함 시 따옴표로 감쌈) */
    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 파일명으로 쓸 수 없는 문자 제거 */
    private String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .substring(0, Math.min(name.length(), 50));
    }
}
