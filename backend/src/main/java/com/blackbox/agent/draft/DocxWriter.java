package com.blackbox.agent.draft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 문서 초안을 .docx(Office Open XML)로 쓴다(K-59 4장). 새 라이브러리 없이 워드·한컴오피스·구글 문서가 여는 최소 구성만 쓴다.
 * 들어가는 것: 문서 제목, 장 제목, 문단, 목록, 장 끝의 근거 한 줄. "[작성 필요: …]"는 굵게 표시한다.
 * 표, 그림, 머리글·바닥글, 목차 필드, 쪽 번호는 넣지 않고, 글꼴은 지정하지 않아 편집기 기본값을 쓴다.
 * 같은 내용이면 서버의 시간대와 상관없이 같은 바이트가 나온다(압축 항목의 시각을 고정한다). 같은 내용의 초안을 다시 채택하면
 * 해시가 같아 파일 금고에 내용 변경 기록(REUPLOAD)은 남지 않는다. 버전은 늘고 FILE_EDIT 활동은 화면 업로드처럼 남는다
 */
public final class DocxWriter {
    private DocxWriter() {}

    public record Document(String title, List<Section> sections) {}

    /** body는 빈 줄로 문단을 나누고, "- "로 시작하는 줄은 목록이다. sources는 장 끝에 넣을 근거 한 줄이며 없으면 null */
    public record Section(String title, String body, String sources) {}

    /** 본문의 "[작성 필요: …]". 문서에서 굵게 쓰고, 초안 카드는 그 개수를 보인다. 콜론 앞 공백과 전각 콜론(：)도 같은 표시로 본다 */
    public static final Pattern MISSING = Pattern.compile("\\[작성 필요\\s*[:：][^\\]\\n]*\\]");
    // XML 1.0에 넣을 수 없는 문자(탭·줄바꿈 외의 제어 문자, 짝 없는 서로게이트 등)
    private static final Pattern NOT_XML = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]");
    // 시간대로 바꾸지 않는 지역 시각으로 넣는다. setTime(밀리초)은 서버의 시간대에 따라 저장되는 시각이 달라지고,
    // 1980년보다 이르게 바뀌는 시간대에서는 확장 시각 필드까지 붙는다. JDK는 1980-01-01 00:00을 "1980년 이전" 표시로 써서
    // 그 시각에도 확장 필드를 붙이므로 하루 뒤로 둔다
    static final LocalDateTime FIXED_TIME = LocalDateTime.of(1980, 1, 2, 0, 0);

    public static byte[] write(Document doc) {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES);
            entry(zip, "_rels/.rels", ROOT_RELS);
            entry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS);
            entry(zip, "word/styles.xml", STYLES);
            entry(zip, "word/numbering.xml", NUMBERING);
            entry(zip, "word/document.xml", document(doc));
        } catch (IOException e) {
            throw new UncheckedIOException(e);   // 메모리에 쓰므로 일어나지 않는다
        }
        return out.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTimeLocal(FIXED_TIME);
        zip.putNextEntry(e);
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static String document(Document doc) {
        StringBuilder body = new StringBuilder();
        paragraph(body, "Title", null, doc.title());
        for (Section s : doc.sections()) {
            paragraph(body, "Heading1", null, s.title());
            for (Block block : blocks(s.body())) {
                if (block.item()) paragraph(body, "ListParagraph", "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>", block.text());
                else paragraph(body, null, null, block.text());
            }
            if (s.sources() != null && !s.sources().isBlank()) paragraph(body, "Sources", null, s.sources());
        }
        return XML + "<w:document xmlns:w=\"" + W + "\"><w:body>" + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"
                + "</w:body></w:document>";
    }

    /**
     * 본문을 문단과 목록 항목으로 나눈다. 빈 줄이 문단의 경계이고, "- "로 시작하는 줄은 그 줄 하나가 목록 항목이다.
     * 한 문단 안의 줄바꿈은 문단 안 줄바꿈(\n)으로 남긴다
     */
    record Block(boolean item, String text) {}

    static List<Block> blocks(String body) {
        List<Block> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : (body == null ? "" : body).split("\\R", -1)) {
            boolean item = line.startsWith("- ");
            if (line.isBlank() || item) {
                if (!current.isEmpty()) { out.add(new Block(false, current.toString())); current.setLength(0); }
                String content = item ? line.substring(2).strip() : "";
                if (!content.isEmpty()) out.add(new Block(true, content));   // 내용 없는 "- " 줄은 버린다
                continue;
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line.strip());
        }
        if (!current.isEmpty()) out.add(new Block(false, current.toString()));
        return out;
    }

    private static void paragraph(StringBuilder out, String style, String extraProps, String text) {
        out.append("<w:p>");
        if (style != null || extraProps != null) {
            out.append("<w:pPr>");
            if (style != null) out.append("<w:pStyle w:val=\"").append(style).append("\"/>");
            if (extraProps != null) out.append(extraProps);
            out.append("</w:pPr>");
        }
        String clean = NOT_XML.matcher(text == null ? "" : text).replaceAll("");
        Matcher m = MISSING.matcher(clean);
        int at = 0;
        while (m.find()) {
            run(out, clean.substring(at, m.start()), false);
            run(out, m.group(), true);
            at = m.end();
        }
        run(out, clean.substring(at), false);
        out.append("</w:p>");
    }

    /** 글 조각 하나. 줄바꿈은 문단 안 줄바꿈으로, 탭은 탭으로 바꾼다 */
    private static void run(StringBuilder out, String text, boolean bold) {
        if (text.isEmpty()) return;
        out.append("<w:r>");
        if (bold) out.append("<w:rPr><w:b/></w:rPr>");
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append("<w:br/>");
            String[] tabs = lines[i].split("\t", -1);
            for (int j = 0; j < tabs.length; j++) {
                if (j > 0) out.append("<w:tab/>");
                if (!tabs[j].isEmpty()) out.append("<w:t xml:space=\"preserve\">").append(escape(tabs[j])).append("</w:t>");
            }
        }
        out.append("</w:r>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r", "");
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private static final String CONTENT_TYPES = XML
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
            + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS = XML
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>"
            + "</Relationships>";

    // 글꼴은 지정하지 않는다(K-59 4장). 크기와 굵기, 문단 간격만 정하고 언어는 한국어로 둔다
    private static final String STYLES = XML
            + "<w:styles xmlns:w=\"" + W + "\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/><w:lang w:val=\"ko-KR\" w:eastAsia=\"ko-KR\"/></w:rPr></w:rPrDefault>"
            + "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>"
            + "<w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"360\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"40\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>"
            + "<w:pPr><w:keepNext/><w:spacing w:before=\"360\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/>"
            + "<w:pPr><w:ind w:left=\"720\"/></w:pPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:customStyle=\"1\" w:styleId=\"Sources\"><w:name w:val=\"Sources\"/><w:basedOn w:val=\"Normal\"/>"
            + "<w:rPr><w:color w:val=\"595959\"/><w:sz w:val=\"18\"/></w:rPr></w:style>"
            + "</w:styles>";

    private static final String NUMBERING = XML
            + "<w:numbering xmlns:w=\"" + W + "\">"
            + "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/>"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:lvlJc w:val=\"left\"/>"
            + "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
            + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
            + "</w:numbering>";
}
