package com.tenderops.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Insert-only audit writer (R11). No update/delete code path exists. */
@Service
public class AuditService {

  private final AuditLogRepository repository;
  private final ObjectMapper objectMapper;

  public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AuditLog record(Long companyId, Long userId, String action, String entityType,
      Long entityId, Object before, Object after) {
    return repository.save(new AuditLog(companyId, userId, action, entityType, entityId,
        toJson(before), toJson(after)));
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "{\"error\":\"unserializable\"}";
    }
  }
}
