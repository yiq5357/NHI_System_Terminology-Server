package com.hitstdio.fhir.server.provider;

import org.hl7.fhir.r4.model.Procedure;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;

public final class ProcedureResourceProvider extends BaseResourceProvider<Procedure> {
	
    public ProcedureResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}

	@Override
    public Class<Procedure> getResourceType() {
        return Procedure.class;
    }
    
}