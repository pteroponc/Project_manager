package project_manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Test-client convenience for legacy scenarios. Token validation/replay tests bypass this helper. */
final class ArchitectureTestTokens {
    static String token(JdbcTemplate jdbc, String projectId) {
        var rows = jdbc.queryForList("select instance_id from project_architecture_passports where project_id=?",String.class,projectId);
        return rows.isEmpty()?"00000000-0000-0000-0000-000000000000":rows.get(0);
    }
    static void supply(MockHttpServletRequestBuilder builder, JdbcTemplate jdbc, ObjectMapper json) {
        builder.with(request -> {
            String path = request.getRequestURI(); String[] segments = path.split("/");
            if (segments.length<5 || !path.startsWith("/api/projects/") || !"architecture".equals(segments[4])) return request;
            String method=request.getMethod(); boolean base=segments.length==5;
            boolean protectedPost="POST".equals(method) && (path.endsWith("/components")||path.endsWith("/integrations")||path.endsWith("/risks")||path.endsWith("/checks/initialize"));
            if ("DELETE".equals(method)) {
                if (request.getParameter("expectedPassportInstanceId")==null) request.addParameter("expectedPassportInstanceId",token(jdbc,segments[3]));
            } else if (protectedPost || "PATCH".equals(method) && base) {
                try {
                    var body=json.readTree(request.getContentAsByteArray());
                    if (body instanceof ObjectNode object && !object.has("expectedPassportInstanceId")) {
                        // Insert without reserializing: keep duplicate fields/trailing JSON for strict-parser tests.
                        String raw=new String(request.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                        int brace=raw.indexOf('{');
                        String field="\"expectedPassportInstanceId\":\""+token(jdbc,segments[3])+"\""+(object.isEmpty()?"":",");
                        request.setContent((raw.substring(0,brace+1)+field+raw.substring(brace+1)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                } catch (java.io.IOException invalidJson) { /* Preserve malformed input for parser tests. */ }
            }
            return request;
        });
    }
}
