package com.blackbox.repository;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 실제 DB 테스트가 함께 쓰는 자료 만들기. 부르는 쪽의 트랜잭션에 저장만 한다. */
public final class ReviewFixtures {
    private ReviewFixtures() {}

    public static User user(EntityManager em, String name) {
        User user = new User(); user.setEmail(name + "-" + UUID.randomUUID() + "@example.com"); user.setName(name);
        user.setPasswordHash("not-a-real-hash"); user.setRole("STUDENT"); em.persist(user);
        return user;
    }

    public static Project project(EntityManager em, User creator) {
        Project project = new Project(); project.setName("review-" + UUID.randomUUID()); project.setCreatedBy(creator);
        em.persist(project); return project;
    }

    public static Deliverable deliverable(EntityManager em, Project project, String title) {
        Deliverable d = new Deliverable(); d.setProject(project); d.setTitle(title); d.setDueDate(LocalDate.of(2026, 10, 18));
        em.persist(d); return d;
    }

    public static ProjectMember member(EntityManager em, Project project, User user, String role) {
        ProjectMember member = new ProjectMember(); member.setProject(project); member.setUser(user); member.setRole(role);
        em.persist(member); return member;
    }

    /** content 한 글자가 파일 내용을 대신한다. 같은 글자면 같은 내용(같은 해시)이다. */
    public static FileVault file(EntityManager em, Project project, User uploader, String name, int version, char content) {
        FileVault file = new FileVault(); file.setProject(project); file.setUploader(uploader); file.setFileName(name);
        file.setVersion(version); file.setFileHash(String.valueOf(content).repeat(64)); file.setFileSize(10L);
        file.setStoragePath("/tmp/review-fixture"); file.setUploadedAt(OffsetDateTime.now()); em.persist(file);
        return file;
    }

    /** assessedBy가 null이면 충족을 확인하지 않은 요구사항이다. */
    public static DeliverableRequirement requirement(EntityManager em, Deliverable deliverable, boolean required, User assessedBy) {
        DeliverableRequirement r = new DeliverableRequirement(); r.setDeliverable(deliverable); r.setContent("요구사항 " + UUID.randomUUID());
        r.setRequired(required);
        if (assessedBy != null) r.assess(assessedBy);
        em.persist(r); return r;
    }

    public static ReviewRound round(EntityManager em, Deliverable deliverable, int no, FileVault file, User opener, String decision) {
        ReviewRound round = round(em, deliverable, no, opener, decision); round.setFile(file);
        return round;
    }

    /** decision이 null이면 결정 전 회차다. */
    public static ReviewRound round(EntityManager em, Deliverable deliverable, int no, User opener, String decision) {
        ReviewRound round = new ReviewRound(); round.setProject(deliverable.getProject()); round.setDeliverable(deliverable);
        round.setRoundNo(no); round.setOpenedBy(opener);
        if (decision != null) round.decide(decision, opener);
        em.persist(round); return round;
    }
}
