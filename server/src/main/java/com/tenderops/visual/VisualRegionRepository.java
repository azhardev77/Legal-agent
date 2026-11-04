package com.tenderops.visual;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisualRegionRepository extends JpaRepository<VisualRegion, Long> {

  List<VisualRegion> findByPageIdOrderById(Long pageId);
}
