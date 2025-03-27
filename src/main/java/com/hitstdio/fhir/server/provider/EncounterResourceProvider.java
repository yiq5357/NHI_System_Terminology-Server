package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Encounter;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class EncounterResourceProvider extends BaseResourceProvider<Encounter> {
	
    public EncounterResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Encounter> getResourceType() {
        return Encounter.class;
    }

}