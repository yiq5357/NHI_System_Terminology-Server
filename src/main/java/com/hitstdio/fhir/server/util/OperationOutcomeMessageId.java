package com.hitstdio.fhir.server.util;

public class OperationOutcomeMessageId {
	
	 // ValueSet 相關
    public static final String UNABLE_TO_RESOLVE_VALUE_SET = "Unable_to_resolve_value_Set_";
    public static final String NONE_OF_CODES_IN_VALUE_SET_ONE = "None_of_the_provided_codes_are_in_the_value_set_one";
    public static final String UNABLE_TO_CHECK_CODES_IN_VS = "UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_CS";
    public static final String TX_GENERAL_CC_ERROR = "TX_GENERAL_CC_ERROR_MESSAGE";
    
    // CodeSystem 相關
    public static final String UNKNOWN_CODE_IN_VERSION = "Unknown_Code_in_Version";
    public static final String UNKNOWN_CODESYSTEM_VERSION = "UNKNOWN_CODESYSTEM_VERSION";
    public static final String UNKNOWN_CODESYSTEM = "UNKNOWN_CODESYSTEM";
    public static final String TERMINOLOGY_TX_SYSTEM_VALUESET2 = "Terminology_TX_System_ValueSet2";
    public static final String TERMINOLOGY_TX_SYSTEM_RELATIVE = "Terminology_TX_System_Relative";
    public static final String CODING_HAS_NO_SYSTEM_CANNOT_VALIDATE = "Coding_has_no_system__cannot_validate";
    public static final String UNABLE_TO_INFER_CODESYSTEM = "UNABLE_TO_INFER_CODESYSTEM";
    
    // 其他驗證錯誤
    public static final String ABSTRACT_CODE_NOT_ALLOWED = "Abstract_Code_Not_Allowed";
    public static final String INVALID_DISPLAY = "Invalid_Display";
    public static final String STATUS_CODE_WARNING = "STATUS_CODE_WARNING_CODE";
    public static final String MISSING_REQUIRED_PARAMETER = "Missing_Required_Parameter";
    public static final String THIS_CODE_NOT_IN_VS = "this-code-not-in-vs";
    public static final String INACTIVE_CONCEPT_FOUND = "INACTIVE_CONCEPT_FOUND";
    
    // 常用的 URL 常量
    public static final String TX_ISSUE_TYPE_SYSTEM = "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type";
    public static final String MESSAGE_ID_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id";
 
    // 防止實例化
    private OperationOutcomeMessageId() {
        
    }
}
