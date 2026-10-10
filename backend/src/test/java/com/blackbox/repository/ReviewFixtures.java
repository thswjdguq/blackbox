package com.blackbox.repository;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
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

    /** decision이 null이면 결정 전 회차다. */
    public static ReviewRound round(EntityManager em, Deliverable deliverable, int no, User opener, String decision) {
        ReviewRound round = new ReviewRound(); round.setProject(deliverable.getProject()); round.setDeliverable(deliverable);
        round.setRoundNo(no); round.setOpenedBy(opener);
        if (decision != null) round.decide(decision, opener);
        em.persist(round); return round;
    }
}
