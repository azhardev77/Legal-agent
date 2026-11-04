package com.tenderops.company;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
  List<Certificate> findByCompanyIdOrderById(Long companyId);
}
