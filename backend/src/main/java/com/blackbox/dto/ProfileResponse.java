package com.blackbox.dto;

import com.blackbox.entity.User;
import java.util.UUID;

public record ProfileResponse(UUID id, String name, String email, String role, Affiliation affiliation) {
    public record Affiliation(String source,String schoolId,String departmentId,String schoolName,String departmentName,Integer catalogYear) {}
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),u.getAffiliationSource()==null?null:
                new Affiliation(u.getAffiliationSource(),u.getUniversityId(),u.getDepartmentId(),u.getUniversityName(),u.getDepartmentName(),u.getAffiliationCatalogYear()));
    }
}
