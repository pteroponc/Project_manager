package project_manager.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class BrowserWriteProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    static final String HEADER = "X-PM-Write";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String site = request.getHeader("Sec-Fetch-Site");
        // The marker is public. Browsers must preflight cross-origin requests carrying it.
        if (!"1".equals(request.getHeader(HEADER)) || (site != null && !"same-origin".equals(site))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                {"code":"WRITE_REQUEST_FORBIDDEN","error":"Write request failed browser protection checks"}
                """);
            return;
        }
        chain.doFilter(request, response);
    }
}
