package com.blackbox.repository;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 PostgreSQL에서 회차 매핑, 목록 조회의 조회 수, 제출물 행 잠금을 확인한다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReviewRoundRepositoryTest {
    @Autowired EntityManager em;
    @Autowired ReviewRoundRepository rounds;
    @Autowired DeliverableRepository deliverables;
    @Autowired FileVaultRepository files;

    @Test void roundsComeLatestFirstWithOneQueryAndLockFindsOnlyOwnProject() {
        User opener = user("opener"), uploader = user("uploader");
        Project project = project(opener), other = project(opener);
        Deliverable deliverable = new Deliverable(); deliverable.setProject(project);
        deliverable.setTitle("중간 보고서"); deliverable.setDueDate(LocalDate.of(2026, 10, 18)); em.persist(deliverable);
        FileVault file = new FileVault(); file.setProject(project); file.setUploader(uploader);
        file.setFileName("보고서.pdf"); file.setFileHash("a".repeat(64)); file.setFileSize(10L);
        file.setStoragePath("/tmp/review-round-test"); file.setUploadedAt(OffsetDateTime.now()); em.persist(file);
        round(deliverable, 1, null, opener);
        ReviewRound second = round(deliverable, 2, file, opener);
        second.decide("APPROVED", opener);
        em.flush();
        em.clear();

        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var found = rounds.findByDeliverableOrderByRoundNoDesc(em.getReference(Deliverable.class, deliverable.getId()));
        assertEquals(2, found.size());
        assertEquals(2, found.get(0).getRoundNo());
        assertEquals("uploader", found.get(0).getFile().getUploader().getName());
        assertEquals("opener", found.get(0).getDecidedBy().getName());
        assertNull(found.get(1).getFile());
        assertNull(found.get(1).getDecision());
        assertEquals(1, statistics.getPrepareStatementCount(), "파일·올린 사람·연 사람·결정자를 한 번에 읽는다");

        assertTrue(rounds.existsByDeliverable(deliverable));
        assertTrue(deliverables.lockByIdAndProject(deliverable.getId(), project).isPresent());
        assertTrue(deliverables.lockByIdAndProject(deliverable.getId(), other).isEmpty());
        assertTrue(files.findByIdAndProject(file.getId(), project).isPresent());
        assertTrue(files.findByIdAndProject(file.getId(), other).isEmpty());
    }

    private ReviewRound round(Deliverable deliverable, int no, FileVault file, User opener) {
        ReviewRound round = new ReviewRound(); round.setProject(deliverable.getProject()); round.setDeliverable(deliverable);
        round.setRoundNo(no); round.setFile(file); round.setOpenedBy(opener); em.persist(round);
        return round;
    }

    private Project project(User creator) {
        Project project = new Project(); project.setName("review-round-" + UUID.randomUUID()); project.setCreatedBy(creator);
        em.persist(project);
        return project;
    }

    private User user(String name) {
        User user = new User(); user.setEmail(name + "-" + UUID.randomUUID() + "@example.com"); user.setName(name);
        user.setPasswordHash("not-a-real-hash"); user.setRole("STUDENT"); em.persist(user);
        return user;
    }
}
