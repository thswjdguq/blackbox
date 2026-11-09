package com.blackbox.agent.proposal;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeadlineTimeTest {
    @Test void validatesMinutePrecisionAndKeepsDateOnlyRequests() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String time : new String[] {null, "00:00", "18:00", "23:59"})
                assertTrue(validator.validate(new DeliverableDtos.SaveRequest("자료", null, LocalDate.now(), null, null, time)).isEmpty());
            for (String time : new String[] {"", "24:00", "18:60", "9:00", "18:00:30"})
                assertFalse(validator.validate(new DeliverableDtos.SaveRequest("자료", null, LocalDate.now(), null, null, time)).isEmpty());
        }
    }

    @Test void aiProposalKeepsExplicitTimeAndRejectsInvalidTime() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var mapper = new ObjectMapper().findAndRegisterModules();
            var kind = new DeliverablePlanKind(mapper, factory.getValidator(), mock(DeliverableService.class), mock(TaskService.class));
            var content = mapper.readTree("""
                {"deliverable":{"title":"자료","dueDate":"2026-11-05","dueTime":"18:00"},"requirements":[],"tasks":[]}
                """);
            var checked = kind.check(UUID.randomUUID(), new User(), content, true);
            assertTrue(checked.problems().isEmpty());
            assertEquals("18:00", checked.content().path("deliverable").path("dueTime").asText());
            ((com.fasterxml.jackson.databind.node.ObjectNode)content.path("deliverable")).put("dueTime", "24:00");
            assertFalse(kind.check(UUID.randomUUID(), new User(), content, true).problems().isEmpty());
        }
    }
}
