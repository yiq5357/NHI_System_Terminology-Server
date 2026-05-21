package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import java.util.List;

public class BatchValidateProvider {

    private final ValueSetResourceProvider valueSetProvider;

    public BatchValidateProvider(ValueSetResourceProvider valueSetProvider) {
        this.valueSetProvider = valueSetProvider;
    }

    @Operation(name = "$batch-validate", idempotent = false)
    public Parameters batchValidate(
            @OperationParam(name = "url") UriType url,
            @OperationParam(name = "lenient-display-validation") BooleanType lenientDisplayValidation,
            @OperationParam(name = "tx-resource") List<IBaseResource> txResources,
            @OperationParam(name = "validation") List<Parameters> validations
    ) {
        if (validations == null || validations.isEmpty()) {
            throw new InvalidRequestException("At least one 'validation' parameter is required");
        }

        Parameters output = new Parameters();
        try {
            valueSetProvider.registerTxResources(txResources);
            for (Parameters validationInput : validations) {
                Resource result = executeBatchItem(validationInput, url, lenientDisplayValidation);
                output.addParameter().setName("validation").setResource(result);
            }
        } finally {
            valueSetProvider.clearTxResources();
        }
        return output;
    }

    private Resource executeBatchItem(
            Parameters itemParams,
            UriType globalUrl,
            BooleanType globalLenient) {

        Coding coding = null;
        CodeableConcept codeableConcept = null;
        CodeType code = null;
        CanonicalType system = null;
        StringType display = null;
        BooleanType localLenient = null;
        boolean hasValidInput = false;
        StringBuilder seenParams = new StringBuilder();

        for (ParametersParameterComponent p : itemParams.getParameter()) {
            if (seenParams.length() > 0) seenParams.append("|");
            String fhirType = p.getValue() != null ? p.getValue().fhirType() : "null";
            seenParams.append(p.getName()).append(":").append(fhirType);

            switch (p.getName()) {
                case "coding":
                    if (p.getValue() instanceof Coding c) { coding = c; hasValidInput = true; }
                    break;
                case "codeableConcept":
                    if (p.getValue() instanceof CodeableConcept cc) { codeableConcept = cc; hasValidInput = true; }
                    break;
                case "code":
                    if (p.getValue() instanceof CodeType ct) { code = ct; hasValidInput = true; }
                    break;
                case "system":
                    if (p.getValue() instanceof UriType u) system = new CanonicalType(u.getValue());
                    break;
                case "display":
                    if (p.getValue() instanceof StringType s) display = s;
                    break;
                case "lenient-display-validation":
                    if (p.getValue() instanceof BooleanType b) localLenient = b;
                    break;
            }
        }

        // 無有效輸入欄位（coding/codeableConcept/code）→ 直接回傳 OperationOutcome
        if (!hasValidInput) {
            return buildBatchItemError(
                "Unable to find code to validate (looked for coding | codeableConcept | code in parameters ="
                    + seenParams + ")");
        }

        // 局部 lenient 覆蓋全域
        BooleanType effectiveLenient = localLenient != null ? localLenient : globalLenient;

        try {
            return (Resource) valueSetProvider.validateCode(
                null,             // resourceId
                code,             // code
                system,           // system
                null,             // systemVersion (StringType)
                null,             // systemVersionCode (CodeType)
                globalUrl,        // url
                null,             // valueSet
                null,             // version
                null,             // valueSetVersion
                display,          // display
                coding,           // coding
                codeableConcept,  // codeableConcept
                null,             // displayLanguage
                null,             // abstract
                null,             // activeOnly
                null,             // inferSystem
                effectiveLenient, // lenient-display-validation
                null,             // valueset-membership-only
                null,             // default-valueset-version
                null,             // system-version list
                null,             // check-system-version list
                null,             // force-system-version list
                null              // txResources
            );
        } catch (UnprocessableEntityException e) {
            if (e.getOperationOutcome() instanceof OperationOutcome oo) return oo;
            return buildBatchItemError(e.getMessage());
        } catch (Exception e) {
            return buildBatchItemError(e.getMessage());
        }
    }

    private OperationOutcome buildBatchItemError(String message) {
        OperationOutcome oo = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = oo.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        CodeableConcept details = new CodeableConcept();
        details.setText(message);
        issue.setDetails(details);
        return oo;
    }
}
