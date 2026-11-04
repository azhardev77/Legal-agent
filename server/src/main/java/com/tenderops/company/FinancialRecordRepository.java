package com.tenderops.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {
  List<FinancialRecord> findByCompanyIdOrderByFyEndDateDesc(Long companyId);

  Optional<FinancialRecord> findByCompanyIdAndFyLabel(Long companyId, String fyLabel);
}
