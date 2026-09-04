package com.lovemaptually.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.auth.JwtAuthenticationFilter;
import com.lovemaptually.common.GlobalExceptionHandler.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper mapper) throws Exception {
        return http.csrf(c -> c.disable()).cors(c -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**", "/api/invites/*", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(), new ErrorResponse(401, "인증이 필요합니다", new ErrorBody("UNAUTHORIZED", List.of())));
                }))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
}
