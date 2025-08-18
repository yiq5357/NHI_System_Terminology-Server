package com.hitstdio.fhir.server.util;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service for ValueSet expansion operations.
 * Handles the business logic for expanding value sets.
 */
public class ValueSetExpansionService {
    
    private final IFhirResourceDao<ValueSet> valueSetDao;
    private final IFhirResourceDao<CodeSystem> codeSystemDao;
    private final ResourceFinder resourceFinder;
    private final ConceptCollector conceptCollector;
    private final ExpansionBuilder expansionBuilder;
    private final ConceptFilter conceptFilter;
    
    public ValueSetExpansionService(IFhirResourceDao<ValueSet> valueSetDao, 
                                    IFhirResourceDao<CodeSystem> codeSystemDao) {
        this.valueSetDao = valueSetDao;
        this.codeSystemDao = codeSystemDao;
        this.resourceFinder = new ResourceFinder(valueSetDao, codeSystemDao);
        this.conceptFilter = new ConceptFilter();
        this.conceptCollector = new ConceptCollector(resourceFinder, conceptFilter);
        this.expansionBuilder = new ExpansionBuilder(conceptFilter);
    }
    
    /**
     * Main method to perform ValueSet expansion
     */
    public ValueSet expand(ExpansionRequest request) {
        ValueSet sourceValueSet = retrieveSourceValueSet(request);
        
        ValueSet resultValueSet = createResultValueSet(sourceValueSet, request);
        
        ValueSetExpansionComponent expansion = resultValueSet.getExpansion();
        initializeExpansion(expansion);
        
        processSupplements(sourceValueSet, expansion, request);
        
        discoverAndAugmentProperties(sourceValueSet, request.getSupplements(), request);

        List<ValueSetExpansionContainsComponent> allConcepts = 
            conceptCollector.collectAllConcepts(sourceValueSet, expansion, request);
        
        List<ValueSetExpansionContainsComponent> pagedConcepts = 
            applyPaging(allConcepts, request);
        
        expansionBuilder.buildExpansion(expansion, sourceValueSet, allConcepts, 
                                       pagedConcepts, request);
        
        return resultValueSet;
    }
    
    private void discoverAndAugmentProperties(ValueSet valueSet, 
            Map<String, CodeSystem> supplements, 
            ExpansionRequest request) {
    	Set<String> discoveredProperties = new HashSet<>();
        
        if (valueSet.hasCompose()) {
            for (ConceptSetComponent include : valueSet.getCompose().getInclude()) {
                for (ConceptReferenceComponent concept : include.getConcept()) {
                    for (Extension ext : concept.getExtension()) {
                        getPropertyCodeFromExtensionUrl(ext.getUrl()).ifPresent(discoveredProperties::add);
                    }
                }
            }
        }
        
        if (supplements != null) {
            for (CodeSystem cs : supplements.values()) {
                if (cs.hasConcept()) {
                    for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                        for (CodeSystem.ConceptPropertyComponent prop : concept.getProperty()) {
                            discoveredProperties.add(prop.getCode());
                        }
                        for (Extension ext : concept.getExtension()) {
                            getPropertyCodeFromExtensionUrl(ext.getUrl()).ifPresent(discoveredProperties::add);
                        }
                    }
                }
            }
        }
        
