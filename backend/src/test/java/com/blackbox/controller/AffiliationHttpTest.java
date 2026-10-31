package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.security.JwtService;
import com.blackbox.service.UniversityCatalogService;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.blackbox.repository.ReviewFixtures.user;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AffiliationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired UniversityCatalogService catalog;
    private JsonNode signup(Map<String,Object> affiliation,int status) throws Exception {
        var body=new HashMap<String,Object>();
        body.put("email","affiliation-"+UUID.randomUUID()+"@example.invalid");body.put("name","합성 가입자");body.put("password","test-only-password");
        if(affiliation!=null)body.put("affiliation",affiliation);
        var result=mvc.perform(post("/api/auth/signup").contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().is(status)).andReturn();
        if(status!=201)return mapper.readTree(result.getResponse().getContentAsString());
        em.flush();User user=em.createQuery("select u from User u where u.email=:email",User.class).setParameter("email",body.get("email")).getSingleResult();
        return mapper.readTree(mvc.perform(get("/api/auth/profile").header("Authorization","Bearer "+jwt.generateAccessToken(user.getEmail()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void publicCatalogOnlyListsUndergraduateSchoolsAndTheirDepartments() throws Exception {
        mvc.perform(get("/api/auth/affiliation/schools")).andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2025));
        assertTrue(catalog.list().schools().size()>300);
        assertTrue(catalog.list().schools().stream().noneMatch(s->s.type().contains("대학원")));
        var shingu=catalog.list().schools().stream().filter(s->s.name().equals("신구대학교")).findFirst().orElseThrow();
        mvc.perform(get("/api/auth/affiliation/schools/"+shingu.id()+"/departments")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/affiliation/schools/fake/departments")).andExpect(status().isBadRequest());
    }
    @Test void selectedAffiliationUsesCanonicalNamesAndPersists() throws Exception {
        var school=catalog.list().schools().get(0);var department=catalog.departments(school.id()).get(0);
        var profile=signup(Map.of("source","CATALOG","schoolId",school.id(),"departmentId",department.id(),"schoolName","위조 이름"),201);
        assertEquals(school.name(),profile.at("/affiliation/schoolName").asText());
        assertEquals(department.name(),profile.at("/affiliation/departmentName").asText());
        assertEquals("CATALOG",profile.at("/affiliation/source").asText());
    }
    @Test void manualAffiliationIsSavedWithoutClaimingCatalogMatch() throws Exception {
        var profile=signup(Map.of("source","SELF_REPORTED","schoolName","  합성 신규대학교  ","departmentName"," 신설융합전공 "),201);
        assertEquals("합성 신규대학교",profile.at("/affiliation/schoolName").asText());
        assertEquals("SELF_REPORTED",profile.at("/affiliation/source").asText());
        assertTrue(profile.at("/affiliation/schoolId").isNull());assertTrue(profile.at("/affiliation/catalogYear").isNull());
    }
    @Test void rejectsUnknownSchoolForeignDepartmentAndMissingAffiliation() throws Exception {
        var schools=catalog.list().schools();var other=catalog.departments(schools.get(1).id()).get(0);
        signup(Map.of("source","CATALOG","schoolId",schools.get(0).id(),"departmentId",other.id()),400);
        signup(Map.of("source","CATALOG","schoolId","fake","departmentId","fake"),400);
        signup(null,400);
    }
    @Test void rejectsBlankOversizedOrConflictingManualInput() throws Exception {
        signup(Map.of("source","SELF_REPORTED","schoolName"," ","departmentName","합성학과"),400);
        signup(Map.of("source","SELF_REPORTED","schoolName","가".repeat(151),"departmentName","합성학과"),400);
        signup(Map.of("source","SELF_REPORTED","schoolName","합성대학","departmentName","합성학과","schoolId","fake"),400);
    }
    @Test void existingAccountCanAddAffiliationAndNameOnlyUpdatePreservesIt() throws Exception {
        User user=user(em,"기존 합성회원");em.flush();String token="Bearer "+jwt.generateAccessToken(user.getEmail());String hash=user.getPasswordHash();
        mvc.perform(get("/api/auth/profile").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.affiliation").isEmpty());
        mvc.perform(put("/api/auth/profile").header("Authorization",token).contentType("application/json").content("{\"name\":\"기존 합성회원\",\"affiliation\":{\"source\":\"SELF_REPORTED\",\"schoolName\":\"합성대학\",\"departmentName\":\"합성학과\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affiliation.source").value("SELF_REPORTED"));
        mvc.perform(put("/api/auth/profile").header("Authorization",token).contentType("application/json").content("{\"name\":\"이름만 변경\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affiliation.schoolName").value("합성대학"));
        em.flush();em.refresh(user);assertEquals(hash,user.getPasswordHash());
    }
    @Test void affiliationWriteStillRequiresAuthentication() throws Exception {
        mvc.perform(put("/api/auth/profile").contentType("application/json").content("{\"name\":\"합성\"}")).andExpect(status().isUnauthorized());
    }
}
