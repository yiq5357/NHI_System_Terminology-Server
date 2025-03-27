package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.AllergyIntolerance;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class AllergyIntoleranceResourceProvider extends BaseResourceProvider<AllergyIntolerance> {
	
    public AllergyIntoleranceResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<AllergyIntolerance> getResourceType() {
        return AllergyIntolerance.class;
    }

}