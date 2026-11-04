package com.tenderops.tender;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenderRepository extends JpaRepository<Tender, Long> {

  List<Tender> findByCompanyIdOrderByIdDesc(Long companyId);

  Optional<Tender> findByIdAndCompanyId(Long id, Long companyId);

  /** SHA-256 dedup (TC-ING-02): an identical upload returns the existing version, no reprocessing. */
  @Query("SELECT v FROM TenderVersion v JOIN Tender t ON v.tenderId = t.id "
      + "WHERE t.companyId = :companyId AND v.sha256 = :sha256 ORDER BY v.id")
  List<TenderVersion> findVersionsByCompanyAndSha256(Long companyId, String sha256);
}
