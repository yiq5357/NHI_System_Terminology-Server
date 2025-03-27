package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Specimen;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class SpecimenResourceProvider extends BaseResourceProvider<Specimen> {
	
    public SpecimenResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Specimen> getResourceType() {
        return Specimen.class;
    }
    
}