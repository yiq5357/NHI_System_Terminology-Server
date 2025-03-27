package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.MedicationRequest;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class MedicationRequestResourceProvider extends BaseResourceProvider<MedicationRequest> {
	
    public MedicationRequestResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<MedicationRequest> getResourceType() {
        return MedicationRequest.class;
    }

}