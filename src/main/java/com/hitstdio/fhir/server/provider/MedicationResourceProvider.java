package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Medication;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;


public final class MedicationResourceProvider extends BaseResourceProvider<Medication> {

    public MedicationResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Medication> getResourceType() {
        return Medication.class;
    }

}