package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class NamingSystemResourceProvider extends BaseResourceProvider<NamingSystem> {
	
    public NamingSystemResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<NamingSystem> getResourceType() {
        return NamingSystem.class;
    }

}