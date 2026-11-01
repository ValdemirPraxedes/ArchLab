package br.com.archlab.api.v2;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.util.*;

/** Bounded v2 JSON request buffering, including requests without Content-Length. */
@Component
public final class V2BodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 1024 * 1024;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/v2/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            tooLarge(request,response);
            return;
        }
        byte[] payload=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(payload.length>MAX_BYTES) {
            tooLarge(request,response);
            return;
        }
        HttpServletRequest wrapped=new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return payload.length; }
            @Override public long getContentLengthLong() { return payload.length; }
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream bytes=new ByteArrayInputStream(payload);
                return new ServletInputStream() {
                    @Override public int read() { return bytes.read(); }
                    @Override public boolean isFinished() { return bytes.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Synchronous JSON body only");
                    }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        chain.doFilter(wrapped,response);
    }
    private static void tooLarge(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        String instance=request.getRequestURI().replace("\\","\\\\").replace("\"","\\\"");
        response.getWriter().write("{\"type\":\"/problems/payload-too-large\","
                +"\"title\":\"Payload Too Large\",\"status\":413,"
                +"\"detail\":\"Request exceeds 1 MiB limit\","
                +"\"instance\":\""+instance+"\",\"code\":\"PAYLOAD_TOO_LARGE\"}");
    }
}
