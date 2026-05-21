package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
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
        return valueSetProvider.batchValidate(url, lenientDisplayValidation, txResources, validations);
    }
}
