package com.kyuhyeong.dashboard.repository;

import com.kyuhyeong.dashboard.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByVisibleTrueOrderBySortOrderAsc();

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.achievements WHERE p.slug = :slug")
    Optional<Project> findBySlug(String slug);
}
