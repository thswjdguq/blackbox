package com.blackbox.agent.proposal;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 초안 파일 이름과 "[작성 필요]" 세기(K-59 3장) */
class DocumentDraftKindTest {
    @Test void theFileNameIsSafeForPathsAndFitsTheVaultColumn() {
        assertEquals("중간 보고서 초안.docx", DocumentDraftKind.fileName("중간 보고서"));
        assertEquals("1_2차 _보고서_ v1_0 초안.docx", DocumentDraftKind.fileName("1/2차 \"보고서\" v1:0"));
        assertEquals("a_b 초안.docx", DocumentDraftKind.fileName(" a\nb "));
        String longName = DocumentDraftKind.fileName("가".repeat(300));
        assertEquals(255, longName.length());
        assertTrue(longName.endsWith(" 초안.docx"));
    }

    @Test void missingMarksAreCountedWithSpacesAndFullWidthColons() throws Exception {
        var content = new ObjectMapper().readTree("""
                {"sections": [{"body": "[작성 필요: 설문] 그리고 [작성 필요 : 표]"}, {"body": "[작성 필요：그림] [작성 필요] [작성: 아님]"}]}""");
        assertEquals(3, DocumentDraftKind.missing(content));
    }
}
