package com.hitstdio.fhir.server.provider;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceDesignationComponent;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.param.TokenParam;


public final class ValueSetResourceProvider extends BaseResourceProvider<ValueSet> {
	
	private final IFhirResourceDao<ValueSet> valueSetDao;
	private final IFhirResourceDao<CodeSystem> codeSystemDao;

	public ValueSetResourceProvider(DaoRegistry theDaoRegistry) {
	    super(theDaoRegistry);
	    this.valueSetDao = theDaoRegistry.getResourceDao(ValueSet.class);
	    this.codeSystemDao = theDaoRegistry.getResourceDao(CodeSystem.class);
	}

    @Operation(name = "$expand", idempotent = true)
    public ValueSet expandValueSet(
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
            //@OperationParam(name = "excludePostCoordinated") BooleanType theExcludePostCoordinated,
            @OperationParam(name = "displayLanguage") CodeType theDisplayLanguageParam,
            @OperationParam(name = "exclude-system") List<CanonicalType> theExcludeSystem,
            @OperationParam(name = "system-version") List<UriType> theSystemVersion,
            @OperationParam(name = "check-system-version") List<UriType> theCheckSystemVersion,
            @OperationParam(name = "force-system-version") List<UriType> theForceSystemVersion,
            RequestDetails requestDetails) {
    	//暫不實做
    	BooleanType theExcludePostCoordinated = null;

        String displayLanguageValue = null;
        if (theDisplayLanguageParam != null && theDisplayLanguageParam.hasValue()) {
            displayLanguageValue = theDisplayLanguageParam.getValueAsString();
        } else {
            String acceptLanguageHeader = requestDetails.getHeader("Accept-Language");
            if (acceptLanguageHeader != null && !acceptLanguageHeader.trim().isEmpty()) {
                String[] languages = acceptLanguageHeader.split(",");
                if (languages.length > 0) {
                    displayLanguageValue = languages[0].split(";")[0].trim();
                }
            }
        }    	
    	
    	
    	ValueSet valueSet = retrieveValueSet(theUrl, theValueSetVersion, requestDetails);

        Map<String, String> systemVersionMap = parseSystemVersions(theSystemVersion);
        Map<String, String> checkSystemVersionMap = parseSystemVersions(theCheckSystemVersion);
        Map<String, String> forceSystemVersionMap = parseSystemVersions(theForceSystemVersion);        
        
        // Page
        int offset = theOffset != null ? theOffset.getValue() : 0;
        int count = theCount != null ? theCount.getValue() : 0;
        
        if (offset < 0) {
            throw new InvalidRequestException("Offset must be >= 0");
        }
        if (count < 0) {
            throw new InvalidRequestException("Count must be >= 0");
        }

        Set<String> excludedSystemsSet = new java.util.HashSet<>(); 
        if (theExcludeSystem != null) {
            for (CanonicalType excludedSys : theExcludeSystem) {
                if (excludedSys != null && excludedSys.hasValue()) {
                    excludedSystemsSet.add(excludedSys.getValueAsString());
                }
            }
        }               
        
        ValueSet result = new ValueSet();
        
        if (theIncludeDefinition != null && theIncludeDefinition.getValue()) {
            result.setMeta(valueSet.getMeta());
            result.setUrl(valueSet.getUrl());
            result.setIdentifier(valueSet.getIdentifier());
            result.setVersion(valueSet.getVersion());
            result.setName(valueSet.getName());
            result.setTitle(valueSet.getTitle());
            result.setStatus(valueSet.getStatus());
            result.setExperimental(valueSet.getExperimental());
            result.setDate(valueSet.getDate());
            result.setPublisher(valueSet.getPublisher());
            result.setContact(valueSet.getContact());
            result.setDescription(valueSet.getDescription());
            result.setUseContext(valueSet.getUseContext());
            result.setJurisdiction(valueSet.getJurisdiction());
            result.setImmutable(valueSet.getImmutable());
            result.setPurpose(valueSet.getPurpose());
            result.setCopyright(valueSet.getCopyright());
            result.setCompose(valueSet.getCompose());
            result.setExtension(valueSet.getExtension());
        } else {
            result.setMeta(valueSet.getMeta());
            result.setUrl(valueSet.getUrl());
            result.setIdentifier(valueSet.getIdentifier());
            result.setVersion(valueSet.getVersion());
            result.setName(valueSet.getName());
            result.setTitle(valueSet.getTitle());
            result.setStatus(valueSet.getStatus());
            result.setExperimental(valueSet.getExperimental());
            result.setDate(valueSet.getDate());
            result.setImmutable(valueSet.getImmutable());
        }
        
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        result.setExpansion(expansion);
        expansion.setTimestamp(new Date());
        expansion.setIdentifier("urn:uuid:" + UUID.randomUUID().toString());

        addExpansionParameters(expansion, theUrl, theValueSetVersion, theFilter, theDate,
                theOffset, theCount, theIncludeDesignations, theDesignation, theIncludeDefinition,
                theActiveOnly, theExcludeNested, theExcludeNotForUI, theExcludePostCoordinated,
                theDisplayLanguageParam, theExcludeSystem, theSystemVersion, theCheckSystemVersion,
                theForceSystemVersion, requestDetails);        
        
        List<ValueSet.ValueSetExpansionContainsComponent> allCodes = new ArrayList<>();
        
        if (valueSet.hasCompose()) { 
            for (ValueSet.ConceptSetComponent include : valueSet.getCompose().getInclude()) {
                String system = include.getSystem();

                if (excludedSystemsSet.contains(system)) {
                    continue; 
                }
                
                String version = systemVersion(system, include.getVersion(), 
                        systemVersionMap, checkSystemVersionMap, forceSystemVersionMap);

                CodeSystem codeSystem = findCodeSystem(system, version, requestDetails); 
                
                String usedCodeSystemUrl = system;
                if (codeSystem != null && codeSystem.hasVersion()) {
                    if (version == null) version = codeSystem.getVersion(); 
                }
                if (version != null && !version.trim().isEmpty()) {
                    usedCodeSystemUrl += "|" + version;
                }
                expansion.addParameter().setName("used-codesystem").setValue(new UriType(usedCodeSystemUrl));

                if (version != null) {
                     expansion.addParameter().setName("version") 
                         .setValue(new UriType(system + "|" + version));
                }
                
                if (codeSystem != null) {
                    addCodesFromCodeSystem(allCodes, codeSystem, include, theFilter,
                            theExcludeNested, theIncludeDesignations,
                            theActiveOnly, theExcludeNotForUI,
                            theDesignation,
                            displayLanguageValue);
                }
            }
        }

        
        expansion.setTotal(allCodes.size());
        
        // Page
        List<ValueSet.ValueSetExpansionContainsComponent> pagedCodes = applyPaging(allCodes, offset, count);
        for (ValueSet.ValueSetExpansionContainsComponent code : pagedCodes) {
            expansion.addContains(code);
        }
        return result;
    }

