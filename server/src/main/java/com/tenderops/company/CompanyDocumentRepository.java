package com.tenderops.company;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {
  List<CompanyDocument> findByCompanyIdOrderById(Long companyId);

  Optional<CompanyDocument> findByCompanyIdAndSha256(Long companyId, String sha256);
}
