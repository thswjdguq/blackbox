package com.blackbox.service;

import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScoreSnapshotScopeTest {
    final EntityManager em = mock(EntityManager.class);
    final ScoreReviewActivityReader reviews = mock(ScoreReviewActivityReader.class);
    final ScoreSnapshotService snapshots = new ScoreSnapshotService(em, reviews);
    final Project project = new Project();

    @Test void fileOnlyContributorIsIncludedEvenWhenReviewReaderOmitsZeroRows() {
        ProjectMember worker = member("MEMBER"); UUID id = worker.getUser().getId();
        files(List.<Object[]>of(new Object[]{id, 1L}));
        when(reviews.read(project)).thenReturn(Map.of());
        var result = snapshots.read(project, List.of(worker));
        assertEquals(1, result.get(id).fileUploads());
        assertEquals(0, result.get(id).reviews().total());
    }

    @Test void fileQueryUsesRosterAndIgnoresObserverOrForeignReviewKeys() {
        ProjectMember worker = member("LEADER"), observer = member("OBSERVER");
        var q = files(List.of());
        when(reviews.read(project)).thenReturn(Map.of(observer.getUser().getId(),
                new ScoreReviewActivityReader.Counts(9, 9, 9, 9, 9, 9)));
        var result = snapshots.read(project, List.of(worker, observer));
        assertEquals(List.of(worker.getUser().getId()), result.keySet().stream().toList());
        assertEquals(0, result.get(worker.getUser().getId()).reviews().total());
        verify(q).setParameter("project", project);
        verify(q).setParameter("users", List.of(worker.getUser().getId()));
    }

    @Test void noContributorsSkipsBothDomainQueries() {
        assertEquals(Map.of(), snapshots.read(project, List.of(member("OBSERVER"))));
        verifyNoInteractions(em, reviews);
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Object[]> files(List<Object[]> rows) {
        TypedQuery<Object[]> q = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(rows);
        return q;
    }

    private ProjectMember member(String role) {
        User u = new User(); u.setId(UUID.randomUUID());
        ProjectMember m = new ProjectMember(); m.setProject(project); m.setUser(u); m.setRole(role); return m;
    }
}
