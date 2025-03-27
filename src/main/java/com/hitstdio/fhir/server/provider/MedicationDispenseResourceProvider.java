package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.MedicationDispense;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class MedicationDispenseResourceProvider extends BaseResourceProvider<MedicationDispense> {
	
    public MedicationDispenseResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<MedicationDispense> getResourceType() {
        return MedicationDispense.class;
    }

}