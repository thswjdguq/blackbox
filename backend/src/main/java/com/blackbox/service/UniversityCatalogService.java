package com.blackbox.service;

import com.blackbox.dto.AffiliationRequest;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;

@Service
public class UniversityCatalogService {
    public record Department(String id,String name) {}
    public record School(String id,String name,String region,String type,List<Department> departments) {}
    public record Catalog(String source,String sourceUrl,String downloadedAt,int year,String asOf,int rawRowCount,List<School> schools) {}
    public record SchoolSummary(String id,String name,String region,String type) {}
    public record SchoolList(int year,String asOf,String sourceUrl,List<SchoolSummary> schools) {}
    private final Catalog catalog;
    private final Map<String,School> schools;

    public UniversityCatalogService(ObjectMapper mapper) throws IOException {
        try(var in=new ClassPathResource("catalog/universities.json").getInputStream()) {
            catalog=mapper.readValue(in,Catalog.class);
        }
        Map<String,School> index=new LinkedHashMap<>();
        for(School school:catalog.schools()) {
            if(index.put(school.id(),school)!=null || school.departments().isEmpty()) throw new IllegalStateException("Invalid university catalog");
        }
        schools=Collections.unmodifiableMap(index);
    }
    public SchoolList list() {
        return new SchoolList(catalog.year(),catalog.asOf(),catalog.sourceUrl(),schools.values().stream()
                .map(s->new SchoolSummary(s.id(),s.name(),s.region(),s.type())).toList());
    }
    public List<Department> departments(String schoolId) { return school(schoolId).departments(); }
    public void apply(User user,AffiliationRequest req) {
        if(req==null) throw bad("소속 학교와 학과를 선택하거나 직접 입력해 주세요.");
        if("CATALOG".equals(req.source())) {
            School school=school(req.schoolId());
            Department department=school.departments().stream().filter(d->d.id().equals(req.departmentId())).findFirst()
                    .orElseThrow(()->bad("선택한 학교에 등록된 학과를 선택해 주세요."));
            user.setUniversityId(school.id());user.setUniversityName(school.name());
            user.setDepartmentId(department.id());user.setDepartmentName(department.name());
            user.setAffiliationSource("CATALOG");user.setAffiliationCatalogYear(catalog.year());
        } else if("SELF_REPORTED".equals(req.source())) {
            if(req.schoolId()!=null&&!req.schoolId().isBlank() || req.departmentId()!=null&&!req.departmentId().isBlank())
                throw bad("직접 입력 시 목록 식별자를 함께 보낼 수 없습니다.");
            String school=text(req.schoolName(),"학교"),department=text(req.departmentName(),"학과");
            user.setUniversityId(null);user.setDepartmentId(null);user.setAffiliationCatalogYear(null);
            user.setUniversityName(school);user.setDepartmentName(department);user.setAffiliationSource("SELF_REPORTED");
        } else throw bad("소속 입력 방식을 확인해 주세요.");
    }
    private School school(String id) {
        School school=id==null?null:schools.get(id);
        if(school==null) throw bad("목록에 있는 학교를 선택하거나 직접 입력을 이용해 주세요.");
        return school;
    }
    private String text(String value,String label) {
        String text=value==null?"":value.strip();
        if(text.isBlank() || text.length()>150 || text.codePoints().anyMatch(Character::isISOControl)) throw bad(label+"명을 150자 이내로 입력해 주세요.");
        return text;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
