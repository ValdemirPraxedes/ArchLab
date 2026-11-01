package br.com.archlab.simulation.core.v2;

import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/**
 * Snapshot only executed connections, in exchange initiation order.
 * No HTTP, SOAP or database operation is performed.
 * Fails closed on unsafe XML or out-of-contract representation fields.
 */
public final class MessageRepresentationProjectorV2 {
    public List<MessageRepresentationV2> project(ArchitectureV2 architecture,
                                                   List<ExchangeTraceV2> exchanges) {
        Objects.requireNonNull(architecture);
        Objects.requireNonNull(exchanges);
        Map<String, ConnectionV2> connections = new HashMap<>();
        for (var connection : architecture.connections()) connections.put(connection.id(), connection);
        List<MessageRepresentationV2> result = new ArrayList<>();
        Set<String> included = new HashSet<>();
        if (exchanges.size() > 3000) throw new IllegalArgumentException("Too many message representations");
        for (var exchange : exchanges) {
            if (!included.add(exchange.connectionId())) continue;
            ConnectionV2 connection = Objects.requireNonNull(connections.get(exchange.connectionId()),
                    "Unknown executed connection");
            result.add(new MessageRepresentationV2(connection.id(), snapshot(connection.integration())));
        }
        return List.copyOf(result);
    }

    private IntegrationV2 snapshot(IntegrationV2 integration) {
        Objects.requireNonNull(integration, "Missing integration");
        if (integration instanceof HttpRestIntegrationV2 rest) {
            HttpRequestV2 req = rest.request() == null
                    ? new HttpRequestV2(HttpMethodV2.GET, "/", Map.of(), null) : rest.request();
            HttpResponseV2 res = rest.response() == null
                    ? new HttpResponseV2(200, Map.of(), null) : rest.response();
            if (req.method() == null || invalid(req.path(),2048,true)
                    || res.statusCode()<100 || res.statusCode()>599)
                throw new IllegalArgumentException("Invalid HTTP request/response template");
            headers(req.headers()); headers(res.headers());
            limit(req.body(),32768); limit(res.body(),32768);
            return new HttpRestIntegrationV2(
                    new HttpRequestV2(req.method(),req.path(),req.headers(),req.body()),
                    new HttpResponseV2(res.statusCode(),res.headers(),res.body()));
        }
        if (integration instanceof SoapHttpIntegrationV2 soap) {
            if (!"1.2".equals(soap.soapVersion()) || invalid(soap.operation(),256,false)
                    || invalid(soap.action(),2048,false)
                    || (soap.httpStatusCode()!=null &&
                        (soap.httpStatusCode()<100 || soap.httpStatusCode()>599)))
                throw new IllegalArgumentException("Invalid SOAP 1.2 template");
            limit(soap.requestEnvelope(),32768); limit(soap.responseEnvelope(),32768);
            headers(soap.requestHeaders()); headers(soap.responseHeaders());
            safeXml(soap.requestEnvelope()); safeXml(soap.responseEnvelope());
            return new SoapHttpIntegrationV2(soap.soapVersion(),soap.operation(),soap.action(),
                    soap.requestEnvelope(),soap.responseEnvelope(),soap.httpStatusCode(),
                    soap.requestHeaders(),soap.responseHeaders());
        }
        if (integration instanceof DatabaseQueryIntegrationV2 query) {
            limit(query.queryText(),32768); limit(query.responseText(),32768);
            return new DatabaseQueryIntegrationV2(query.queryText(),query.responseText());
        }
        throw new IllegalArgumentException("Unsupported integration template");
    }

    private static boolean invalid(String value,int max,boolean required) {
        return (required && (value==null || value.isEmpty()))
                || (value!=null && value.codePointCount(0,value.length())>max);
    }
    private static void limit(String value,int max) {
        if(invalid(value,max,false))throw new IllegalArgumentException("Template field exceeds limit");
    }
    private static void headers(Map<String,String> headers) {
        if(headers==null)return;
        if(headers.size()>32)throw new IllegalArgumentException("Too many headers");
        for(var entry:headers.entrySet()) {
            if(invalid(entry.getKey(),128,true) || invalid(entry.getValue(),1024,false)
                    || entry.getValue()==null)
                throw new IllegalArgumentException("Invalid header");
        }
    }
    private static void safeXml(String xml) {
        if(xml==null)return;
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException {
                    throw ex;
                }
            });
            builder.parse(new InputSource(new StringReader(xml)));
        } catch(Exception ex) {
            throw new IllegalArgumentException("Unsafe or malformed SOAP XML",ex);
        }
    }
}
