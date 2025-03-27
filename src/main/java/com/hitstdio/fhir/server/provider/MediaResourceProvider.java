package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Media;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class MediaResourceProvider extends BaseResourceProvider<Media> {
	
    public MediaResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Media> getResourceType() {
        return Media.class;
    }

}