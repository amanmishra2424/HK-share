package com.pdfprinting.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("JWT".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            try {
                var claims = jwtUtil.parseClaims(token);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    var auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // invalid token -> ignore and continue (request will be unauthorized if required)
            }
        }

        filterChain.doFilter(request, response);
    }
}
