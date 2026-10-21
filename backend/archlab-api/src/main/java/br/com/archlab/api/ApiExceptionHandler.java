package br.com.archlab.api;

import br.com.archlab.simulation.core.validation.InvalidArchitectureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidArchitectureException.class)
    public ResponseEntity<Map<String, Object>> invalidArchitecture(InvalidArchitectureException exception,
                                                                     HttpServletRequest request) {
        Map<String, Object> problem = problem(422, "/problems/invalid-architecture",
                "Arquitetura inválida", "A arquitetura não atende aos requisitos da simulação.",
                "ARCHITECTURE_INVALID", request);
        problem.put("violations", exception.result().violations());
        return ResponseEntity.status(422).contentType(MediaType.valueOf("application/problem+json")).body(problem);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
        return respond(400, "/problems/invalid-request", "Requisição inválida",
                "Não foi possível interpretar os dados enviados.", "INVALID_REQUEST", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMedia(Exception exception, HttpServletRequest request) {
        return respond(415, "/problems/unsupported-media-type", "Tipo de mídia não suportado",
                "Envie application/json.", "UNSUPPORTED_MEDIA_TYPE", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internalError(Exception exception, HttpServletRequest request) {
        return respond(500, "/problems/internal-error", "Erro interno",
                "A operação não pôde ser concluída.", "INTERNAL_ERROR", request);
    }

    private static ResponseEntity<Map<String, Object>> respond(int status, String type, String title, String detail,
                                                                  String code, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.valueOf("application/problem+json"))
                .body(problem(status, type, title, detail, code, request));
    }

    private static Map<String, Object> problem(int status, String type, String title, String detail,
                                                String code, HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("title", title);
        map.put("status", status);
        map.put("detail", detail);
        map.put("instance", request.getRequestURI());
        map.put("code", code);
        return map;
    }
}
