package com.hitstdio.fhir.server.provider;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.RestfulServer;

public class TerminologyCapabilitiesResourceProvider extends BaseResourceProvider<TerminologyCapabilities> {

	 private final IFhirResourceDao<CodeSystem> codeSystemDao;

	    public TerminologyCapabilitiesResourceProvider(DaoRegistry theDaoRegistry) {
	        super(theDaoRegistry);
	        this.codeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
	    }

	    @Operation(name = "$metadata", idempotent = true)
	    public TerminologyCapabilities getTerminologyCapabilities(
	        @OperationParam(name = "mode") String mode,
	        RequestDetails requestDetails
	    ) {
	        if (mode != null && mode.equals("terminology")) {
	            TerminologyCapabilities terminologyCapabilities = new TerminologyCapabilities();

	            // �]�w���ݩ�
	            terminologyCapabilities.setVersion("1.0.0");
	            terminologyCapabilities.setStatus(Enumerations.PublicationStatus.ACTIVE);
	            terminologyCapabilities.setUrl("localhost:8085/metadata?mode=terminology");
	            terminologyCapabilities.setName("TerminologyCapabilities");
	            terminologyCapabilities.setTitle("Terminology Capabilities Statement");
	            terminologyCapabilities.setDate(new Date());
	            terminologyCapabilities.setKind(TerminologyCapabilities.CapabilityStatementKind.CAPABILITY);
	            terminologyCapabilities.setPublisher("Your Organization");

	            TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent expansion = new TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent();
	            terminologyCapabilities.setExpansion(expansion);
	            
	            List<String> parameterNames = java.util.Arrays.asList(
	                    "activeOnly", "count", "displayLanguage", "excludeNested",
	                    "force-system-version", "includeDefinition", "includeDesignations",
	                    "offset", "property", "system-version", "tx-resource"
	                );

	            for (String paramName : parameterNames) {
                    TerminologyCapabilities.TerminologyCapabilitiesExpansionParameterComponent parameterComponent = 
                        new TerminologyCapabilities.TerminologyCapabilitiesExpansionParameterComponent();
                    parameterComponent.setName(paramName);
                    
                    expansion.addParameter(parameterComponent);
                }

	            
	            
	            // �q��Ʈw���X�Ҧ�CodeSystem�귽
	            SearchParameterMap searchParams = new SearchParameterMap();
	            IBundleProvider results = codeSystemDao.search(searchParams, requestDetails);
	            List<IBaseResource> codeSystemResources = results.getResources(0, results.size());

	            // �ھڼ��X�Ӫ�CodeSystem�]�wTerminologyCapabilities
	            for (IBaseResource resource : codeSystemResources) {
	                if (resource instanceof CodeSystem) {
	                    CodeSystem cs = (CodeSystem) resource;
	                    if (cs.getUrl() != null) {
	                    	terminologyCapabilities.addCodeSystem()
	                        .setUri(cs.getUrl())
	                        .setVersion(Collections.singletonList(
	                            new TerminologyCapabilities.TerminologyCapabilitiesCodeSystemVersionComponent()
	                                .setCode(cs.getVersion())
	                        ));
	                    }
	                }
	            }

	            return terminologyCapabilities;
	        }

	        return null;
	    }

	    @Override
	    public Class<TerminologyCapabilities> getResourceType() {
	        return TerminologyCapabilities.class;
	    }
	}

