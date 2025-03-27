package com.hitstdio.fhir.server.r4;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import org.hl7.fhir.r4.model.Bundle;

public final class SystemProvider {

	@Transaction
	public Bundle transaction(@TransactionParam Bundle theInput) {
		return theInput;
	}
}
