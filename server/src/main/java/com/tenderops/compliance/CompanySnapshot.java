package com.tenderops.compliance;

import com.tenderops.company.Certificate;
import com.tenderops.company.FinancialRecord;
import com.tenderops.company.Project;
import java.util.List;

/** Company evidence visible to one evaluation run. */
public record CompanySnapshot(List<FinancialRecord> financials, List<Project> projects,
    List<Certificate> certificates) {
}
