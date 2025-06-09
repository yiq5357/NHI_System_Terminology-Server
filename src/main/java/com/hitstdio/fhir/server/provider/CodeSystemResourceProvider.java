package com.hitstdio.fhir.server.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.primitive.XhtmlDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.*;

public class CodeSystemResourceProvider extends BaseResourceProvider<CodeSystem> {
    
	// IFhirResourceDao：泛型接口，提供針對 FHIR 資源的基本操作方法，例如 CRUD 操作
	private final IFhirResourceDao<CodeSystem> dao;
    
	// DaoRegistry：FHIR 的 HAPI FHIR 框架提供的工具，作為 DAO（資料訪問物件）的管理中心
    public CodeSystemResourceProvider(DaoRegistry theDaoRegistry) {
        super(theDaoRegistry);
        this.dao = theDaoRegistry.getResourceDao(CodeSystem.class);
    }
    
    RequestDetails requestDetails = new SystemRequestDetails();
    
    @Patch
    public MethodOutcome patch(
        @IdParam IdType theId,
        @ResourceParam PatchTypeEnum patchType,
        @ResourceParam String thePatchBody
    ) {
        return dao.patch(theId, null, patchType, thePatchBody, null, requestDetails);
    }
    
    private CodeSystem getCodeSystem(String system, String version) {
        
    	// 建立搜尋參數
    	// SearchParameterMap：FHIR 的查詢參數容器，用於指定搜尋條件
        SearchParameterMap searchParams = new SearchParameterMap();
        searchParams.add(CodeSystem.SP_URL, new UriParam(system));
        if (version != null) {
            searchParams.add(CodeSystem.SP_VERSION, new StringParam(version));
        }
        
        try {
            // 執行搜尋
        	// IBundleProvider：搜尋結果的封裝物件，包含資源集合，支持分頁和延遲加載
            IBundleProvider searchResult = dao.search(searchParams, null);
            
            if (searchResult.size() == 0) {
                throw new ResourceNotFoundException(
                    String.format("CodeSystem with URL '%s' and version '%s' not found", 
                    system, version));
            }
            
            // 取得第一個符合的結果
            CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
            
            if (codeSystem == null) {
                throw new ResourceNotFoundException(
                    String.format("CodeSystem with URL '%s' and version '%s' not found", 
                    system, version));
            }
            
            return codeSystem;
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorException("Error retrieving CodeSystem", e);
        }
    }
    
