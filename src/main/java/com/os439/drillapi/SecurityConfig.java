package com.os439.drillapi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthSessions sessions,
            @Value("${app.allowed-origins}") String origins) throws Exception {
        var limits = new RequestLimits();
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        // Authentication uses explicit Authorization headers, never ambient cookies.
        return http.cors(c -> c.configurationSource(source)).csrf(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.requestMatchers("/ping", "/api/auth/signup", "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> {
                res.setStatus(401); res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
            }))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                        FilterChain chain) throws ServletException, IOException {
                    String header = req.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ") && header.length() < 256) {
                        sessions.findById(AuthController.hash(header.substring(7)))
                            .filter(s -> s.expiresAt.isAfter(Instant.now())).ifPresent(s ->
                                SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(s.accountId.toString(), null, List.of())));
                    }
                    String path=req.getRequestURI();
                    boolean auth=path.equals("/api/auth/login") || path.equals("/api/auth/signup");
                    var identity=SecurityContextHolder.getContext().getAuthentication();
                    boolean generate=path.equals("/api/generate") || path.endsWith("/generate");
                    if(req.getMethod().equals("POST") && ((auth && !limits.allow("auth:"+req.getRemoteAddr(),30,60000))
                        || (generate && identity!=null && !limits.allow("generate:"+identity.getName(),10,3600000)))) {
                        res.setStatus(429); res.setContentType("application/json");
                        res.getWriter().write("{\"message\":\"Too many requests. Please wait before trying again.\"}");
                        return;
                    }
                    chain.doFilter(req, res);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }
}
