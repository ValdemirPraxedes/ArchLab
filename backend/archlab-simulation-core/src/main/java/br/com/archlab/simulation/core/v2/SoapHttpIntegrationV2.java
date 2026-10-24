package br.com.archlab.simulation.core.v2;

import java.util.Map;

/** XML is kept as passive text. Validation of XML safety belongs to BE-08. */
public record SoapHttpIntegrationV2(String soapVersion, String operation, String action,
                                    String requestEnvelope, String responseEnvelope,
                                    Integer httpStatusCode, Map<String, String> requestHeaders,
                                    Map<String, String> responseHeaders) implements IntegrationV2 {
    public SoapHttpIntegrationV2 {
        requestHeaders = requestHeaders == null ? null : Map.copyOf(requestHeaders);
        responseHeaders = responseHeaders == null ? null : Map.copyOf(responseHeaders);
    }
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.SOAP_HTTP; }
}
