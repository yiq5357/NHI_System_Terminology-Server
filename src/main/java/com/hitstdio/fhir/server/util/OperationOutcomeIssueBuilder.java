package com.hitstdio.fhir.server.util;

import org.hl7.fhir.r4.model.*;

public class OperationOutcomeIssueBuilder {
    
    private final OperationOutcome.OperationOutcomeIssueComponent issue;
    
    public OperationOutcomeIssueBuilder() {
        this.issue = new OperationOutcome.OperationOutcomeIssueComponent();
    }
    
    public OperationOutcomeIssueBuilder setSeverity(OperationOutcome.IssueSeverity severity) {
        issue.setSeverity(severity);
        return this;
    }
    
    public OperationOutcomeIssueBuilder setCode(OperationOutcome.IssueType code) {
        issue.setCode(code);
        return this;
    }
    
    public OperationOutcomeIssueBuilder setMessageId(String messageId) {
        issue.addExtension(new Extension(
            OperationOutcomeMessageId.MESSAGE_ID_EXTENSION_URL,
            new StringType(messageId)
        ));
        return this;
    }
    
    public OperationOutcomeIssueBuilder setDetails(String txIssueType, String text) {
        CodeableConcept details = new CodeableConcept();
        details.addCoding(new Coding(
            OperationOutcomeMessageId.TX_ISSUE_TYPE_SYSTEM,
            txIssueType,
            null
        ));
        issue.setDetails(details);
        return this;
    }
    
    public OperationOutcomeIssueBuilder addLocation(String location) {
        issue.addLocation(location);
        return this;
    }
    
    public OperationOutcomeIssueBuilder addExpression(String expression) {
        issue.addExpression(expression);
        return this;
    }
    
    public OperationOutcomeIssueBuilder setDiagnostics(String diagnostics) {
        issue.setDiagnostics(diagnostics);
        return this;
    }
    
    public OperationOutcome.OperationOutcomeIssueComponent build() {
        return issue;
    }
	
}
