package com.tenderops.company;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  List<Project> findByCompanyIdOrderById(Long companyId);
}
