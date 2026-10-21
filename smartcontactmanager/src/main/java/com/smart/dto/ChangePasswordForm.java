package com.smart.dto;

import jakarta.validation.constraints.NotBlank;

/** Passwords are never trimmed or logged (no toString on purpose). */
public class ChangePasswordForm {

    @NotBlank(message = "Enter your current password")
    private String currentPassword;

    @NotBlank(message = "Enter a new password")
    private String newPassword;

    @NotBlank(message = "Confirm your new password")
    private String confirmPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
