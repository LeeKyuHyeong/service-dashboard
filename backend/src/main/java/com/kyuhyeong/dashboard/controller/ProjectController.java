package com.kyuhyeong.dashboard.controller;

import com.kyuhyeong.dashboard.dto.ProjectDetailDto;
import com.kyuhyeong.dashboard.dto.ProjectListDto;
import com.kyuhyeong.dashboard.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;

    @GetMapping
    public List<ProjectListDto> listProjects() {
        return projectRepository.findAllByVisibleTrueOrderBySortOrderAsc()
                .stream()
                .map(ProjectListDto::from)
                .toList();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProjectDetailDto> getProject(@PathVariable String slug) {
        return projectRepository.findBySlug(slug)
                .map(ProjectDetailDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
