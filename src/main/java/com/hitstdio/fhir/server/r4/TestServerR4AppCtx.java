package com.hitstdio.fhir.server.r4;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.hitstdio.fhir.server.provider.BundleResourceProvider;
import com.hitstdio.fhir.server.provider.CodeSystemResourceProvider;
import com.hitstdio.fhir.server.provider.ConceptMapResourceProvider;
import com.hitstdio.fhir.server.provider.TerminologyCapabilitiesResourceProvider;
import com.hitstdio.fhir.server.provider.ValueSetResourceProvider;
import com.hitstdio.fhir.server.provider.ValueSetResourceProvider;
import com.hitstdio.fhir.server.provider.StructureDefinitionResourceProvider;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Import({
	TestJpaR4Config.class
})
public class TestServerR4AppCtx {
	private final DaoRegistry myDaoRegistry;
	
	@Autowired
	public TestServerR4AppCtx(DaoRegistry theDaoRegistry) {
		this.myDaoRegistry = theDaoRegistry;
	}

	@Bean(name = "resourceProviders")
	public List<IResourceProvider> resourceProviders() {
		List<IResourceProvider> retVal = new ArrayList<>();
		retVal.add(new BundleResourceProvider(myDaoRegistry));
		retVal.add(new CodeSystemResourceProvider(myDaoRegistry));
		retVal.add(new ConceptMapResourceProvider(myDaoRegistry));
		retVal.add(new TerminologyCapabilitiesResourceProvider(myDaoRegistry));
		retVal.add(new ValueSetResourceProvider(myDaoRegistry));
		retVal.add(new StructureDefinitionResourceProvider(myDaoRegistry));
		return retVal;
	}

	@Bean(name = "plainProviders")
	public List<Object> plainProviders() {
		List<Object> retVal = new ArrayList<>();
		retVal.add(new SystemProvider());
		return retVal;
	}

}