        if (!discoveredProperties.isEmpty()) {
            List<CodeType> originalProperties = request.getProperty();
            Set<String> existingCodes = originalProperties.stream()
                .map(CodeType::getCode)
                .collect(Collectors.toSet());
            
            for (String code : discoveredProperties) {
                if (!existingCodes.contains(code)) {
                    originalProperties.add(new CodeType(code));
                }
            }
        }
    }
    
    private java.util.Optional<String> getPropertyCodeFromExtensionUrl(String url) {
        if (url == null) return java.util.Optional.empty();
        switch (url) {
            case "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder": return java.util.Optional.of("order");
            case "http://hl7.org/fhir/StructureDefinition/valueset-label": return java.util.Optional.of("label");
            case "http://hl7.org/fhir/StructureDefinition/itemWeight": return java.util.Optional.of("weight");
            default: return java.util.Optional.empty();
        }
    }
	/**
     * Retrieves the source ValueSet based on request parameters
     */
    private ValueSet retrieveSourceValueSet(ExpansionRequest request) {
        // Check tx-resources first
        Map<String, Resource> txResources = request.getTxResources();
        if (request.getUrl() != null && request.getUrl().hasValue() && 
            txResources.containsKey(request.getUrl().getValueAsString())) {
            return (ValueSet) txResources.get(request.getUrl().getValueAsString());
        }
        
        if (request.getId() != null && request.getId().hasIdPart()) {
            return valueSetDao.read(request.getId(), request.getRequestDetails());
        }
        
        if (request.getUrl() == null || !request.getUrl().hasValue()) {
            throw new InvalidRequestException(
                "Either a resource ID or the 'url' parameter must be provided for the $expand operation.");
        }
        
        return resourceFinder.findValueSetByUrl(
            request.getUrl().getValue(), 
            request.getValueSetVersion() != null ? request.getValueSetVersion().getValue() : null,
            request.getRequestDetails()
        );
    }
    
    /**
     * Creates the result ValueSet with appropriate metadata
     */
    private ValueSet createResultValueSet(ValueSet sourceValueSet, ExpansionRequest request) {
        ValueSet result = new ValueSet();
        boolean includeDefinition = request.getIncludeDefinition() != null && 
                                   request.getIncludeDefinition().getValue();
        
        copyBasicMetadata(sourceValueSet, result);
        
        if (includeDefinition) {
            copyFullMetadata(sourceValueSet, result);
        }
        
        return result;
    }
    
    /**
     * Copies basic metadata from source to result
     */
    private void copyBasicMetadata(ValueSet source, ValueSet result) {
        result.setMeta(source.getMeta().copy());
        
        if (source.hasLanguage()) {
            result.setLanguage(source.getLanguage());
        }
        if (source.hasUrl()) {
            result.setUrl(source.getUrl());
        }
        
        result.setIdentifier(source.getIdentifier());
        
        if (source.hasVersion()) {
            result.setVersion(source.getVersion());
        }
        if (source.hasName()) {
            result.setName(source.getName());
        }
        if (source.hasTitle()) {
            result.setTitle(source.getTitle());
        }
        
        result.setStatus(source.getStatus());
        
        if (source.hasExperimental()) {
            result.setExperimental(source.getExperimental());
        }
        if (source.hasDate()) {
            result.setDate(source.getDate());
            result.getDateElement().setPrecision(TemporalPrecisionEnum.DAY);
        }
        if (source.hasImmutable()) {
            result.setImmutable(source.getImmutable());
        }
    }
    
    /**
     * Copies full metadata when includeDefinition is true
     */
    private void copyFullMetadata(ValueSet source, ValueSet result) {
        if (source.hasPublisher()) {
            result.setPublisher(source.getPublisher());
        }
        
        result.setContact(source.getContact());
        
        if (source.hasDescription()) {
            result.setDescription(source.getDescription());
        }
        
        result.setUseContext(source.getUseContext());
        result.setJurisdiction(source.getJurisdiction());
        
        if (source.hasPurpose()) {
            result.setPurpose(source.getPurpose());
        }
        if (source.hasCopyright()) {
            result.setCopyright(source.getCopyright());
        }
        if (source.hasCompose()) {
            result.setCompose(source.getCompose().copy());
        }
        
        result.setExtension(source.getExtension());
    }
    
    /**
     * Initializes the expansion component with basic metadata
     */
    private void initializeExpansion(ValueSetExpansionComponent expansion) {
        expansion.setIdentifier("urn:uuid:" + UUID.randomUUID());
        expansion.setTimestamp(new Date());
    }
    
    /**
     * Processes supplement CodeSystems from extensions
     */
    private void processSupplements(ValueSet sourceValueSet, 
                                   ValueSetExpansionComponent expansion, 
                                   ExpansionRequest request) {
        final String SUPPLEMENT_URL = "http://hl7.org/fhir/StructureDefinition/valueset-supplement";
        Map<String, CodeSystem> supplements = new HashMap<>();
        
        for (Extension ext : sourceValueSet.getExtension()) {
            if (SUPPLEMENT_URL.equals(ext.getUrl()) && ext.getValue() instanceof CanonicalType) {
                CanonicalType supplementCanonical = (CanonicalType) ext.getValue();
                if (supplementCanonical.hasValue()) {
                    String[] parts = supplementCanonical.getValue().split("\\|");
                    String url = parts[0];
                    String version = parts.length > 1 ? parts[1] : null;
                    
                    CodeSystem supplementCs = resourceFinder.findCodeSystem(
                        url, version, request);
                    
                    if (supplementCs != null) {
                        expansion.addParameter()
                            .setName("used-supplement")
                            .setValue(supplementCanonical.copy());
                        expansion.addParameter()
                            .setName("version")
                            .setValue(supplementCanonical.copy());
                        
                        supplements.put(url, supplementCs);
                    }
                }
            }
        }
        
        request.setSupplements(supplements);
    }
    
    /**
     * Applies paging to the collected concepts
     */
    private List<ValueSetExpansionContainsComponent> applyPaging(
            List<ValueSetExpansionContainsComponent> allConcepts,
            ExpansionRequest request) {
        
        int offset = request.getOffset() != null ? request.getOffset().getValue() : 0;
        int count = request.getCount() != null ? request.getCount().getValue() : 0;
        
        if (offset < 0) {
            throw new InvalidRequestException("Offset must be >= 0");
        }
        if (count < 0) {
            count = 0;
        }
        
        if (count <= 0) {
            return allConcepts;
        }
        
        if (offset >= allConcepts.size()) {
            return Collections.emptyList();
        }
        
        int endIndex = Math.min(offset + count, allConcepts.size());
        return allConcepts.subList(offset, endIndex);
    }
}
