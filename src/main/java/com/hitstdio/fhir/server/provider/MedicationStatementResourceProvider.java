package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.MedicationStatement;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class MedicationStatementResourceProvider extends BaseResourceProvider<MedicationStatement> {
	
    public MedicationStatementResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<MedicationStatement> getResourceType() {
        return MedicationStatement.class;
    }

}