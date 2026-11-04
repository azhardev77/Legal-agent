package com.tenderops.compliance;

import com.tenderops.company.CertificateRepository;
import com.tenderops.company.Company;
import com.tenderops.company.CompanyRepository;
import com.tenderops.company.FinancialRecordRepository;
import com.tenderops.company.ProjectRepository;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.tender.TenderRequirement;
import com.tenderops.tender.TenderRequirementRepository;
import com.tenderops.tender.TenderVersion;
import com.tenderops.tender.TenderVersionRepository;
import com.tenderops.web.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates every requirement of one tender version against one company in stable
 * order, then derives the decision, readiness score and risks. Pure read path;
 * run persistence, events and amendments belong to M6.
 */
@Service
public class ComplianceService {

  private final TenderVersionRepository versions;
  private final TenderRequirementRepository requirements;
  private final CompanyRepository companies;
  private final FinancialRecordRepository financials;
  private final ProjectRepository projects;
  private final CertificateRepository certificates;
  private final RequirementEvaluator evaluator;
  private final TenderopsProperties properties;

  public ComplianceService(TenderVersionRepository versions,
      TenderRequirementRepository requirements, CompanyRepository companies,
      FinancialRecordRepository financials, ProjectRepository projects,
      CertificateRepository certificates, RequirementEvaluator evaluator,
      TenderopsProperties properties) {
    this.versions = versions;
    this.requirements = requirements;
    this.companies = companies;
    this.financials = financials;
    this.projects = projects;
    this.certificates = certificates;
    this.evaluator = evaluator;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public ComplianceOutcome evaluate(long versionId, long companyId, Clock clock) {
    TenderVersion version = versions.findById(versionId).orElseThrow(
        () -> new NotFoundException("tender version " + versionId + " not found"));
    Company company = companies.findById(companyId).orElseThrow(
        () -> new NotFoundException("company " + companyId + " not found"));
    List<TenderRequirement> rows =
        requirements.findByTenderVersionIdOrderById(versionId);
    CompanySnapshot snapshot = new CompanySnapshot(
        financials.findByCompanyIdOrderByFyEndDateDesc(companyId),
        projects.findByCompanyIdOrderById(companyId),
        certificates.findByCompanyIdOrderById(companyId));
    EvaluationContext context = EvaluationContext.of(version.getSubmissionDeadline(),
        version.getEstimatedValue(), properties.ruleVersion(), company.getDataVersion());
    List<EvaluatedRequirement> results = new ArrayList<>();
    for (TenderRequirement row : rows) {
      results.add(evaluator.evaluate(row, snapshot, context));
    }
    Decision decision = BidDecisionEngine.decide(results);
    BigDecimal score = ReadinessScore.of(results);
    List<RiskFinding> risks =
        RiskEngine.assess(results, version.getSubmissionDeadline(), clock);
    return new ComplianceOutcome(results, decision, score, risks, context.contextHash(),
        context.ruleVersion(), context.companyDataVersion());
  }
}
