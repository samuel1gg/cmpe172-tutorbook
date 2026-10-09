package edu.sjsu.cmpe172.tutorbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Session-based login with role-based access control (RBAC).
 *
 * Login: POST /login with email + password. On success Spring Security stores
 * the AppUser (with its role from the users table) in the server-side HTTP
 * session; the browser only holds the JSESSIONID cookie.
 *
 * A logged-in user with the wrong role gets 403 Forbidden.
 * An anonymous user is redirected to /login.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // public pages
                .requestMatchers("/", "/slots", "/login", "/error", "/403",
                        "/css/**", "/api/**", "/actuator/health", "/h2-console/**").permitAll()
                // customer-only
                .requestMatchers("/slots/*/book", "/my/**").hasRole("CUSTOMER")
                // provider-only
                .requestMatchers("/provider/**").hasRole("PROVIDER")
                // anything else (e.g. confirmation page) just needs a login
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/", false)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/?logout"))
            // wrong role -> 403 with our error page
            .exceptionHandling(ex -> ex.accessDeniedPage("/403"))
            // H2 console runs in frames and posts without a CSRF token (dev only)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /** Passwords are hashed with BCrypt (salted, slow on purpose); never stored as plaintext. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
