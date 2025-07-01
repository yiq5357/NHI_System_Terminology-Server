package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import org.hl7.fhir.r4.model.*;

public class StructureDefinitionResourceProvider extends BaseResourceProvider<StructureDefinition> {

	public StructureDefinitionResourceProvider(DaoRegistry theDaoRegistry) {
		super(theDaoRegistry);
	}
	
	
	
	@Override
    public Class<StructureDefinition> getResourceType() {
        return StructureDefinition.class;
    }
}