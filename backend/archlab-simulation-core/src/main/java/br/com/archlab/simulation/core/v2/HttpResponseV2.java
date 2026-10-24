package br.com.archlab.simulation.core.v2;

import java.util.Map;

public record HttpResponseV2(int statusCode, Map<String, String> headers, String body) {
    public HttpResponseV2 {
        headers = headers == null ? null : Map.copyOf(headers);
    }
}
