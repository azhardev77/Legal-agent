package com.tenderops.visual;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

  List<AiCallLog> findByComponentOrderById(String component);
}
