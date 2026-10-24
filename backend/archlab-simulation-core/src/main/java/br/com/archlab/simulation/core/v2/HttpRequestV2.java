package br.com.archlab.simulation.core.v2;

import java.util.Map;

/** Optional headers and body; maps are snapshotted to avoid external mutation. */
public record HttpRequestV2(HttpMethodV2 method, String path, Map<String, String> headers, String body) {
    public HttpRequestV2 {
        headers = headers == null ? null : Map.copyOf(headers);
    }
}
