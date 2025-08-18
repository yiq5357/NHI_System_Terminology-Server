package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds expansion component entries for concepts.
 */
public class ConceptComponentBuilder {

	private static final String EXT_CONTAINS_PROPERTY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property";

	private final ConceptFilter conceptFilter;
	private final LanguageProcessor languageProcessor;

	public ConceptComponentBuilder(ConceptFilter conceptFilter) {
		this.conceptFilter = conceptFilter;
		this.languageProcessor = new LanguageProcessor();
	}

	/**
	 * Creates an expansion component for a concept
	 */
    public ValueSetExpansionContainsComponent createExpansionComponent(
            CodeSystem codeSystem,
            CodeSystem.ConceptDefinitionComponent conceptDef,
            ExpansionRequest request) {
        
        ValueSetExpansionContainsComponent component = new ValueSetExpansionContainsComponent();
        component.setSystem(codeSystem.getUrl());
        component.setCode(conceptDef.getCode());
        
        CodeSystem.ConceptDefinitionComponent mergedConceptDef = 
            mergeWithSupplements(conceptDef, request);
        
        processDisplay(component, mergedConceptDef, codeSystem, request);
        setConceptFlags(component, mergedConceptDef, codeSystem);
        if (shouldIncludeDesignations(request)) {
            addDesignations(component, mergedConceptDef, request);
        }
        
        List<Extension> knownNonPropertyExtensions = mergedConceptDef.getExtension().stream()
                .filter(ext -> isKnownNonPropertyExtension(ext.getUrl()))
                .collect(Collectors.toList());

        component.setExtension(knownNonPropertyExtensions);

        if (request.getProperty() != null && !request.getProperty().isEmpty()) {
            addProperties(component, mergedConceptDef, request.getProperty());
        }
        
        return component;
    }


    private boolean isKnownNonPropertyExtension(String url) {
        if (url == null) {
            return false;
        }
        switch (url) {
            case "http://hl7.org/fhir/StructureDefinition/valueset-deprecated":
            case "http://hl7.org/fhir/StructureDefinition/rendering-style":
            case "http://hl7.org/fhir/StructureDefinition/rendering-xhtml":
            case "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition":
                return true;
            default:
                return false;
        }
    }

	/**
	 * Merges concept definition with supplement CodeSystems
	 */
	private CodeSystem.ConceptDefinitionComponent mergeWithSupplements(CodeSystem.ConceptDefinitionComponent conceptDef,
			ExpansionRequest request) {

		CodeSystem.ConceptDefinitionComponent merged = conceptDef.copy();

        if (request.getSupplements() != null && !request.getSupplements().isEmpty()) {
            for (CodeSystem supplementCs : request.getSupplements().values()) {
                CodeSystem.ConceptDefinitionComponent supplementConcept = 
                    findConceptInCodeSystem(supplementCs, conceptDef.getCode());

				if (supplementConcept != null) {
					for (CodeSystem.ConceptDefinitionDesignationComponent supDesg : supplementConcept
							.getDesignation()) {
						merged.addDesignation(supDesg);
					}

					for (CodeSystem.ConceptPropertyComponent supProp : supplementConcept.getProperty()) {
						merged.getProperty().removeIf(p -> p.getCode().equals(supProp.getCode()));
						merged.addProperty(supProp);
					}

                    for (Extension supExt : supplementConcept.getExtension()) {
                        merged.getExtension().removeIf(e -> e.getUrl().equals(supExt.getUrl()));
                        merged.addExtension(supExt);
                        
                        mapExtensionToProperty(supExt, merged);
                    }
				}
			}
		}

		return merged;
	}

	private void mapExtensionToProperty(Extension ext, CodeSystem.ConceptDefinitionComponent conceptDef) {
		getPropertyCodeFromExtensionUrl(ext.getUrl()).ifPresent(propertyCode -> {
			if (ext.hasValue()) {
				conceptDef.getProperty().removeIf(p -> propertyCode.equals(p.getCode()));
				conceptDef.addProperty().setCode(propertyCode).setValue(ext.getValue());
			}
		});
	}

	private java.util.Optional<String> getPropertyCodeFromExtensionUrl(String url) {
		if (url == null)
			return java.util.Optional.empty();
		switch (url) {
		case "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder":
		case "http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder":
			return java.util.Optional.of("order");
		case "http://hl7.org/fhir/StructureDefinition/valueset-label":
		case "http://hl7.org/fhir/StructureDefinition/codesystem-label":
			return java.util.Optional.of("label");
		case "http://hl7.org/fhir/StructureDefinition/itemWeight":
			return java.util.Optional.of("weight");
		default:
			return java.util.Optional.empty();
		}
	}

