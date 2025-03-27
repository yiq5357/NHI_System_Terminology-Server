package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Bundle;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class BundleResourceProvider extends BaseResourceProvider<Bundle> {
	
    public BundleResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Bundle> getResourceType() {
        return Bundle.class;
    }

}