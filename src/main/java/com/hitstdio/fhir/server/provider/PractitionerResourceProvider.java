package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Practitioner;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class PractitionerResourceProvider extends BaseResourceProvider<Practitioner> {
	
    public PractitionerResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Practitioner> getResourceType() {
        return Practitioner.class;
    }
    
}