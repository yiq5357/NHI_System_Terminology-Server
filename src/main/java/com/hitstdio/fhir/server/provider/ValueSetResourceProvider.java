package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.*;

import com.hitstdio.fhir.server.util.ExpansionRequest;
import com.hitstdio.fhir.server.util.ValueSetExpansionService;

import java.util.List;

/**
 * HAPI FHIR Resource Provider for the ValueSet resource.
 * Implements the $expand operation for expanding value sets.
 */
public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {

    private final ValueSetExpansionService expansionService;
    
    public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        IFhirResourceDao<ValueSet> valueSetDao = theDaoRegistry.getResourceDao(ValueSet.class);
        IFhirResourceDao<CodeSystem> codeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
        
        this.expansionService = new ValueSetExpansionService(valueSetDao, codeSystemDao);
    }

    @Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }

    /**
     * Handles the type-level $expand operation: /ValueSet/$expand
     */
    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSetTypeLevel(
            @OperationParam(name = "url") UriType theUrl,
            @OperationParam(name = "valueSetVersion") StringType theValueSetVersion,
            @OperationParam(name = "filter") StringType theFilter,
            @OperationParam(name = "date") DateType theDate,
            @OperationParam(name = "offset") IntegerType theOffset,
            @OperationParam(name = "count") IntegerType theCount,
            @OperationParam(name = "includeDesignations") BooleanType theIncludeDesignations,
            @OperationParam(name = "designation") List<StringType> theDesignation,
            @OperationParam(name = "includeDefinition") BooleanType theIncludeDefinition,
            @OperationParam(name = "activeOnly") BooleanType theActiveOnly,
            @OperationParam(name = "excludeNested") BooleanType theExcludeNested,
            @OperationParam(name = "excludeNotForUI") BooleanType theExcludeNotForUI,
            @OperationParam(name = "displayLanguage") CodeType theDisplayLanguageParam,
            @OperationParam(name = "exclude-system") List<CanonicalType> theExcludeSystem,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,
            @OperationParam(name = "property") List<CodeType> theProperty,
            @ResourceParam Parameters theParameters,
            RequestDetails requestDetails) {

        ExpansionRequest request = ExpansionRequest.builder()
                .url(theUrl)
                .valueSetVersion(theValueSetVersion)
                .filter(theFilter)
                .date(theDate)
                .offset(theOffset)
                .count(theCount)
                .includeDesignations(theIncludeDesignations)
                .designation(theDesignation)
                .includeDefinition(theIncludeDefinition)
                .activeOnly(theActiveOnly)
                .excludeNested(theExcludeNested)
                .excludeNotForUI(theExcludeNotForUI)
                .displayLanguage(theDisplayLanguageParam)
                .excludeSystem(theExcludeSystem)
                .systemVersion(theSystemVersion)
                .checkSystemVersion(theCheckSystemVersion)
                .forceSystemVersion(theForceSystemVersion)
                .property(theProperty)
                .parameters(theParameters)
                .requestDetails(requestDetails)
                .build();

        return expansionService.expand(request);
    }

    /**
     * Handles the instance-level $expand operation: /ValueSet/[id]/$expand
     */
    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSetInstanceLevel(
            @IdParam IdType theId,
            @OperationParam(name = "url") UriType theUrl,
            @OperationParam(name = "valueSetVersion") StringType theValueSetVersion,
            @OperationParam(name = "filter") StringType theFilter,
            @OperationParam(name = "date") DateType theDate,
            @OperationParam(name = "offset") IntegerType theOffset,
            @OperationParam(name = "count") IntegerType theCount,
            @OperationParam(name = "includeDesignations") BooleanType theIncludeDesignations,
            @OperationParam(name = "designation") List<StringType> theDesignation,
            @OperationParam(name = "includeDefinition") BooleanType theIncludeDefinition,
            @OperationParam(name = "activeOnly") BooleanType theActiveOnly,
            @OperationParam(name = "excludeNested") BooleanType theExcludeNested,
            @OperationParam(name = "excludeNotForUI") BooleanType theExcludeNotForUI,
            @OperationParam(name = "displayLanguage") CodeType theDisplayLanguageParam,
            @OperationParam(name = "exclude-system") List<CanonicalType> theExcludeSystem,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,
            @OperationParam(name = "property") List<CodeType> theProperty,
            @ResourceParam Parameters theParameters,
            RequestDetails requestDetails) {

        ExpansionRequest request = ExpansionRequest.builder()
                .id(theId)
                .url(theUrl)
                .valueSetVersion(theValueSetVersion)
                .filter(theFilter)
                .date(theDate)
                .offset(theOffset)
                .count(theCount)
                .includeDesignations(theIncludeDesignations)
                .designation(theDesignation)
                .includeDefinition(theIncludeDefinition)
                .activeOnly(theActiveOnly)
                .excludeNested(theExcludeNested)
                .excludeNotForUI(theExcludeNotForUI)
                .displayLanguage(theDisplayLanguageParam)
                .excludeSystem(theExcludeSystem)
                .systemVersion(theSystemVersion)
                .checkSystemVersion(theCheckSystemVersion)
                .forceSystemVersion(theForceSystemVersion)
                .property(theProperty)
                .parameters(theParameters)
                .requestDetails(requestDetails)
                .build();

        return expansionService.expand(request);
    }
}