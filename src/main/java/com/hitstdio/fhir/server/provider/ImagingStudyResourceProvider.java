package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.ImagingStudy;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ImagingStudyResourceProvider extends BaseResourceProvider<ImagingStudy> {
	
    public ImagingStudyResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<ImagingStudy> getResourceType() {
        return ImagingStudy.class;
    }

}