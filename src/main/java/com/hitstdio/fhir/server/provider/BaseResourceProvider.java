package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;

import com.hitstdio.fhir.server.servlet.ExampleRestfulServlet;

import java.io.IOException;

public abstract class BaseResourceProvider<T extends IBaseResource> implements IResourceProvider {

	private final DaoRegistry myDaoRegistry;
	
	
	public BaseResourceProvider(DaoRegistry theDaoRegistry) {
		myDaoRegistry = theDaoRegistry;
	}
	
	protected MethodOutcome validateWithAddNewCreate(Resource theResource) {
	    try {
	    	theResource.setId(new IdType("test-for-validate"));
	        ValidationResult validationResult = validateResourceValidationResult((T) theResource);
	        if (!validationResult.isSuccessful()) {
	            return validationResult2MethodOutcomeResource(validationResult);
	        }
	    } catch (IOException e) {
	        throw new InvalidRequestException("An error occurred during validation.");
	    }
	    return null;
    }    

	protected MethodOutcome validateWithAddNewUpdate(Resource theResource) {
	    try {
	        ValidationResult validationResult = validateResourceValidationResult((T) theResource);
	        if (!validationResult.isSuccessful()) {
	            return validationResult2MethodOutcomeResource(validationResult);
	        }
	    } catch (IOException e) {
	        throw new InvalidRequestException("An error occurred during validation.");
	    }
	    return null;
    }   	
	
	@Create
	public MethodOutcome createResource(@ResourceParam Resource theResource, RequestDetails requestDetails) {
		if(theResource==null) {
			throw new InvalidRequestException("Resource is null.");
		}
		
		/*MethodOutcome validationResult = validateWithAddNewCreate(theResource);
	    if (validationResult != null) {
	    	validationResult.setResponseStatusCode(Constants.STATUS_HTTP_400_BAD_REQUEST);
	        return validationResult;
	    }	*/
		
		IFhirResourceDao<T> dao = (IFhirResourceDao<T>) myDaoRegistry.getResourceDao(getResourceType());

		DaoMethodOutcome daoMethodOutcome = dao.create((T) theResource, requestDetails);

		if (!daoMethodOutcome.getCreated()) {
			return new MethodOutcome(new IdType(), daoMethodOutcome.getOperationOutcome(), false)
					.setResource(theResource);
		}

		return new MethodOutcome(
						new IdType(daoMethodOutcome.getPersistentId().getId().toString()), true)
				.setResource(theResource);
	}    

	@Update
	public MethodOutcome updateResource(@IdParam IdType theId, @ResourceParam Resource theResource, RequestDetails requestDetails) {
		if(theResource==null) {
			throw new InvalidRequestException("Resource is null.");
		}
		
		/*MethodOutcome validationResult = validateWithAddNewUpdate(theResource);
	    if (validationResult != null) {
	    	validationResult.setResponseStatusCode(Constants.STATUS_HTTP_400_BAD_REQUEST);
	        return validationResult;
	    }	*/
	    
		IFhirResourceDao<T> dao = (IFhirResourceDao<T>) myDaoRegistry.getResourceDao(getResourceType());

		DaoMethodOutcome daoMethodOutcome = dao.update((T) theResource, requestDetails);
		
		if (!daoMethodOutcome.getCreated()) {
			return new MethodOutcome(theId, daoMethodOutcome.getOperationOutcome(), false).setResource(theResource);
		}

		return new MethodOutcome(theResource.getIdElement(), true).setResource(theResource);		
	}	

	@Read(version = true)
	public T readResource(@IdParam IdType theId, RequestDetails requestDetails) {
		
		IFhirResourceDao<T> dao = (IFhirResourceDao<T>) myDaoRegistry.getResourceDao(getResourceType());

		T resource = dao.read(theId, requestDetails);

		if (resource != null) {
			return resource;
		}
		return null;
	}

    @Validate
    public MethodOutcome validateResource(@ResourceParam T theResource) throws IOException {
		if(theResource==null) {
			throw new InvalidRequestException("Resource is null.");
		}
        ValidationResult result = validateResourceValidationResult(theResource);
        MethodOutcome retVal = validationResult2MethodOutcome(result);
        return retVal;
    }
	    
    protected ValidationResult validateResourceValidationResult(T theResource) throws IOException {
		FhirContext ctx = FhirContext.forR4Cached();
		
		FhirValidator validator = ctx.newValidator();
		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(ExampleRestfulServlet.validationSupportChain);
		validator.registerValidatorModule(instanceValidator);
		
		ValidationResult result = validator.validateWithResult(theResource);
		return result;
    }

    protected MethodOutcome validationResult2MethodOutcome(ValidationResult validationResult) {
		MethodOutcome methodOutcome = new MethodOutcome();
		OperationOutcome outcome = (OperationOutcome) validationResult.toOperationOutcome();		
		methodOutcome.setOperationOutcome(outcome);
		return methodOutcome;
    }

    protected MethodOutcome validationResult2MethodOutcomeResource(ValidationResult validationResult) {
		MethodOutcome methodOutcome = new MethodOutcome();
		OperationOutcome outcome = (OperationOutcome) validationResult.toOperationOutcome();		
		methodOutcome.setResource(outcome);
		return methodOutcome;
    }   

}