    @Operation(name = "$lookup", idempotent = true)// idempotent：重複執行相同的請求不會改變資源狀態或結果
    public IBaseResource lookup(
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "property") List<StringType> properties) {
          		
        // 1. 增加輸入參數驗證
        validateInputParameters(code, system, coding);
        
        if (coding != null) {
            code = new CodeType(coding.getCode());
            system = new UriType(coding.getSystem());
            if (coding.hasVersion()) {
                version = new StringType(coding.getVersion());
            }
        }
             
    	try {     
            // 2. 新增系統參數檢查
            if (system == null || system.isEmpty()) {
                throw new UnprocessableEntityException("The system parameter is required");
            }
            
            CodeSystem codeSystem = findCodeSystemWithConcept( 
                code.getValue(),
                system.getValue(),      
                version != null ? version.getValue() : null
            );

            Parameters retVal = new Parameters();
            
            // 3. 使用 builder pattern 建立回應
            String codeValue = code.getValue();
            String codeSystemValue = system.getValue();
            ConceptDefinitionComponent concept = codeSystem.getConcept().stream()
            											   .filter(c -> c.getCode().equals(codeValue))
            											   .findFirst()
            											   .orElseThrow(() -> new ResourceNotFoundException(
            													   String.format("Concept with code '%s' not found in system '%s'", codeValue, codeSystemValue)));
                
                buildSuccessResponse(codeSystem, retVal, concept, properties);

                return retVal;
            
        } catch (ResourceNotFoundException e) {
            // 4. 更詳細的錯誤處理
        	return createOperationOutcome(e);
        	
        } catch (Exception e) {
            // 5. 具體的例外處理
        	return createErrorOperationOutcome(e);
        }
    }
    
  
    
    // 驗證參數：確保必須至少有 code 或 coding
    private void validateInputParameters(CodeType code, UriType system, Coding coding) {
        if (code == null && coding == null) {
            throw new InvalidRequestException("Either 'code' or 'coding' parameter must be provided");
        }
        
        if (coding != null && coding.isEmpty()) {
            throw new InvalidRequestException("If coding is provided, it cannot be empty");
        }
    }
    
    // 查找概念    
    private CodeSystem findCodeSystemWithConcept(String code, String system, String version) {
        try {
            CodeSystem codeSystem = getCodeSystem(system, version);
            codeSystem.getConcept().stream()
                      .filter(c -> c.getCode().equals(code))
                      .findFirst()
                      .orElseThrow(() -> new ResourceNotFoundException(
                    		  String.format("Concept with code '%s' not found in system '%s'", code, system)));
            return codeSystem;
        } catch (Exception e) {
            throw new ResourceNotFoundException(
                String.format("Error finding concept: %s", e.getMessage()), e);
        }
    }
    
    // 成功回應
    private void buildSuccessResponse(
    				CodeSystem codeSystem, 
    				Parameters retVal, 
    				ConceptDefinitionComponent concept, 
    				List<StringType> properties) {

    	// 設置基本參數
        retVal.addParameter()
              .setName("name")
              .setValue(new StringType(codeSystem.getName()));
            
        retVal.addParameter()
              .setName("display")
              .setValue(new StringType(concept.getDisplay()));
            
        if (concept.hasDefinition()) {
            retVal.addParameter()
                  .setName("definition")
                  .setValue(new StringType(concept.getDefinition()));
        }
        
        // 處理額外屬性
        if (properties != null && !properties.isEmpty()) {
            for (StringType property : properties) {
                addPropertyToParameters(retVal, concept, property.getValue());
            }
        }
        
        retVal.addParameter()
              .setName("found")
              .setValue(new BooleanType(true));
    }

    
    // 创建未找到资源的OperationOutcome
    private OperationOutcome createNotFoundOperationOutcome(Exception e) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("exception");
        
        // 生成HTML div
        String htmlDiv = "<div xmlns=\"http://www.w3.org/1999/xhtml\"><h1>Operation Outcome</h1>" +
                         "<table border=\"0\"><tr><td style=\"font-weight: bold;\">ERROR</td><td>[]</td><td></td></tr></table></div>";
        
        outcome.getText().setStatus(Narrative.NarrativeStatus.GENERATED);
        
        // 創建一個新的 XhtmlNode 並設置內容
        XhtmlNode divNode = new XhtmlNode();
        divNode.setValueAsString(htmlDiv);

        // 將 XhtmlNode 設置到 Resource 的 Text div
        outcome.getText().setDiv(divNode);
        
        outcome.addIssue()
               .setSeverity(OperationOutcome.IssueSeverity.ERROR)
               .setCode(OperationOutcome.IssueType.NOTFOUND)
               .setDetails(new CodeableConcept().setText(e.getMessage()));
        
        return outcome;
    }
    
    // 创建通用错误的OperationOutcome
    private OperationOutcome createErrorOperationOutcome(Exception e) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("exception");
        
        outcome.addIssue()
               .setSeverity(OperationOutcome.IssueSeverity.ERROR)
               .setCode(OperationOutcome.IssueType.EXCEPTION)
               .setDetails(new CodeableConcept().setText("Error processing lookup operation: " + e.getMessage()));
        
        return outcome;
    }
    
    
    // 建立OperationOutcome的新方法
    private OperationOutcome createOperationOutcome(Exception e) {
        return createOperationOutcome(
            e.getMessage(), 
            OperationOutcome.IssueSeverity.ERROR, 
            OperationOutcome.IssueType.NOTFOUND
        );
    }
    
    // 使用自訂參數建立OperationOutcome的重載方法
    private OperationOutcome createOperationOutcome(
            String message, 
            OperationOutcome.IssueSeverity severity, 
            OperationOutcome.IssueType issueType) {
        
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("exception");
        
        // 產生用於文字表示的 HTML div
        String htmlDiv = String.format(
        		"<div xmlns=\"http://www.w3.org/1999/xhtml\">" +
        		        "<h1>Operation Outcome</h1>" +
        		        "<table border=\"0\">" +
        		        "<tr><td style=\"font-weight: bold;\">%s</td><td>[]</td><td>%s</td></tr>" +
        		        "</table></div>", 
        		        severity.toCode(),
        		        message
        );
 
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString(htmlDiv);
        
        outcome.setText(narrative);
        
        outcome.addIssue()
               .setSeverity(severity)
               .setCode(issueType)
               .setDetails(new CodeableConcept().setText(message));
        
        return outcome;
    }
    
    // 例外處理
    public class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }

        public ResourceNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // 將特定的屬性從一個 ConceptDefinitionComponent 物件添加到一個 Parameters 物件
    private void addPropertyToParameters(Parameters params, ConceptDefinitionComponent concept, String propertyName) {
        concept.getProperty().stream()
               .filter(p -> p.getCode().equals(propertyName))
               .forEach(p -> {
            	   Parameters.ParametersParameterComponent prop = params.addParameter();
            	   prop.setName("property");
            	   prop.addPart().setName("code").setValue(new CodeType(p.getCode()));
                
                // 改進型別處理
                Type value = p.getValue();
                Parameters.ParametersParameterComponent valuePart = prop.addPart().setName("value");
                if (value instanceof CodeType) {
                    valuePart.setValue(new CodeType(((CodeType)value).getValue()));
                } else if (value instanceof StringType) {
                    valuePart.setValue(new StringType(((StringType)value).getValue()));
                } else if (value instanceof BooleanType) {
                    valuePart.setValue(new BooleanType(((BooleanType)value).getValue()));
                } else {
                    valuePart.setValue(value);
                }
            });
    } 
    
    @Operation(name = "$validate-code", idempotent = true)
    public Parameters validateCode(
            @IdParam(optional = true) IdType resourceId,
            @OperationParam(name = "code") CodeType code,
            @OperationParam(name = "system") UriType system,
            @OperationParam(name = "version") StringType version,
            @OperationParam(name = "display") StringType display,
            @OperationParam(name = "coding") Coding coding,
            @OperationParam(name = "codeableConcept") CodeableConcept codeableConcept
    ) {
        // Extract values from Coding or CodeableConcept if provided
        if (coding != null) {
            code = coding.getCodeElement();
            system = coding.getSystemElement();
            display = coding.getDisplayElement();
        } else if (codeableConcept != null && !codeableConcept.getCoding().isEmpty()) {
            Coding first = codeableConcept.getCodingFirstRep();
            code = first.getCodeElement();
            system = first.getSystemElement();
            display = first.getDisplayElement();
        }

        validateCodeAndSystem(code, system, resourceId);

         try {
            CodeSystem codeSystem = (resourceId != null) ?
            getCodeSystemById(resourceId.getIdPart(), version) :
            validateFindCodeSystemWithConcept(code.getValue(), system.getValue(), version != null ? version.getValue() : null);

            ConceptDefinitionComponent concept = findConceptRecursive(codeSystem.getConcept(), code.getValue());
            if (concept == null) {
                return buildResult(false, code, system, null, "Concept with code '" + code.getValue() + "' not found.");
            }

            boolean isValid = isDisplayValid(concept, display);
            String message = isValid ? "Code validated successfully." : "Display does not match.";

            return buildResult(isValid, code, system, concept, message);

        } catch (ResourceNotFoundException e) {
            return buildResult(false, code, system, null, e.getMessage());
        } catch (Exception e) {
            return buildErrorOutcome(e);
        }
    }

    // 驗證 code 和 system/resourceId 是否有給
    private void validateCodeAndSystem(CodeType code, UriType system, IdType resourceId) {
    	// code 必填
    	if (code == null || code.isEmpty()) {
            throw new InvalidRequestException("Parameter 'code' is required.");
        }
    	// 至少要有 system 或 resourceId
    	if (system == null && resourceId == null) {
            throw new InvalidRequestException("Either 'system' or resource ID must be provided.");
        }
    }

    // 檢查 display 是否符合概念定義中的 display
    private boolean isDisplayValid(ConceptDefinitionComponent concept, StringType display) {
    	 // 若 display 沒填或正確，就視為通過
    	return display == null || display.isEmpty() || display.getValue().equals(concept.getDisplay());
    }

    // 遞迴尋找指定 code 的 ConceptDefinitionComponent
    private ConceptDefinitionComponent findConceptRecursive(List<ConceptDefinitionComponent> concepts, String code) {
    	// 找到符合的 code
    	for (ConceptDefinitionComponent concept : concepts) {
            if (concept.getCode().equals(code)) {
                return concept;
            }
            // 遞迴搜尋子概念
            ConceptDefinitionComponent nested = findConceptRecursive(concept.getConcept(), code);
            if (nested != null) return nested;
        }
        return null;
    }

    // 建立結果回應的 Parameters 結構（符合 FHIR 的 output 標準）
    private Parameters buildResult(boolean isValid, CodeType code, UriType system, ConceptDefinitionComponent concept, String message) {
        Parameters result = new Parameters();
        // 驗證結果 true/false
        result.addParameter().setName("result").setValue(new BooleanType(isValid));
        // 回傳原始 code
        result.addParameter().setName("code").setValue(code);
        // 回傳原始 system
        result.addParameter().setName("system").setValue(system);
        // 回傳系統定義的 display（如果有）
        if (concept != null && concept.hasDisplay()) {
            result.addParameter().setName("display").setValue(new StringType(concept.getDisplay()));
        }
        // 驗證訊息
        result.addParameter().setName("message").setValue(new StringType(message));
        return result;
    }

    // 處理例外錯誤情況，包裝為 Parameters 回應
    private Parameters buildErrorOutcome(Exception e) {
        Parameters outcome = new Parameters();
        outcome.addParameter().setName("result").setValue(new BooleanType(false));
        outcome.addParameter().setName("message").setValue(new StringType("Internal error: " + e.getMessage()));
        return outcome;
    }

    // Dummy placeholder, implement based on your DAO/service
    private CodeSystem getCodeSystemById(String id, StringType version) {
    	try {
        	
            // 建立基本的查詢參數
            SearchParameterMap searchParams = new SearchParameterMap();
            searchParams.add("_id", new TokenParam(id));
            
            // 如果有指定版本，加入版本查詢條件
            if (version != null && !version.isEmpty()) {
                searchParams.add(CodeSystem.SP_VERSION, new StringParam(version.getValue()));
            }

            // 執行查詢
            IBundleProvider searchResult = dao.search(searchParams, null);
            
            if (searchResult.size() == 0) {
                throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
            }

            // 取得第一個符合的結果
            CodeSystem codeSystem = (CodeSystem) searchResult.getResources(0, 1).get(0);
            
            if (codeSystem == null) {
                throw new ResourceNotFoundException("CodeSystem not found with ID: " + id);
            }

            // 如果有指定版本但查詢結果的版本不符
            if (version != null && !version.isEmpty() && 
                !version.getValue().equals(codeSystem.getVersion())) {
                throw new ResourceNotFoundException(
                    String.format("Version %s not found for CodeSystem %s", 
                    version.getValue(), id)
                );
            }

            return codeSystem;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorException("Error retrieving CodeSystem", e);
        }
    }

    private CodeSystem validateFindCodeSystemWithConcept(String code, String system, String version) {
    	SearchParameterMap params = new SearchParameterMap();
        params.add(CodeSystem.SP_URL, new UriParam(system));
        
        if (version != null) {
            params.add(CodeSystem.SP_VERSION, new StringParam(version));
        } 

        IBundleProvider results = dao.search(params, null);

        for (IBaseResource resource : results.getResources(0, results.size())) {
            CodeSystem cs = (CodeSystem) resource;
            ConceptDefinitionComponent concept = findConceptRecursive(cs.getConcept(), code);
            if (concept != null) {
                return cs; // 找到含該 code 的 CodeSystem
            }
        }

        throw new ResourceNotFoundException(String.format(
            "No CodeSystem with system '%s' and version '%s' contains code '%s'",
            system, version, code
        ));
    }
    
	@Override
    public Class<CodeSystem> getResourceType() {
        return CodeSystem.class;
    }
}