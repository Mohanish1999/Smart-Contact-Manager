package com.smart.dto;

import com.smart.entity.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The only profile fields a user may change. Email (the login id), role, enabled and password
 * are intentionally absent, so they cannot be altered through the profile form.
 */
public class ProfileForm {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    @Pattern(regexp = "^(\\p{L}[\\p{L} .'-]*)?$", message = "Name can contain letters, spaces and . ' - only")
    private String name;

    @Size(max = 500, message = "About cannot exceed 500 characters")
    private String about;

    public static ProfileForm from(User user) {
        ProfileForm form = new ProfileForm();
        form.setName(user.getName());
        form.setAbout(user.getAbout());
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about == null ? null : about.trim();
    }
}
