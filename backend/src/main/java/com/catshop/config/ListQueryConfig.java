package com.catshop.config;

import com.catshop.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Scalar-only list queries; runs after the security filter chain. */
@Configuration
public class ListQueryConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("GET".equals(request.getMethod()) && request.getParameterMap().values().stream().anyMatch(values -> values.length > 1))
                    throw new BusinessException("DUPLICATE_QUERY_PARAMETER", "列表查詢參數不可重複", org.springframework.http.HttpStatus.BAD_REQUEST);
                return true;
            }
        }).addPathPatterns("/api/products", "/api/orders", "/api/wishlist",
                "/api/admin/products", "/api/admin/orders", "/api/admin/categories", "/api/admin/coupons",
                "/api/admin/members", "/api/admin/members/*/history",
                "/api/admin/inventory/low-stock", "/api/admin/inventory/movements");
    }
}
