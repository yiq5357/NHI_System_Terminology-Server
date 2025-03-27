package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Observation;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ObservationResourceProvider extends BaseResourceProvider<Observation> {

    public ObservationResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Observation> getResourceType() {
        return Observation.class;
    }

}