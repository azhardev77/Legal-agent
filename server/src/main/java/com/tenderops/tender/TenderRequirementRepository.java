package com.tenderops.tender;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderRequirementRepository extends JpaRepository<TenderRequirement, Long> {

  List<TenderRequirement> findByTenderVersionIdOrderById(Long tenderVersionId);

  long countByTenderVersionId(Long tenderVersionId);

  void deleteByTenderVersionId(Long tenderVersionId);
}
