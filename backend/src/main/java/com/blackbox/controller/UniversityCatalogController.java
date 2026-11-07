package com.blackbox.controller;

import com.blackbox.service.UniversityCatalogService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth/affiliation")
public class UniversityCatalogController {
    private final UniversityCatalogService catalog;
    public UniversityCatalogController(UniversityCatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/schools") public UniversityCatalogService.SchoolList schools() { return catalog.list(); }
    @GetMapping("/schools/{schoolId}/departments")
    public List<UniversityCatalogService.Department> departments(@PathVariable String schoolId) { return catalog.departments(schoolId); }
}
