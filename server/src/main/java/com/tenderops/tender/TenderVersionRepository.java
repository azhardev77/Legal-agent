package com.tenderops.tender;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderVersionRepository extends JpaRepository<TenderVersion, Long> {

  List<TenderVersion> findByTenderIdOrderByVersionNumber(Long tenderId);
}
