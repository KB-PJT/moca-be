package com.moca.mocabe.global.auth;

import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Spring Security Context에서 인증된 MOCA 사용자를 읽는다. */
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof MocaUserPrincipal)) {
            throw new AuthenticationRequiredException();
        }

        return ((MocaUserPrincipal) authentication.getPrincipal()).getUserId();
    }
}
