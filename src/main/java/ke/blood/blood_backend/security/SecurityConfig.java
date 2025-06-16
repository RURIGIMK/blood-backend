package ke.blood.blood_backend.security;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration enabling:
 *  - CORS support via a CorsConfigurationSource defined here
 *  - CSRF disabled for stateless JWT APIs
 *  - Stateless session management
 *  - Public access to /auth/** and Swagger UI endpoints
 *  - JWT authentication for all other endpoints
 */
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS using the CorsConfigurationSource bean defined below
                .cors(Customizer.withDefaults())

                // Disable CSRF because we use stateless JWT
                .csrf(csrf -> csrf.disable())

                // Stateless session: do not create or use HTTP sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Permit unauthenticated access to registration/login
                        .requestMatchers("/auth/**").permitAll()
                        // Permit Swagger/OpenAPI endpoints
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Define CORS configuration here so no separate WebConfig is needed.
     * Adjust allowedOrigins(...) to match your frontend origin(s).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow the React frontend origin; change if your frontend runs elsewhere
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        // Allow common HTTP methods your frontend uses
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow all headers, including Authorization
        configuration.setAllowedHeaders(List.of("*"));
        // Allow credentials (e.g., if Authorization header or cookies are sent)
        configuration.setAllowCredentials(true);
        // Optional: how long the response from a pre-flight request can be cached by clients
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this CORS config to all endpoints
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Password encoder bean for encoding user passwords (BCrypt).
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
