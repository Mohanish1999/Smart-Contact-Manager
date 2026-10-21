package com.smart.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class MyConfig {

    private static final int REMEMBER_ME_SECONDS = 14 * 24 * 60 * 60;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authProvider,
            @Value("${app.remember-me-key:}") String rememberMeKey) throws Exception {

        // Set REMEMBER_ME_KEY in production so "remember me" survives restarts; otherwise a random key is used.
        String key = rememberMeKey == null || rememberMeKey.isBlank() ? UUID.randomUUID().toString() : rememberMeKey;

        // CSRF protection stays ON (Spring Security default). Forms built with th:action carry the token
        // automatically, and the JavaScript that calls the API sends it in a header.
        http
            // Razorpay's server cannot send a CSRF token; the webhook is authenticated by its HMAC signature instead.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/razorpay/webhook"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/about", "/signup", "/signin", "/dologin",
                    "/forgot", "/send-otp", "/verify-otp", "/change-password",
                    "/user/do_register", "/razorpay/webhook",
                    "/error", "/favicon.ico",
                    "/css/**", "/js/**", "/img/**", "/images/**", "/fonts/**", "/vendor/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                .anyRequest().authenticated()   // includes /search/** and the uploaded photos
            )
            .authenticationProvider(authProvider)
            .formLogin(form -> form
                .loginPage("/signin")
                .loginProcessingUrl("/dologin")
                .defaultSuccessUrl("/user/index", true)
                .failureUrl("/signin?error")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(key)
                .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                .userDetailsService(userDetailsService())
            )
            .logout(logout -> logout
                .logoutUrl("/logout")                 // POST only while CSRF is enabled
                .logoutSuccessUrl("/signin?logout")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
