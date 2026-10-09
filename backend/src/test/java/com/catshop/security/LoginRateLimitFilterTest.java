package com.catshop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimitFilterTest {
    @Test void resetRequestsAndTokenAttemptsHaveSeparateIpWindows() throws Exception {
        Clock clock=mock(Clock.class);when(clock.millis()).thenReturn(0L);
        var filter=new LoginRateLimitFilter(new ObjectMapper().findAndRegisterModules(),1,60000,clock);
        for(String path:java.util.List.of("/api/auth/forgot-password","/api/auth/reset-password")){
            assertThat(call(filter,path,"127.0.0.1").getStatus()).isEqualTo(200);
            assertThat(call(filter,path,"127.0.0.1").getStatus()).isEqualTo(429);
        }
        assertThat(call(filter,"/api/auth/login","127.0.0.1").getStatus()).isEqualTo(200);
    }
    @Test void limitsLoginAndRecoversAfterWindowWithoutTrustingForwardedHeaders() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new ObjectMapper().findAndRegisterModules(), 2, 60000, clock);
        for (int i = 0; i < 2; i++) assertThat(call(filter, "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = call(filter, "/api/auth/login", "127.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(call(filter, "/api/auth/login", "127.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/auth/register", "127.0.0.1").getStatus()).isEqualTo(200);
        when(clock.millis()).thenReturn(60001L);
        assertThat(call(filter, "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(LoginRateLimitFilter filter, String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path); request.setRemoteAddr(address);
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }
}
