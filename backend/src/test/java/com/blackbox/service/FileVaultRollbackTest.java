package com.blackbox.service;

import com.blackbox.agent.draft.GeneratedFile;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 업로드와 같은 트랜잭션이 되돌아가면 디스크의 파일도 지운다. DB에 기록이 없는 파일이 남지 않는다(K-59 3장) */
@SpringBootTest(properties = "app.file.upload-dir=${java.io.tmpdir}/bb-draft-test")
@AutoConfigureMockMvc @ActiveProfiles("test")
class FileVaultRollbackTest {
    @Autowired FileVaultService files;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ScoreScheduler scoreScheduler;   // DocumentDraftCardHttpTest와 같은 설정이라 서버를 함께 쓴다

    @Test void aRolledBackUploadLeavesNoFileOnDisk() {
        Path stored = new TransactionTemplate(transactions).execute(status -> {
            User owner = user(em, "업로드한 사람");
            Project project = project(em, owner);
            member(em, project, owner, "LEADER");
            em.flush();
            try {
                String path = em.createQuery("select f.storagePath from FileVault f where f.id = :id", String.class)
                        .setParameter("id", files.upload(project.getId(), new GeneratedFile("되돌릴 파일.docx", GeneratedFile.DOCX, new byte[]{1, 2, 3}), owner).id())
                        .getSingleResult();
                assertTrue(Files.exists(Path.of(path)), "되돌리기 전에는 디스크에 있다");
                status.setRollbackOnly();   // 업로드 다음 일(검토 회차 열기 등)이 실패한 경우
                return Path.of(path);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertFalse(Files.exists(stored), "되돌린 업로드의 파일이 남았다: " + stored);
    }
}
