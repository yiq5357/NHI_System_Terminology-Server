package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.PractitionerRole;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class PractitionerRoleResourceProvider extends BaseResourceProvider<PractitionerRole> {
	
    public PractitionerRoleResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<PractitionerRole> getResourceType() {
        return PractitionerRole.class;
    }
    
}