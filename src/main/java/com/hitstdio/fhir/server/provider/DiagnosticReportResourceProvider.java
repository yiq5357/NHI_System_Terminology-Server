package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.DiagnosticReport;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class DiagnosticReportResourceProvider extends BaseResourceProvider<DiagnosticReport> {
	
    public DiagnosticReportResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<DiagnosticReport> getResourceType() {
        return DiagnosticReport.class;
    }

}