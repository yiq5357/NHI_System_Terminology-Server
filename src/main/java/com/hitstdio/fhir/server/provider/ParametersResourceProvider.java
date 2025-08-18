package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import org.hl7.fhir.r4.model.*;

public class ParametersResourceProvider extends BaseResourceProvider<Parameters> {

	public ParametersResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}
	
	@Override
    public Class<Parameters> getResourceType() {
        return Parameters.class;
    }
}