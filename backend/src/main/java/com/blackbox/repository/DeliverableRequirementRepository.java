package com.blackbox.repository;
import com.blackbox.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeliverableRequirementRepository extends JpaRepository<DeliverableRequirement, UUID> {
    List<DeliverableRequirement> findByDeliverableOrderByCreatedAtAsc(Deliverable deliverable);
    Optional<DeliverableRequirement> findByIdAndDeliverable(UUID id, Deliverable deliverable);
    void deleteByDeliverable(Deliverable deliverable);
}
