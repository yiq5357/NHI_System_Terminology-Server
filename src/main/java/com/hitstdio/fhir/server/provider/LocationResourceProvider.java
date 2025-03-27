package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Location;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class LocationResourceProvider extends BaseResourceProvider<Location> {
	
    public LocationResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Location> getResourceType() {
        return Location.class;
    }

}