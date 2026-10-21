package com.smart.config;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.smart.entity.User;

public class CustomUserDetails implements UserDetails {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(user.getRole());
        return List.of(simpleGrantedAuthority);
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // Must be BCrypt encoded
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // Or user.getUsername() depending on your entity
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Or implement logic based on your User entity
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Or implement logic based on your User entity
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Or implement logic based on your User entity
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled(); // Make sure your User entity has an 'enabled' field
    }
}
