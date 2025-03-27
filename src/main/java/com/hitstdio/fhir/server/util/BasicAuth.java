package com.hitstdio.fhir.server.util;

import java.util.Base64;

import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

public class BasicAuth {
    public String[] decodeAndExtractCredentials(String authorization) {
        if (authorization != null && authorization.startsWith("Basic ")) {
            String base64Credentials = authorization.substring(6);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String decodedString = new String(decodedBytes);
            String[] credentials = decodedString.split(":", 2);
            if (credentials.length == 2) {
                return credentials;
            }
        }
        throw new UnprocessableEntityException("Header中缺少Authorization參數，請於Header中提供Basic Auth的授權資訊。");
    }
}
