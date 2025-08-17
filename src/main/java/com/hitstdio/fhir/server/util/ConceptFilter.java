package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.r4.model.ValueSet.FilterOperator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles filtering logic for concepts during ValueSet expansion.
 */
public class ConceptFilter {
    
    private static final String EXT_CONCEPT_NOT_FOR_UI = 
        "http://hl7.org/fhir/StructureDefinition/codesystem-concept-not-for-ui";
    
    /**
     * Checks if a concept should be included based on all filtering criteria
     */
    public boolean shouldIncludeConcept(ValueSet sourceValueSet,
                                       CodeSystem.ConceptDefinitionComponent conceptDef,
                                       String display,
                                       ExpansionRequest request) {
        // Filter 1: Text filter (matches code or display)
        if (!matchesTextFilter(conceptDef, display, request)) {
            return false;
        }
        
        // Filter 2: Active only filter
        if (!matchesActiveFilter(sourceValueSet, conceptDef, request)) {
            return false;
        }
        
        // Filter 3: Exclude not for UI
        if (!matchesUIFilter(conceptDef, request)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if concept matches the text filter
     */
    private boolean matchesTextFilter(CodeSystem.ConceptDefinitionComponent conceptDef,
                                     String display,
                                     ExpansionRequest request) {
        if (request.getFilter() == null || !request.getFilter().hasValue()) {
            return true;
        }
        
        String filterStr = request.getFilter().getValue().toLowerCase();
        return (conceptDef.getCode() != null && 
                conceptDef.getCode().toLowerCase().contains(filterStr)) ||
               (display != null && display.toLowerCase().contains(filterStr));
    }
    
    /**
     * Checks if concept matches the active filter
     */
    private boolean matchesActiveFilter(ValueSet sourceValueSet,
                                       CodeSystem.ConceptDefinitionComponent conceptDef,
                                       ExpansionRequest request) {
        // Check if filtering for active concepts is required
        boolean activeOnlyRequested = request.getActiveOnly() != null && 
                                     request.getActiveOnly().getValue();
        
        boolean vsRequiresActive = sourceValueSet.hasCompose() &&
                                  sourceValueSet.getCompose().hasInactive() &&
                                  !sourceValueSet.getCompose().getInactive();
        
        if (activeOnlyRequested || vsRequiresActive) {
            return !isConceptInactive(conceptDef);
        }
        
        return true;
    }
    
    /**
     * Checks if concept should be excluded from UI
     */
    private boolean matchesUIFilter(CodeSystem.ConceptDefinitionComponent conceptDef,
                                   ExpansionRequest request) {
        if (request.getExcludeNotForUI() == null || !request.getExcludeNotForUI().getValue()) {
            return true;
        }
        
        boolean isNotForUI = conceptDef.getExtension().stream()
            .anyMatch(ext -> EXT_CONCEPT_NOT_FOR_UI.equals(ext.getUrl()) && 
                           ext.getValue() instanceof BooleanType && 
                           ((BooleanType) ext.getValue()).booleanValue());
        
        return !isNotForUI;
    }
    
    /**
     * Checks if a concept matches all provided filters
     */
    public boolean matchesAllFilters(CodeSystem.ConceptDefinitionComponent concept,
                                    List<ConceptSetFilterComponent> filters,
                                    CodeSystem codeSystem) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        
        for (ConceptSetFilterComponent filter : filters) {
            if (!conceptMatchesFilter(concept, filter, codeSystem)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if a concept matches a single filter
     */
    private boolean conceptMatchesFilter(CodeSystem.ConceptDefinitionComponent concept,
                                        ConceptSetFilterComponent filter,
                                        CodeSystem codeSystem) {
        String property = filter.getProperty();
        FilterOperator op = filter.getOp();
        String value = filter.getValue();
        
        if (op == null || !filter.hasValue()) {
            return true;
        }
        
        switch (op) {
            case EQUAL:
                return matchesEqualFilter(concept, property, value);
            case REGEX:
                return matchesRegexFilter(concept, property, value);
            case IN:
                return matchesInFilter(concept, property, value, true);
            case NOTIN:
                return matchesInFilter(concept, property, value, false);
            case EXISTS:
                return matchesExistsFilter(concept, property, value);
            case ISA:
            case ISNOTA:
            case DESCENDENTOF:
            case GENERALIZES:
                // These are handled elsewhere in the hierarchy processing
                return true;
            default:
                return false;
        }
    }
    
    private boolean matchesEqualFilter(CodeSystem.ConceptDefinitionComponent concept,
                                      String property, String value) {
        if ("code".equals(property)) {
            return value.equals(concept.getCode());
        }
        if ("display".equals(property)) {
            return value.equals(concept.getDisplay());
        }
        
        return concept.getProperty().stream()
            .anyMatch(p -> property.equals(p.getCode()) && 
                         p.hasValue() && 
                         value.equals(p.getValue().primitiveValue()));
    }
    
    private boolean matchesRegexFilter(CodeSystem.ConceptDefinitionComponent concept,
                                      String property, String value) {
        if ("code".equals(property)) {
            return concept.hasCode() && concept.getCode().matches(value);
        }
        if ("display".equals(property)) {
            return concept.hasDisplay() && concept.getDisplay().matches(value);
        }
        
        return concept.getProperty().stream()
            .anyMatch(p -> {
                if (property.equals(p.getCode()) && p.hasValue()) {
                    String propValue = p.getValue().primitiveValue();
                    return propValue != null && propValue.matches(value);
                }
                return false;
            });
    }
    
    private boolean matchesInFilter(CodeSystem.ConceptDefinitionComponent concept,
                                   String property, String value, boolean shouldBeIn) {
        Set<String> valueSet = Arrays.stream(value.split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
        
        boolean isInSet;
        if ("code".equals(property)) {
            isInSet = concept.hasCode() && valueSet.contains(concept.getCode());
        } else if ("display".equals(property)) {
            isInSet = concept.hasDisplay() && valueSet.contains(concept.getDisplay());
        } else {
            isInSet = concept.getProperty().stream()
                .anyMatch(p -> property.equals(p.getCode()) && 
                             p.hasValue() && 
                             valueSet.contains(p.getValue().primitiveValue()));
        }
        
        return shouldBeIn ? isInSet : !isInSet;
    }
    
    private boolean matchesExistsFilter(CodeSystem.ConceptDefinitionComponent concept,
                                       String property, String value) {
        boolean shouldExist = Boolean.parseBoolean(value);
        boolean actuallyExists;
        
        if ("code".equals(property)) {
            actuallyExists = concept.hasCode() && !concept.getCode().isEmpty();
        } else if ("display".equals(property)) {
            actuallyExists = concept.hasDisplay() && !concept.getDisplay().isEmpty();
        } else if ("definition".equals(property)) {
            actuallyExists = concept.hasDefinition() && !concept.getDefinition().isEmpty();
        } else {
            actuallyExists = concept.getProperty().stream()
                .anyMatch(p -> property.equals(p.getCode()));
        }
        
        return shouldExist == actuallyExists;
    }
    
    /**
     * Determines if a concept is inactive
     */
    public boolean isConceptInactive(CodeSystem.ConceptDefinitionComponent concept) {
        return concept.getProperty().stream()
            .anyMatch(p -> {
                if ("inactive".equals(p.getCode()) && p.getValue() instanceof BooleanType) {
                    return ((BooleanType) p.getValue()).booleanValue();
                }
                
                if ("status".equals(p.getCode()) && p.getValue() instanceof CodeType) {
                    String statusCode = ((CodeType) p.getValue()).getCode();
                    boolean isKnownActive = "active".equalsIgnoreCase(statusCode) || 
                                          "A".equalsIgnoreCase(statusCode);
                    return !isKnownActive;
                }
                
                return false;
            });
    }
    
    /**
     * Determines if a concept is abstract (not selectable)
     */
    public boolean isConceptAbstract(CodeSystem.ConceptDefinitionComponent concept,
                                    CodeSystem codeSystem) {
        final String NOT_SELECTABLE_URI = "http://hl7.org/fhir/concept-properties#notSelectable";
        
        String notSelectableCode = codeSystem.getProperty().stream()
            .filter(propDef -> NOT_SELECTABLE_URI.equals(propDef.getUri()))
            .map(CodeSystem.PropertyComponent::getCode)
            .findFirst()
            .orElse("notSelectable");
        
        return concept.getProperty().stream()
            .anyMatch(p -> notSelectableCode.equals(p.getCode()) &&
                         p.getValue() instanceof BooleanType &&
                         ((BooleanType) p.getValue()).booleanValue());
    }
}