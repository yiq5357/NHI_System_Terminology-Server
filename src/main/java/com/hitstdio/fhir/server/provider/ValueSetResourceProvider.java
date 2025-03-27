package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {
	
    public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }

}