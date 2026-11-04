package com.tenderops.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

  List<DocumentPage> findByTenderVersionIdOrderByPageNo(Long tenderVersionId);

  long countByTenderVersionId(Long tenderVersionId);
}
