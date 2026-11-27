package com.blackbox.agent.draft;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** .docx 생성(K-59 4장, 8장 수용 테스트 5). 압축을 풀어 각 XML이 올바른지와 문서의 내용을 본다 */
class DocxWriterTest {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    final DocxWriter.Document doc = new DocxWriter.Document("중간 보고서 <초안>", List.of(
            new DocxWriter.Section("문제 정의", "첫 줄 & 둘째 문장\n이어지는 줄\n\n- 첫 항목\n-  \n- 둘째 항목\n결과는 [작성 필요: 실험 결과]로 둔다\u0001 😀",
                    "근거: 업무 「문제 정의 초안」, 회의 「설계 회의」(2026-11-20)"),
            new DocxWriter.Section("설계", "가운데\t탭", null)));

    @Test void everyPartIsWellFormedAndTheDocumentHasTheContractsPieces() throws Exception {
        Map<String, byte[]> parts = unzip(DocxWriter.write(doc));
        assertEquals(List.of("[Content_Types].xml", "_rels/.rels", "word/_rels/document.xml.rels", "word/styles.xml",
                "word/numbering.xml", "word/document.xml"), new ArrayList<>(parts.keySet()));
        for (var part : parts.entrySet()) assertNotNull(parse(part.getValue()), part.getKey());   // 하나라도 깨지면 편집기가 열지 못한다

        List<String[]> paragraphs = paragraphs(parse(parts.get("word/document.xml")));
        assertEquals(List.of(
                "Title|중간 보고서 <초안>",
                "Heading1|문제 정의",
                "|첫 줄 & 둘째 문장\n이어지는 줄",
                "ListParagraph|첫 항목",
                "ListParagraph|둘째 항목",
                "|결과는 [작성 필요: 실험 결과]로 둔다 😀",
                "Sources|근거: 업무 「문제 정의 초안」, 회의 「설계 회의」(2026-11-20)",
                "Heading1|설계",
                "|가운데\t탭"), paragraphs.stream().map(p -> p[0] + "|" + p[1]).toList(),
                "XML에 넣을 수 없는 제어 문자는 빠지고, 내용 없는 목록 줄은 버린다");

        String xml = new String(parts.get("word/document.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("<w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">[작성 필요: 실험 결과]</w:t></w:r>"), "작성 필요 표시는 굵게");
        assertEquals(2, xml.split("<w:numId w:val=\"1\"/>", -1).length - 1, "목록 항목은 글머리 기호 목록이다");
        assertFalse(new String(parts.get("word/styles.xml"), java.nio.charset.StandardCharsets.UTF_8).contains("rFonts"), "글꼴은 지정하지 않는다");
    }

    /** 같은 내용의 초안을 다시 채택하면 해시가 같아 내용 변경 기록(REUPLOAD)이 남지 않는다. 항목의 시각은 서버의 시간대와 상관없이 고정이다 */
    @Test void theSameDocumentGivesTheSameBytesInAnyTimeZone() throws Exception {
        byte[] docx = DocxWriter.write(doc);
        assertArrayEquals(docx, DocxWriter.write(doc));
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                assertEquals(DocxWriter.FIXED_TIME, e.getTimeLocal(), e.getName());
                assertNull(e.getExtra(), "시간대에 따라 붙는 확장 시각 필드가 없다: " + e.getName());
            }
        }
    }

    @Test void aLoneSurrogateIsDroppedButAPairStays() throws Exception {
        var broken = new DocxWriter.Document("제목", List.of(new DocxWriter.Section("장", "앞\uD83D뒤 😀", null)));
        String xml = new String(unzip(DocxWriter.write(broken)).get("word/document.xml"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains(">앞뒤 😀<"), xml);
    }

    @Test void blankLinesSplitParagraphsAndDashLinesAreItems() {
        assertEquals(List.of(new DocxWriter.Block(false, "가\n나"), new DocxWriter.Block(true, "항목"), new DocxWriter.Block(false, "다")),
                DocxWriter.blocks("  가 \n나\n\n\n- 항목\n다\n"));
        assertEquals(List.of(), DocxWriter.blocks(null));
        assertEquals(List.of(new DocxWriter.Block(false, "-붙여 쓴 줄은 문단")), DocxWriter.blocks("-붙여 쓴 줄은 문단"));
    }

    private static Map<String, byte[]> unzip(byte[] docx) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) parts.put(e.getName(), zip.readAllBytes());
        }
        return parts;
    }

    private static org.w3c.dom.Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /** 문단마다 [스타일, 글]. 문단 안 줄바꿈은 \n, 탭은 \t로 읽는다 */
    private static List<String[]> paragraphs(org.w3c.dom.Document document) {
        List<String[]> out = new ArrayList<>();
        NodeList ps = document.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < ps.getLength(); i++) {
            Element p = (Element) ps.item(i);
            NodeList style = p.getElementsByTagNameNS(W, "pStyle");
            StringBuilder text = new StringBuilder();
            NodeList all = p.getElementsByTagNameNS(W, "*");
            for (int j = 0; j < all.getLength(); j++) {
                switch (all.item(j).getLocalName()) {
                    case "t" -> text.append(all.item(j).getTextContent());
                    case "br" -> text.append('\n');
                    case "tab" -> text.append('\t');
                    default -> {}
                }
            }
            out.add(new String[]{style.getLength() == 0 ? "" : ((Element) style.item(0)).getAttributeNS(W, "val"), text.toString()});
        }
        return out;
    }
}
