package com.catshop.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper to read the authenticated member id from the SecurityContext. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long currentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }

    public static Long requireMemberId() {
        Long id = currentMemberId();
        if (id == null) {
            throw com.catshop.exception.BusinessException.unauthorized("請先登入");
        }
        return id;
    }
}
