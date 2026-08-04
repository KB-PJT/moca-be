package com.moca.mocabe.global.auth;

import java.io.IOException;
import java.util.Collections;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authorization Bearer의 MOCA opaque access token을 Redis 세션으로 인증한다. */
public class MocaOpaqueAuthenticationFilter extends OncePerRequestFilter {

    private final OpaqueTokenService opaqueTokenService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public MocaOpaqueAuthenticationFilter(OpaqueTokenService opaqueTokenService,
                                          ApiErrorResponseWriter errorResponseWriter) {
        this.opaqueTokenService = opaqueTokenService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token != null) {
            try {
                AuthenticatedUser user = opaqueTokenService.authenticate(token);
                MocaUserPrincipal principal = new MocaUserPrincipal(user.getUserId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidOpaqueTokenException exception) {
                SecurityContextHolder.clearContext();
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN",
                        "유효하지 않거나 만료된 토큰입니다.");
                return;
            } catch (IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN",
                        "유효하지 않거나 만료된 토큰입니다.");
                return;
            } catch (DataAccessException exception) {
                SecurityContextHolder.clearContext();
                errorResponseWriter.write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "DATA_STORE_UNAVAILABLE", "데이터 저장소에 일시적으로 연결할 수 없습니다.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
