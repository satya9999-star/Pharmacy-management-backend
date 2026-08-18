package com.pharmacy.dto;

import com.pharmacy.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String username, String fullName, Role role, boolean passwordResetRequired) {}
    public record ForcePasswordChangeRequest(@NotBlank String username, @NotBlank String temporaryPassword, @NotBlank String newPassword) {}
    public record ForgotPasswordRequest(@NotBlank String username, @NotBlank String mobile) {}
    public record UserRequest(@NotBlank String username, String password, @NotBlank String fullName, String mobile, String email, @NotNull Role role, boolean active, String otp) {}
    public record UserView(Long id, String username, String fullName, String mobile, String email, Role role, boolean active, Instant createdAt) {}
    public record PasswordResetRequest(@NotBlank String password) {}
    public record RegisterOtpRequest(@NotBlank String username, @NotBlank String mobile, @NotBlank String email) {}
    public record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotBlank String fullName, @NotBlank String mobile, @NotBlank String email, @NotBlank String otp) {}
}
