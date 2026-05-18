package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import com.blackbox.service.ProjectAccessChecker;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── 한글 폰트 (NanumGothic, classpath 임베드) ─────────────────────────────
    private static final BaseFont NANUM;
    static {
        try {
            NANUM = BaseFont.createFont(
                    "/fonts/NanumGothic.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("NanumGothic 폰트 로드 실패: " + e.getMessage());
        }
    }

    // ── 색상 팔레트 ──────────────────────────────────────────────────────────
    private static final Color INDIGO      = new Color(99,  102, 241);
    private static final Color INDIGO_LIGHT= new Color(238, 239, 254);
    private static final Color TEAL        = new Color(45,  212, 191);
    private static final Color AMBER       = new Color(245, 158, 11);
    private static final Color RED         = new Color(239, 68,  68);
    private static final Color GRAY_DARK   = new Color(30,  41,  59);
    private static final Color GRAY_MID    = new Color(100, 116, 139);
    private static final Color GRAY_LIGHT  = new Color(241, 245, 249);
    private static final Color WHITE       = Color.WHITE;

    private final ProjectAccessChecker    accessChecker;
    private final ProjectMemberRepository memberRepo;
    private final TaskRepository          taskRepo;
    private final FileVaultRepository     vaultRepo;
    private final ContributionScoreRepository scoreRepo;
    private final AlertRepository         alertRepo;

    public ReportService(ProjectAccessChecker accessChecker,
                         ProjectMemberRepository memberRepo,
                         TaskRepository taskRepo,
                         FileVaultRepository vaultRepo,
                         ContributionScoreRepository scoreRepo,
                         AlertRepository alertRepo) {
        this.accessChecker = accessChecker;
        this.memberRepo    = memberRepo;
        this.taskRepo      = taskRepo;
        this.vaultRepo     = vaultRepo;
        this.scoreRepo     = scoreRepo;
        this.alertRepo     = alertRepo;
    }

    // ── 공개 API ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateReport(UUID projectId, User requester) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, requester);

        List<ProjectMember>      members = memberRepo.findByProject(project);
        List<Task>               tasks   = taskRepo.findByProjectOrderByCreatedAtDesc(project);
        List<FileVault>          vaults  = vaultRepo.findByProjectOrderByUploadedAtDesc(project);
        List<ContributionScore>  scores  = scoreRepo.findByProjectOrderByTotalScoreDesc(project);
        List<Alert>              alerts  = alertRepo.findByProjectOrderByCreatedAtDesc(project);

        String generatedAt = OffsetDateTime.now().format(FMT);
        String reportHash  = computeReportHash(project, scores, tasks, vaults, generatedAt);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PageFooter(reportHash));
            doc.open();

            addCoverSection(doc, project, requester, generatedAt, reportHash, members.size());
            addScoreSection(doc, scores);
            addTaskSection(doc, tasks);
            addVaultSection(doc, vaults);
            addAlertSection(doc, alerts);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패: " + e.getMessage(), e);
        }
    }

    // ── 표지 섹션 ─────────────────────────────────────────────────────────────

    private void addCoverSection(Document doc, Project project, User requester,
                                 String generatedAt, String reportHash, int memberCount)
            throws DocumentException {
        // 상단 컬러 바
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell bar = new PdfPCell(new Phrase(" "));
        bar.setBackgroundColor(INDIGO);
        bar.setFixedHeight(6);
        bar.setBorder(Rectangle.NO_BORDER);
        header.addCell(bar);
        doc.add(header);

        doc.add(spacer(16));

        // 로고 텍스트
        doc.add(para("Team Blackbox", font(11, INDIGO, Font.BOLD)));
        doc.add(spacer(4));

        // 프로젝트 제목
        doc.add(para(project.getName(), font(22, GRAY_DARK, Font.BOLD)));
        doc.add(spacer(4));
        doc.add(para("무결성 기여도 리포트", font(13, GRAY_MID, Font.NORMAL)));

        doc.add(spacer(20));

        // 메타 정보 카드
        PdfPTable meta = new PdfPTable(new float[]{120f, 300f});
        meta.setWidthPercentage(100);
        addMetaRow(meta, "생성 일시",   generatedAt);
        addMetaRow(meta, "생성자",      requester.getName() + " (" + requester.getEmail() + ")");
        addMetaRow(meta, "팀원 수",     memberCount + "명");
        if (project.getStartDate() != null)
            addMetaRow(meta, "프로젝트 기간",
                    project.getStartDate() + " ~ " + (project.getEndDate() != null ? project.getEndDate() : "진행 중"));
        doc.add(meta);

        doc.add(spacer(16));

        // 리포트 해시
        PdfPTable hashBox = new PdfPTable(1);
        hashBox.setWidthPercentage(100);
        PdfPCell hashCell = new PdfPCell();
        hashCell.setBackgroundColor(INDIGO_LIGHT);
        hashCell.setPadding(10);
        hashCell.setBorderColor(INDIGO);
        hashCell.setBorderWidth(0.5f);

        Paragraph hashPara = new Paragraph();
        hashPara.add(new Chunk("리포트 무결성 해시 (SHA-256)\n", font(8, INDIGO, Font.BOLD)));
        hashPara.add(new Chunk(reportHash, font(8, GRAY_DARK, Font.COURIER)));
        hashCell.addElement(hashPara);
        hashBox.addCell(hashCell);
        doc.add(hashBox);

        doc.add(spacer(8));
        doc.add(para("이 해시값은 리포트 데이터의 무결성을 보장합니다. 동일한 데이터로 재생성하면 동일한 해시가 출력됩니다.",
                font(8, GRAY_MID, Font.ITALIC)));

        // 구분선
        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        sep.setSpacingBefore(8);
        sep.setSpacingAfter(8);
        PdfPCell sepCell = new PdfPCell(new Phrase(" "));
        sepCell.setFixedHeight(1f);
        sepCell.setBackgroundColor(GRAY_LIGHT);
        sepCell.setBorder(Rectangle.NO_BORDER);
        sep.addCell(sepCell);
        doc.add(sep);
    }

    // ── 참여 여부 섹션 ──────────────────────────────────────────────────────

    private static final Color GREEN_LIGHT = new Color(240, 253, 250);
    private static final Color TEAL_DARK   = new Color(20,  184, 166);
    private static final Color YELLOW      = new Color(234, 179,  8);

    private void addScoreSection(Document doc, List<ContributionScore> scores) throws DocumentException {
        doc.add(sectionTitle("팀원별 참여 여부 (역할 수행)"));

        if (scores.isEmpty()) {
            doc.add(emptyNote("아직 참여 데이터가 없습니다."));
            doc.add(spacer(8));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{20f, 14f, 14f, 14f, 16f, 12f, 10f});
        table.setWidthPercentage(100);

        String[] headers = {"이름", "태스크 완료", "회의 참석", "파일 업로드", "액션아이템", "종합", "계산 일시"};
        Color[]  colors  = {INDIGO, TEAL, TEAL, AMBER, TEAL, INDIGO, GRAY_MID};
        for (int i = 0; i < headers.length; i++) {
            table.addCell(headerCell(headers[i], colors[i]));
        }

        for (ContributionScore s : scores) {
            Color levelColor = "FULL".equals(s.getParticipationLevel())    ? TEAL_DARK
                             : "PARTIAL".equals(s.getParticipationLevel()) ? YELLOW
                             :                                               RED;
            Color rowBg = "FULL".equals(s.getParticipationLevel()) ? GREEN_LIGHT : WHITE;

            table.addCell(dataCell(s.getUser().getName(), rowBg, Font.BOLD));
            table.addCell(dataCell(s.isTaskParticipated()    ? "✓ 참여" : "✗ 미참여", rowBg, Font.NORMAL));
            table.addCell(dataCell(s.isMeetingParticipated() ? "✓ 참여" : "✗ 미참여", rowBg, Font.NORMAL));
            table.addCell(dataCell(s.isFileParticipated()    ? "✓ 참여" : "✗ 미참여", rowBg, Font.NORMAL));
            table.addCell(dataCell(s.isActionParticipated()  ? "✓ 참여" : "✗ 미참여", rowBg, Font.NORMAL));

            PdfPCell levelCell = new PdfPCell(new Phrase(levelKo(s.getParticipationLevel()), font(8, WHITE, Font.BOLD)));
            levelCell.setBackgroundColor(levelColor);
            levelCell.setPadding(4);
            levelCell.setBorderColor(GRAY_LIGHT);
            levelCell.setBorderWidth(0.3f);
            table.addCell(levelCell);

            table.addCell(dataCell(s.getCalculatedAt() != null ? s.getCalculatedAt().format(FMT) : "-", rowBg, Font.NORMAL));
        }
        doc.add(table);
        doc.add(spacer(6));
        doc.add(para("* 태스크: 담당 태스크 1개↑ 완료 / 회의: 전체 50%↑ 체크인 / 파일: 1개↑ 업로드 / 액션아이템: 담당 1개↑ 완료",
                font(7, GRAY_MID, Font.ITALIC)));
        doc.add(para("* 전체참여(4개 모두) / 부분참여(2~3개) / 미참여(0~1개)",
                font(7, GRAY_MID, Font.ITALIC)));
        doc.add(spacer(12));
    }

    // ── 태스크 섹션 ───────────────────────────────────────────────────────────

    private void addTaskSection(Document doc, List<Task> tasks) throws DocumentException {
        doc.add(sectionTitle("칸반 태스크 목록"));

        if (tasks.isEmpty()) {
            doc.add(emptyNote("등록된 태스크가 없습니다."));
            doc.add(spacer(8));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{28f, 12f, 10f, 12f, 38f});
        table.setWidthPercentage(100);

        for (String h : new String[]{"제목", "상태", "우선순위", "마감일", "행 해시 (SHA-256)"}) {
            table.addCell(headerCell(h, GRAY_DARK));
        }

        for (Task t : tasks) {
            String rowHash = sha256(t.getId() + "|" + t.getTitle() + "|" +
                    t.getStatus() + "|" + t.getPriority() + "|" + t.getCreatedAt());
            table.addCell(dataCell(t.getTitle(),                 WHITE, Font.NORMAL));
            table.addCell(dataCell(statusKo(t.getStatus()),      WHITE, Font.NORMAL));
            table.addCell(dataCell(priorityKo(t.getPriority()),  WHITE, Font.NORMAL));
            table.addCell(dataCell(t.getDueDate() != null ? t.getDueDate().toString() : "-", WHITE, Font.NORMAL));
            table.addCell(hashCell(rowHash));
        }
        doc.add(table);
        doc.add(spacer(12));
    }

    // ── Hash Vault 섹션 ───────────────────────────────────────────────────────

    private void addVaultSection(Document doc, List<FileVault> vaults) throws DocumentException {
        doc.add(sectionTitle("Hash Vault — 파일 무결성 증거"));

        if (vaults.isEmpty()) {
            doc.add(emptyNote("업로드된 파일이 없습니다."));
            doc.add(spacer(8));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{22f, 8f, 14f, 56f});
        table.setWidthPercentage(100);

        for (String h : new String[]{"파일명", "버전", "업로드 일시", "SHA-256 해시 (원본 고정값)"}) {
            table.addCell(headerCell(h, TEAL));
        }

        for (FileVault v : vaults) {
            table.addCell(dataCell(v.getFileName(),                              GRAY_LIGHT, Font.NORMAL));
            table.addCell(dataCell("v" + v.getVersion(),                         GRAY_LIGHT, Font.NORMAL));
            table.addCell(dataCell(v.getUploadedAt() != null ? v.getUploadedAt().format(FMT) : "-", GRAY_LIGHT, Font.NORMAL));
            table.addCell(hashCell(v.getFileHash()));
        }
        doc.add(table);
        doc.add(spacer(12));
    }

    // ── 경보 섹션 ─────────────────────────────────────────────────────────────

    private void addAlertSection(Document doc, List<Alert> alerts) throws DocumentException {
        doc.add(sectionTitle("경보 현황"));

        List<Alert> active = alerts.stream().filter(a -> !a.isRead()).toList();
        if (active.isEmpty()) {
            doc.add(emptyNote("활성 경보 없음 — 팀 기여도가 균형 잡혀 있습니다."));
            doc.add(spacer(8));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{18f, 12f, 70f});
        table.setWidthPercentage(100);

        for (String h : new String[]{"유형", "심각도", "메시지"}) {
            table.addCell(headerCell(h, RED));
        }

        for (Alert a : active) {
            table.addCell(dataCell(alertTypeKo(a.getAlertType()), WHITE, Font.BOLD));
            table.addCell(dataCell(a.getSeverity(), WHITE, Font.NORMAL));
            table.addCell(dataCell(a.getMessage(),  WHITE, Font.NORMAL));
        }
        doc.add(table);
        doc.add(spacer(8));
    }

    // ── 리포트 해시 ───────────────────────────────────────────────────────────

    private String computeReportHash(Project project, List<ContributionScore> scores,
                                     List<Task> tasks, List<FileVault> vaults, String ts) {
        StringBuilder sb = new StringBuilder();
        sb.append(project.getId()).append("|").append(ts).append("\n");
        scores.forEach(s -> sb.append(s.getUser().getId()).append("|")
                .append(s.getTotalScore()).append("|").append(s.getCalculatedAt()).append("\n"));
        tasks.forEach(t -> sb.append(t.getId()).append("|")
                .append(t.getStatus()).append("|").append(t.getUpdatedAt()).append("\n"));
        vaults.forEach(v -> sb.append(v.getFileHash()).append("|")
                .append(v.getUploadedAt()).append("\n"));
        return sha256(sb.toString());
    }

    // ── PDF 헬퍼 ─────────────────────────────────────────────────────────────

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, font(11, INDIGO, Font.BOLD));
        p.setSpacingBefore(6);
        p.setSpacingAfter(4);
        return p;
    }

    private Paragraph emptyNote(String text) {
        return new Paragraph(text, font(9, GRAY_MID, Font.ITALIC));
    }

    private Paragraph para(String text, Font f) {
        return new Paragraph(text, f);
    }

    private Chunk spacer(float height) {
        return new Chunk("\n", font((int)(height / 3.5), WHITE, Font.NORMAL));
    }

    private void addMetaRow(PdfPTable table, String key, String value) {
        PdfPCell k = new PdfPCell(new Phrase(key, font(9, GRAY_MID, Font.NORMAL)));
        k.setBorder(Rectangle.NO_BORDER);
        k.setPadding(3);
        PdfPCell v = new PdfPCell(new Phrase(value, font(9, GRAY_DARK, Font.BOLD)));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(3);
        table.addCell(k);
        table.addCell(v);
    }

    private PdfPCell headerCell(String text, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8, WHITE, Font.BOLD)));
        c.setBackgroundColor(bg);
        c.setPadding(5);
        c.setBorderColor(WHITE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private PdfPCell dataCell(String text, Color bg, int style) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "-", font(8, GRAY_DARK, style)));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setBorderColor(GRAY_LIGHT);
        c.setBorderWidth(0.3f);
        return c;
    }

    private PdfPCell hashCell(String hash) {
        PdfPCell c = new PdfPCell(new Phrase(hash != null ? hash : "-", font(6, GRAY_MID, Font.COURIER)));
        c.setBackgroundColor(WHITE);
        c.setPadding(4);
        c.setBorderColor(GRAY_LIGHT);
        c.setBorderWidth(0.3f);
        return c;
    }

    private Font font(int size, Color color, int style) {
        Font f = new Font(NANUM, size, style);
        f.setColor(color);
        return f;
    }

    // ── 암호화 ────────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    // ── 번역 헬퍼 ────────────────────────────────────────────────────────────

    private String fmt(java.math.BigDecimal v) {
        return v != null ? String.format("%.1f", v) : "-";
    }

    private String statusKo(String s) {
        return switch (s) {
            case "TODO"        -> "예정";
            case "IN_PROGRESS" -> "진행 중";
            case "DONE"        -> "완료";
            default            -> s;
        };
    }

    private String priorityKo(String p) {
        return switch (p) {
            case "LOW"    -> "낮음";
            case "MEDIUM" -> "보통";
            case "HIGH"   -> "높음";
            case "URGENT" -> "긴급";
            default       -> p;
        };
    }

    private String alertTypeKo(String t) {
        return switch (t) {
            case "FREE_RIDE" -> "무임승차";
            case "OVERLOAD"  -> "과부하";
            case "DROPOUT"   -> "이탈";
            case "TAMPER"    -> "변조";
            default          -> t;
        };
    }

    private String levelKo(String level) {
        return switch (level) {
            case "FULL"    -> "전체참여";
            case "PARTIAL" -> "부분참여";
            default        -> "미참여";
        };
    }

    // ── 페이지 푸터 이벤트 ───────────────────────────────────────────────────

    private static class PageFooter extends PdfPageEventHelper {
        private final String reportHash;
        PageFooter(String hash) { this.reportHash = hash; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = new Font(NANUM, 7, Font.NORMAL);
            f.setColor(new Color(148, 163, 184));

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("Team Blackbox  |  무결성 리포트  |  " + reportHash.substring(0, 16) + "…", f),
                    document.getPageSize().getWidth() / 2, 30, 0);

            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase(writer.getPageNumber() + " / ?", f),
                    document.getPageSize().getRight(40), 30, 0);
        }
    }
}