	/**
	 * Processes and sets the display value based on language preferences
	 */
	private void processDisplay(ValueSetExpansionContainsComponent component,
			CodeSystem.ConceptDefinitionComponent conceptDef, CodeSystem codeSystem, ExpansionRequest request) {

		String defaultDisplay = conceptDef.getDisplay();
		String defaultLanguage = codeSystem.getLanguage();
		String finalDisplay = defaultDisplay;

		List<CodeSystem.ConceptDefinitionDesignationComponent> finalDesignations = new ArrayList<>();
		if (conceptDef.hasDesignation()) {
			conceptDef.getDesignation().forEach(d -> finalDesignations.add(d.copy()));
		}

		String displayLanguage = request.getDisplayLanguage();
		List<LanguageProcessor.LanguagePreference> preferences = languageProcessor
				.parseLanguagePreferences(displayLanguage);

		String effectiveReqLang = preferences.isEmpty() ? null : preferences.get(0).language;

		if (effectiveReqLang != null && !"*".equals(effectiveReqLang) && !effectiveReqLang.equals(defaultLanguage)) {

			Optional<CodeSystem.ConceptDefinitionDesignationComponent> promotedDesignationOpt = Optional.empty();

			for (LanguageProcessor.LanguagePreference pref : preferences) {
				if (pref.quality > 0 && !"*".equals(pref.language)) {
					promotedDesignationOpt = findDesignationForLanguage(finalDesignations, pref.language);
					if (promotedDesignationOpt.isPresent()) {
						break;
					}
				}
			}

			if (promotedDesignationOpt.isPresent()) {
				finalDisplay = promotedDesignationOpt.get().getValue();
				finalDesignations.remove(promotedDesignationOpt.get());

				if (defaultDisplay != null && !defaultDisplay.isEmpty()) {
					CodeSystem.ConceptDefinitionDesignationComponent demoted = new CodeSystem.ConceptDefinitionDesignationComponent();
					demoted.setLanguage(defaultLanguage);
					demoted.setValue(defaultDisplay);
					demoted.setUse(
							new Coding("http://terminology.hl7.org/CodeSystem/designation-usage", "display", null));
					finalDesignations.add(demoted);
				}
			} else {
				boolean hardFail = preferences.stream().anyMatch(p -> "*".equals(p.language) && p.quality == 0);

				if (hardFail) {
					finalDisplay = null;
					if (defaultDisplay != null && !defaultDisplay.isEmpty()) {
						CodeSystem.ConceptDefinitionDesignationComponent demoted = new CodeSystem.ConceptDefinitionDesignationComponent();
						demoted.setLanguage(defaultLanguage);
						demoted.setValue(defaultDisplay);
						demoted.setUse(
								new Coding("http://terminology.hl7.org/CodeSystem/designation-usage", "display", null));
						finalDesignations.add(demoted);
					}
				}
			}
		}

		component.setDisplay(finalDisplay);

		conceptDef.setDesignation(finalDesignations);
	}

	/**
	 * Sets abstract and inactive flags on the component
	 */
	private void setConceptFlags(ValueSetExpansionContainsComponent component,
			CodeSystem.ConceptDefinitionComponent conceptDef, CodeSystem codeSystem) {

		boolean isAbstract = conceptFilter.isConceptAbstract(conceptDef, codeSystem);
		boolean isInactive = conceptFilter.isConceptInactive(conceptDef);

		if (isAbstract) {
			component.setAbstract(isAbstract);
		}
		if (isInactive) {
			component.setInactive(isInactive);
		}
	}

	/**
	 * Adds designations to the component
	 */
	private void addDesignations(ValueSetExpansionContainsComponent component,
			CodeSystem.ConceptDefinitionComponent conceptDef, ExpansionRequest request) {

		for (CodeSystem.ConceptDefinitionDesignationComponent sourceDesg : conceptDef.getDesignation()) {
			if (shouldIncludeDesignation(sourceDesg, request)) {
				component.addDesignation().setLanguage(sourceDesg.getLanguage()).setUse(sourceDesg.getUse())
						.setValue(sourceDesg.getValue()).setExtension(sourceDesg.getExtension());
			}
		}
	}

