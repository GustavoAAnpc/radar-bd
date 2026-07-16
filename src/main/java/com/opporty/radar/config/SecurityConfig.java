package com.opporty.radar.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos
                        .requestMatchers(
                                "/auth/authenticate", "/api/v1/auth/authenticate",
                                "/auth/refresh-token", "/api/v1/auth/refresh-token",
                                "/auth/register/student", "/api/v1/auth/register/student",
                                "/error"
                        ).permitAll()
                        // Registro genérico requiere ADMIN
                        .requestMatchers("/auth/register", "/api/v1/auth/register").hasRole("ADMIN")
                        // Endpoints de usuario propios (push token, intereses, etc.)
                        .requestMatchers("/users/me/**", "/api/v1/users/me/**").authenticated()
                        // Solo ADMIN puede gestionar usuarios y roles
                        .requestMatchers("/users/**", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/roles/**", "/api/v1/roles/**").hasRole("ADMIN")
                        // Acceso a perfiles de estudiantes y profesores
                        .requestMatchers("/students/me", "/api/v1/students/me").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/students", "/api/v1/students").authenticated()
                        .requestMatchers("/students/**", "/api/v1/students/**").hasRole("ADMIN")
                        .requestMatchers("/teachers/me", "/api/v1/teachers/me").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/teachers", "/api/v1/teachers").authenticated()
                        .requestMatchers("/teachers/**", "/api/v1/teachers/**").hasRole("ADMIN")
                        // El resto necesita autenticación
                        .anyRequest().authenticated()

                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
