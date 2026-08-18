package com.pharmacy.controller;

import com.pharmacy.dto.AuthDtos.*;
import com.pharmacy.model.UserAccount;
import com.pharmacy.repository.UserRepository;
import com.pharmacy.security.JwtService;
import com.pharmacy.security.RsaSupport;
import com.pharmacy.service.PharmacyService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    private final PharmacyService pharmacyService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService, PharmacyService pharmacyService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.pharmacyService = pharmacyService;
    }

    @GetMapping("/public-key")
    public String getPublicKey() {
        return RsaSupport.getPublicKeyBase64();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!user.active) {
            throw new BadCredentialsException("Account is locked. Please contact your administrator.");
        }

        if (user.passwordResetRequired) {
            if (user.temporaryPasswordExpiry != null && user.temporaryPasswordExpiry.isBefore(java.time.Instant.now())) {
                throw new BadCredentialsException("Temporary password has expired. Please request a new password reset.");
            }
        }

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.username, RsaSupport.decrypt(request.password())));

        return new LoginResponse(jwtService.create(user), user.username, user.fullName, user.role, user.passwordResetRequired);
    }

    @PostMapping("/register/otp")
    public void sendRegistrationOtp(@Valid @RequestBody RegisterOtpRequest request) {
        pharmacyService.sendRegistrationOtp(request);
    }

    @PostMapping("/register")
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        RegisterRequest decryptedRequest = new RegisterRequest(
                request.username(),
                RsaSupport.decrypt(request.password()),
                request.fullName(),
                request.mobile(),
                request.email(),
                request.otp()
        );
        return pharmacyService.registerUser(decryptedRequest);
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        pharmacyService.forgotPassword(request);
    }

    @PostMapping("/force-change-password")
    public LoginResponse forceChangePassword(@Valid @RequestBody ForcePasswordChangeRequest request) {
        pharmacyService.forceChangePassword(
                request.username(),
                RsaSupport.decrypt(request.temporaryPassword()),
                RsaSupport.decrypt(request.newPassword())
        );
        UserAccount user = users.findByUsernameIgnoreCase(request.username()).orElseThrow();
        return new LoginResponse(jwtService.create(user), user.username, user.fullName, user.role, false);
    }
}
