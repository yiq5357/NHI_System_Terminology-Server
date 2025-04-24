package com.hitstdio.fhir.server.r4;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.hitstdio.fhir.server.provider.AllergyIntoleranceResourceProvider;
import com.hitstdio.fhir.server.provider.BundleResourceProvider;
import com.hitstdio.fhir.server.provider.CodeSystemResourceProvider;
import com.hitstdio.fhir.server.provider.ConditionResourceProvider;
import com.hitstdio.fhir.server.provider.DeviceResourceProvider;
import com.hitstdio.fhir.server.provider.DiagnosticReportResourceProvider;
import com.hitstdio.fhir.server.provider.DocumentReferenceResourceProvider;
import com.hitstdio.fhir.server.provider.EncounterResourceProvider;
import com.hitstdio.fhir.server.provider.ImagingStudyResourceProvider;
import com.hitstdio.fhir.server.provider.LocationResourceProvider;
import com.hitstdio.fhir.server.provider.MediaResourceProvider;
import com.hitstdio.fhir.server.provider.CompositionResourceProvider;
import com.hitstdio.fhir.server.provider.ConceptMapResourceProvider;
import com.hitstdio.fhir.server.provider.MedicationDispenseResourceProvider;
import com.hitstdio.fhir.server.provider.MedicationRequestResourceProvider;
import com.hitstdio.fhir.server.provider.MedicationResourceProvider;
import com.hitstdio.fhir.server.provider.MedicationStatementResourceProvider;
import com.hitstdio.fhir.server.provider.MessageHeaderResourceProvider;
import com.hitstdio.fhir.server.provider.NamingSystemResourceProvider;
import com.hitstdio.fhir.server.provider.ObservationResourceProvider;
import com.hitstdio.fhir.server.provider.OrganizationResourceProvider;
import com.hitstdio.fhir.server.provider.PatientResourceProvider;
import com.hitstdio.fhir.server.provider.PractitionerResourceProvider;
import com.hitstdio.fhir.server.provider.PractitionerRoleResourceProvider;
import com.hitstdio.fhir.server.provider.ProcedureResourceProvider;
import com.hitstdio.fhir.server.provider.SpecimenResourceProvider;
import com.hitstdio.fhir.server.provider.TerminologyCapabilitiesResourceProvider;

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
		retVal.add(new AllergyIntoleranceResourceProvider(myDaoRegistry));
		retVal.add(new BundleResourceProvider(myDaoRegistry));
		retVal.add(new CompositionResourceProvider(myDaoRegistry));
		retVal.add(new ConditionResourceProvider(myDaoRegistry));
		retVal.add(new DeviceResourceProvider(myDaoRegistry));
		retVal.add(new DiagnosticReportResourceProvider(myDaoRegistry));
		retVal.add(new DocumentReferenceResourceProvider(myDaoRegistry));
		retVal.add(new EncounterResourceProvider(myDaoRegistry));
		retVal.add(new ImagingStudyResourceProvider(myDaoRegistry));
		retVal.add(new LocationResourceProvider(myDaoRegistry));
		retVal.add(new MediaResourceProvider(myDaoRegistry));
		retVal.add(new MedicationDispenseResourceProvider(myDaoRegistry));
		retVal.add(new MedicationRequestResourceProvider(myDaoRegistry));
		retVal.add(new MedicationResourceProvider(myDaoRegistry));
		retVal.add(new MedicationStatementResourceProvider(myDaoRegistry));
		retVal.add(new MessageHeaderResourceProvider(myDaoRegistry));
		retVal.add(new ObservationResourceProvider(myDaoRegistry));
		retVal.add(new OrganizationResourceProvider(myDaoRegistry));
		retVal.add(new PatientResourceProvider(myDaoRegistry));
		retVal.add(new PractitionerResourceProvider(myDaoRegistry));
		retVal.add(new PractitionerRoleResourceProvider(myDaoRegistry));
		retVal.add(new ProcedureResourceProvider(myDaoRegistry));
		retVal.add(new SpecimenResourceProvider(myDaoRegistry));
		retVal.add(new CodeSystemResourceProvider(myDaoRegistry));
		retVal.add(new NamingSystemResourceProvider(myDaoRegistry));
		retVal.add(new ConceptMapResourceProvider(myDaoRegistry));
		retVal.add(new TerminologyCapabilitiesResourceProvider(myDaoRegistry));
		return retVal;
	}

	@Bean(name = "plainProviders")
	public List<Object> plainProviders() {
		List<Object> retVal = new ArrayList<>();
		retVal.add(new SystemProvider());
		return retVal;
	}

}