    // Page
    private List<ValueSet.ValueSetExpansionContainsComponent> applyPaging(
            List<ValueSet.ValueSetExpansionContainsComponent> allCodes, int offset, int count) {
        
        if (allCodes.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (count == 0) {
            return allCodes;
        }
        
        int totalSize = allCodes.size();
        
        if (offset >= totalSize) {
            return new ArrayList<>();
        }
        
        int endIndex = Math.min(offset + count, totalSize);
        
        return allCodes.subList(offset, endIndex);
    }
    
    
    private void addExpansionParameters(ValueSet.ValueSetExpansionComponent expansion, UriType theUrl, StringType theValueSetVersion, StringType theFilter, DateType theDate, IntegerType theOffset, IntegerType theCount, BooleanType theIncludeDesignations, List < StringType > theDesignation, BooleanType theIncludeDefinition, BooleanType theActiveOnly, BooleanType theExcludeNested, BooleanType theExcludeNotForUI, BooleanType theExcludePostCoordinated, CodeType theDisplayLanguage, List < CanonicalType > theExcludeSystem, List < UriType > theSystemVersion, List < UriType > theCheckSystemVersion, List < UriType > theForceSystemVersion, RequestDetails requestDetails){
        if(theUrl != null && theUrl.hasValue()){
            expansion.addParameter().setName("url").setValue(theUrl);
        }
        
        if(theValueSetVersion != null && theValueSetVersion.hasValue()){
            expansion.addParameter().setName("valueSetVersion").setValue(theValueSetVersion);
        }
        
        if(theFilter != null && theFilter.hasValue()){
            expansion.addParameter().setName("filter").setValue(theFilter);
        }

        if(theDate != null && theDate.getValue() != null){
            expansion.addParameter().setName("date").setValue(new DateTimeType(theDate.getValue(), theDate.getPrecision(), theDate.getTimeZone()));
        }

        if(theOffset != null){ 
            expansion.addParameter().setName("offset").setValue(theOffset);
        }

        if(theCount != null){ 
            expansion.addParameter().setName("count").setValue(theCount);
        }

        if(theIncludeDesignations != null){
            expansion.addParameter().setName("includeDesignations").setValue(theIncludeDesignations);
        }

        if(theDesignation != null && !theDesignation.isEmpty())  {
            for(StringType designation: theDesignation) {
                if(designation != null && designation.hasValue()) {
                    expansion.addParameter().setName("designation").setValue(designation);
                }
            }
        }
        
        if(theIncludeDefinition != null){ 
            expansion.addParameter().setName("includeDefinition").setValue(theIncludeDefinition);
        }
 
        if(theActiveOnly != null) { 
            expansion.addParameter().setName("activeOnly").setValue(theActiveOnly);
        }

        if(theExcludeNested != null) {
            expansion.addParameter().setName("excludeNested").setValue(theExcludeNested);
        }
        
        if(theExcludeNotForUI != null) {
            expansion.addParameter().setName("excludeNotForUI").setValue(theExcludeNotForUI);
        }
        
        if(theExcludePostCoordinated != null){
            expansion.addParameter().setName("excludePostCoordinated").setValue(theExcludePostCoordinated);
        }
        
        String displayLanguageValue = null;
        if(theDisplayLanguage != null && theDisplayLanguage.hasValue()){
            displayLanguageValue = theDisplayLanguage.getValueAsString();
        }
        else {
            String acceptLanguageHeader = requestDetails.getHeader("Accept-Language");
            if(acceptLanguageHeader != null && !acceptLanguageHeader.trim().isEmpty()){
                String[] languages = acceptLanguageHeader.split(",");
                if(languages.length > 0){
                    displayLanguageValue = languages[0].split(";")[0].trim();
                }
            }
        }
        
        if(displayLanguageValue != null && !displayLanguageValue.isEmpty()){
            expansion.addParameter().setName("displayLanguage").setValue(new StringType(displayLanguageValue));
        }
        
        if(theExcludeSystem != null && !theExcludeSystem.isEmpty()){
            for(CanonicalType excludeSystem: theExcludeSystem){
                if(excludeSystem != null && excludeSystem.hasValue()){
                    expansion.addParameter().setName("exclude-system").setValue(excludeSystem);
                }
            }
        }
        
        if(theSystemVersion != null && !theSystemVersion.isEmpty()){
            for(UriType systemVersion: theSystemVersion){
                if(systemVersion != null && systemVersion.hasValue()){
                    expansion.addParameter().setName("system-version").setValue(systemVersion);
                }
            }
        }
        
        if(theCheckSystemVersion != null && !theCheckSystemVersion.isEmpty()){
            for(UriType checkSystemVersion: theCheckSystemVersion){
                if(checkSystemVersion != null && checkSystemVersion.hasValue()){
                    expansion.addParameter().setName("check-system-version").setValue(checkSystemVersion);
                }
            }
        }
        
        if(theForceSystemVersion != null && !theForceSystemVersion.isEmpty()){
            for(UriType forceSystemVersion: theForceSystemVersion){
                if(forceSystemVersion != null && forceSystemVersion.hasValue()){
                    expansion.addParameter().setName("force-system-version").setValue(forceSystemVersion);
                }
            }
        } 
    }

    private Map<String, String> parseSystemVersions(List<UriType> systemVersions) {
        Map<String, String> result = new HashMap<>();
        if (systemVersions != null) {
            for (UriType systemVersion : systemVersions) {
                String value = systemVersion.getValueAsString();
                if (value != null && value.contains("|")) {
                    String[] parts = value.split("\\|", 2);
                    if (parts.length > 1 && parts[1] != null && !parts[1].trim().isEmpty()) {
                        result.put(parts[0], parts[1]);
                    }
                }
            }
        }
        return result;
    }   

    private String systemVersion(String system, String includeVersion, 
            Map<String, String> systemVersionMap, 
            Map<String, String> checkSystemVersionMap,
            Map<String, String> forceSystemVersionMap) {
        
        if (forceSystemVersionMap.containsKey(system)) {
            return forceSystemVersionMap.get(system);
        }
        
        if (checkSystemVersionMap.containsKey(system)) {
            String expectedVersion = checkSystemVersionMap.get(system);
            if (includeVersion != null && !includeVersion.equals(expectedVersion)) {
                throw new UnprocessableEntityException(
                    "ValueSet specifies version '" + includeVersion + "' for system '" + system + 
                    "', but check-system-version requires version '" + expectedVersion + "'");
            }
            return expectedVersion;
        }
        
        if (includeVersion != null && !includeVersion.trim().isEmpty()) {
            return includeVersion;
        }
        
        if (systemVersionMap.containsKey(system)) {
            return systemVersionMap.get(system);
        }
        return null;
    }
    
    private ValueSet retrieveValueSet(
            UriType theUrl,
            StringType theValueSetVersion, 
            RequestDetails requestDetails) {

        if (theUrl == null || !theUrl.hasValue()) {
            if (requestDetails.getId() != null && requestDetails.getId().hasIdPart()) {
                IIdType id = requestDetails.getId();
                return valueSetDao.read(id, requestDetails);
            } else {
                throw new InvalidRequestException("Either 'url' parameter or a resource ID must be provided to identify the ValueSet for the $expand operation.");
            }
        }

        SearchParameterMap params = new SearchParameterMap();
        params.add(ValueSet.SP_URL, new UriParam(theUrl.getValueAsString())); 

        String versionString = null;
        if (theValueSetVersion != null && theValueSetVersion.hasValue()) {
            versionString = theValueSetVersion.getValue();
            params.add(ValueSet.SP_VERSION, new TokenParam(versionString)); 
        }

        IBundleProvider bundle = valueSetDao.search(params, requestDetails);

        if (bundle.isEmpty()) {
            String message = "ValueSet with URL '" + theUrl.getValueAsString() + "'";
            if (versionString != null) {
                message += " and version '" + versionString + "'";
            }
            message += " not found.";
            throw new ResourceNotFoundException(message);
        }
        
        return (ValueSet) bundle.getResources(0, 1).get(0);
    }

    private CodeSystem findCodeSystem(String system, String version, RequestDetails requestDetails) {
        SearchParameterMap params = new SearchParameterMap().add("url", new UriParam(system));
        if (version != null && !version.trim().isEmpty()) {
        	params.add("version", new TokenParam(version));
        }
        
        IBundleProvider results = codeSystemDao.search(params, requestDetails);
        if (!results.isEmpty()) {
            return (CodeSystem) results.getResources(0, 1).get(0);
        }
        return null;
    }

    
    //決定該如何從CS取Code (部分/全部)
    private void addCodesFromCodeSystem(List < ValueSet.ValueSetExpansionContainsComponent > allCodes,
            CodeSystem codeSystem,
            ValueSet.ConceptSetComponent include,
            StringType filter,
            BooleanType excludeNested,
            BooleanType includeDesignationsParam, 
            BooleanType activeOnly,
            BooleanType excludeNotForUI,
            List < StringType > designationFilters, 
            String displayLanguageValue) { 
            if(!include.getConcept().isEmpty()) {
                for(ValueSet.ConceptReferenceComponent conceptRef: include.getConcept()) {
                    CodeSystem.ConceptDefinitionComponent conceptDef = findConceptInCodeSystem(codeSystem, conceptRef.getCode());
                    if(conceptDef != null) { 
                        String displayForFilter = conceptDef.getDisplay(); 
                        if(displayLanguageValue != null && !displayLanguageValue.isEmpty() && conceptDef.hasDesignation()) {
                            for(CodeSystem.ConceptDefinitionDesignationComponent desg: conceptDef.getDesignation()) {
                                if(desg.hasLanguage() && displayLanguageValue.equals(desg.getLanguage()) && desg.hasValue()) {
                                    displayForFilter = desg.getValue();
                                    break;
                                }
                            }
                        }
                        if(matchesFilter(conceptRef.getCode(), displayForFilter, filter) && 
                            shouldIncludeConcept(conceptDef, activeOnly, excludeNotForUI)) {
                            addCodeToList(allCodes, include.getSystem(), conceptRef.getCode(),
                                conceptRef.getDisplay(), 
                                conceptDef,
                                includeDesignationsParam, designationFilters,
                                displayLanguageValue); 
                        }
                    }
                }
            } else {
                for(CodeSystem.ConceptDefinitionComponent concept: codeSystem.getConcept()) {
                    processCodeSystemConcept(allCodes, include.getSystem(), concept, filter,
                        codeSystem, excludeNested, includeDesignationsParam,
                        activeOnly, excludeNotForUI,
                        designationFilters,
                        displayLanguageValue);
                }
            }
        }

    private void processCodeSystemConcept(List < ValueSet.ValueSetExpansionContainsComponent > allCodes,
            String system,
            CodeSystem.ConceptDefinitionComponent conceptDef,
            StringType filter,
            CodeSystem codeSystem,
            BooleanType excludeNested,
            BooleanType includeDesignationsParam,
            BooleanType activeOnly,
            BooleanType excludeNotForUI,
            List < StringType > designationFilters,
            String displayLanguageValue) { 
            String displayForFilter = conceptDef.getDisplay(); 
            if(displayLanguageValue != null && !displayLanguageValue.isEmpty() && conceptDef.hasDesignation()) {
                for(CodeSystem.ConceptDefinitionDesignationComponent desg: conceptDef.getDesignation()) {
                    if(desg.hasLanguage() && displayLanguageValue.equals(desg.getLanguage()) && desg.hasValue()) {
                        displayForFilter = desg.getValue();
                        break;
                    }
                }
            }
            if(matchesFilter(conceptDef.getCode(), displayForFilter, filter) && 
                shouldIncludeConcept(conceptDef, activeOnly, excludeNotForUI)) {
                addCodeToList(allCodes, system, conceptDef.getCode(), conceptDef.getDisplay(),
                    conceptDef,
                    includeDesignationsParam, designationFilters,
                    displayLanguageValue); 
            }
            if(excludeNested == null || !excludeNested.getValue()) {
                for(CodeSystem.ConceptDefinitionComponent child: conceptDef.getConcept()) {
                    processCodeSystemConcept(allCodes, system, child, filter,
                        codeSystem, excludeNested, includeDesignationsParam,
                        activeOnly, excludeNotForUI,
                        designationFilters,
                        displayLanguageValue); 
                }
            }
        }


    // 比對
    private boolean matchesFilter(String code, String display, StringType filter) {
        if (filter == null || filter.getValue() == null || filter.getValue().isEmpty()) {
            return true;
        }
        
        String filterStr = filter.getValue().toLowerCase();
        return (code != null && code.toLowerCase().contains(filterStr)) ||
               (display != null && display.toLowerCase().contains(filterStr));
    }

    // 排除條件
    private boolean shouldIncludeConcept(CodeSystem.ConceptDefinitionComponent concept,
            BooleanType activeOnly,
            BooleanType excludeNotForUI) {
    	// 1. activeOnly
        if (activeOnly != null && activeOnly.getValue()) {
            boolean isActive = true; 
            for (CodeSystem.ConceptPropertyComponent property : concept.getProperty()) {
                if ("inactive".equals(property.getCode()) && property.getValue() instanceof BooleanType) {
                    BooleanType inactive = (BooleanType) property.getValue();
                    if (inactive.getValue()) { 
                        isActive = false; 
                        break;
                    }
                }
            }
            if (!isActive) {
                return false; 
            }
        }
        
        // 2. excludeNotForUI
        if (excludeNotForUI != null && excludeNotForUI.getValue()) { 
            for (org.hl7.fhir.r4.model.Extension ext : concept.getExtension()) {
                if ("http://hl7.org/fhir/StructureDefinition/codesystem-concept-not-for-ui".equals(ext.getUrl())) {
                    if (ext.getValue() instanceof BooleanType) {
                        BooleanType notForUI = (BooleanType) ext.getValue();
                        if (notForUI.getValue()) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;

    }

    private CodeSystem.ConceptDefinitionComponent findConceptInCodeSystem(CodeSystem codeSystem, String code) {
        if (codeSystem == null || code == null) {
            return null;
        }
        
        for (CodeSystem.ConceptDefinitionComponent concept : codeSystem.getConcept()) {
            CodeSystem.ConceptDefinitionComponent found = findConceptRecursive(concept, code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private CodeSystem.ConceptDefinitionComponent findConceptRecursive(
            CodeSystem.ConceptDefinitionComponent concept, String code) {
        if (code.equals(concept.getCode())) {
            return concept;
        }
        
        for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
            CodeSystem.ConceptDefinitionComponent found = findConceptRecursive(child, code);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void addDesignationsToComponent(ValueSet.ValueSetExpansionContainsComponent component,
            CodeSystem.ConceptDefinitionComponent conceptDef,
            List < StringType > designationFilters,
            String displayLanguageValue) {
            if(conceptDef.getDesignation().isEmpty()) {
                return;
            }
            for(CodeSystem.ConceptDefinitionDesignationComponent desg: conceptDef.getDesignation()) {
                if(shouldIncludeDesignation(desg, designationFilters, displayLanguageValue)) {
                    ConceptReferenceDesignationComponent expDesignation = component.addDesignation();
                    if(desg.hasLanguage()) {
                        expDesignation.setLanguage(desg.getLanguage());
                    }
                    if(desg.hasUse()) {
                        expDesignation.setUse(desg.getUse().copy());
                    }
                    if(desg.hasValue()) {
                        expDesignation.setValue(desg.getValue());
                    }
                }
            }
        }

    private boolean shouldIncludeDesignation(CodeSystem.ConceptDefinitionDesignationComponent designation,
            List < StringType > designationFilters,
            String displayLanguageValue) { 
            if(designationFilters != null && !designationFilters.isEmpty()) {
                for(StringType filter: designationFilters) {
                    String filterValue = filter.getValue();
                    if(filterValue == null) continue;
                    if(designation.hasLanguage() && filterValue.equals(designation.getLanguage())) {
                        return true;
                    }
                    if(designation.hasUse()) {
                        String useSystem = designation.getUse().getSystem();
                        String useCode = designation.getUse().getCode();
                        if(filterValue.equals(useCode)) {
                            return true;
                        }
                        if(useSystem != null && filterValue.equals(useSystem + "|" + useCode)) {
                            return true;
                        }
                    }
                }
                return false;
            } else {
                if(displayLanguageValue != null && !displayLanguageValue.isEmpty()) {
                    return displayLanguageValue.equals(designation.getLanguage());
                } else {
                    return true;
                }
            }
        }

    private void addCodeToList(List < ValueSet.ValueSetExpansionContainsComponent > allCodes,
            String system, String code, String fallbackDisplay, 
            CodeSystem.ConceptDefinitionComponent conceptDef,
            BooleanType includeDesignationsParam,
            List < StringType > designationFilters,
            String displayLanguageValue) { 
            ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent();
            component.setSystem(system);
            component.setCode(code);
            String finalDisplay = fallbackDisplay; 
            if(conceptDef != null) {
                finalDisplay = conceptDef.getDisplay(); 
                if(displayLanguageValue != null && !displayLanguageValue.isEmpty() && conceptDef.hasDesignation()) {
                    for(CodeSystem.ConceptDefinitionDesignationComponent desg: conceptDef.getDesignation()) {
                        if(desg.hasLanguage() && displayLanguageValue.equals(desg.getLanguage()) && desg.hasValue()) {
                            finalDisplay = desg.getValue();
                            break; 
                        }
                    }
                }
            }
            component.setDisplay(finalDisplay);
            if(conceptDef != null && includeDesignationsParam != null && includeDesignationsParam.getValue()) {
                addDesignationsToComponent(component, conceptDef, designationFilters, displayLanguageValue);
            }
            allCodes.add(component);
        }
    
	@Override
    public Class<ValueSet> getResourceType() {
        return ValueSet.class;
    }
}