	/**
	 * Adds properties as extensions to the component
	 */
	private void addProperties(ValueSetExpansionContainsComponent component,
			CodeSystem.ConceptDefinitionComponent conceptDef, List<CodeType> requestedProperties) {

		for (CodeType reqProp : requestedProperties) {
			String reqPropCode = reqProp.getCode();

			if ("definition".equals(reqPropCode) && conceptDef.hasDefinition()) {
				buildContainsPropertyExtension(component, reqPropCode, new StringType(conceptDef.getDefinition()));
				continue;
			}

			for (CodeSystem.ConceptPropertyComponent p : conceptDef.getProperty()) {
				if (reqPropCode.equals(p.getCode()) && p.hasValue()) {
					buildContainsPropertyExtension(component, p.getCode(), p.getValue());
				}
			}
		}
	}

	/**
	 * Builds a property extension for the component
	 */
	private void buildContainsPropertyExtension(ValueSetExpansionContainsComponent component, String code, Type value) {
		Extension containsPropertyExt = component.addExtension();
		containsPropertyExt.setUrl(EXT_CONTAINS_PROPERTY);

		containsPropertyExt.addExtension().setUrl("code").setValue(new CodeType(code));

		Type finalValue = value;

		if ("order".equals(code) || "weight".equals(code)) {
			if (value instanceof IntegerType) {
				finalValue = new DecimalType(((IntegerType) value).getValue());
			} else if (value instanceof DecimalType) {
				finalValue = value;
			}
		}

		containsPropertyExt.addExtension().setUrl("value").setValue(finalValue);
	}

	/**
	 * Checks if designations should be included
	 */
	private boolean shouldIncludeDesignations(ExpansionRequest request) {
		return request.getIncludeDesignations() != null && request.getIncludeDesignations().getValue();
	}

	/**
	 * Checks if a specific designation should be included
	 */
	private boolean shouldIncludeDesignation(CodeSystem.ConceptDefinitionDesignationComponent designation,
			ExpansionRequest request) {

		List<StringType> filters = request.getDesignation();

		if (filters == null || filters.isEmpty()) {
			return true;
		}

		return filters.stream().anyMatch(filter -> {
			String filterValue = filter.getValue();
			if (filterValue == null) {
				return false;
			}

			if (designation.hasLanguage()) {
				String langCode = designation.getLanguage();
				if (filterValue.equals(langCode)) {
					return true;
				}
				if (filterValue.contains("|")) {
					String langPart = filterValue.substring(filterValue.lastIndexOf('|') + 1);
					if (langPart.equals(langCode)) {
						return true;
					}
				}
			}

			if (designation.hasUse()) {
				Coding use = designation.getUse();
				String useCode = use.getCode();
				String useSystem = use.getSystem();
				if (filterValue.equals(useCode)) {
					return true;
				}
				if (useSystem != null && filterValue.equals(useSystem + "|" + useCode)) {
					return true;
				}
			}

			return false;
		});
	}

	/**
	 * Finds a designation for a specific language
	 */
	private Optional<CodeSystem.ConceptDefinitionDesignationComponent> findDesignationForLanguage(
			List<CodeSystem.ConceptDefinitionDesignationComponent> designations, String requestedLang) {

		if (requestedLang == null || requestedLang.isEmpty() || designations == null) {
			return Optional.empty();
		}

		// Exact match
		Optional<CodeSystem.ConceptDefinitionDesignationComponent> exactMatch = designations.stream()
				.filter(d -> requestedLang.equalsIgnoreCase(d.getLanguage())).findFirst();
		if (exactMatch.isPresent()) {
			return exactMatch;
		}

		// Child language match (e.g., "en-US" for "en")
		Optional<CodeSystem.ConceptDefinitionDesignationComponent> childMatch = designations.stream()
				.filter(d -> d.getLanguage() != null
						&& d.getLanguage().toLowerCase().startsWith(requestedLang.toLowerCase() + "-"))
				.findFirst();
		if (childMatch.isPresent()) {
			return childMatch;
		}

		// Parent language match (e.g., "en" for "en-US")
		if (requestedLang.contains("-")) {
			String parentLang = requestedLang.substring(0, requestedLang.indexOf('-'));
			Optional<CodeSystem.ConceptDefinitionDesignationComponent> parentMatch = designations.stream()
					.filter(d -> parentLang.equalsIgnoreCase(d.getLanguage())).findFirst();
			if (parentMatch.isPresent()) {
				return parentMatch;
			}
		}

		return Optional.empty();
	}

	/**
	 * Helper method to find concept in CodeSystem
	 */
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

	private CodeSystem.ConceptDefinitionComponent findConceptRecursive(CodeSystem.ConceptDefinitionComponent concept,
			String code) {
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
}