package com.neo4flix.user.web;

import com.neo4flix.security.CurrentUser;
import com.neo4flix.user.service.AuthService;
import com.neo4flix.user.service.TwoFactorService;
import com.neo4flix.user.web.TwoFactorDtos.CodeRequest;
import com.neo4flix.user.web.TwoFactorDtos.DisableRequest;
import com.neo4flix.user.web.TwoFactorDtos.EnableResponse;
import com.neo4flix.user.web.TwoFactorDtos.SetupResponse;
import com.neo4flix.user.web.TwoFactorDtos.VerifyRequest;
import com.neo4flix.user.web.AuthDtos.LoginRequest;
import com.neo4flix.user.web.AuthDtos.LoginResponse;
import com.neo4flix.user.web.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final TwoFactorService twoFactor;

    public AuthController(AuthService auth, TwoFactorService twoFactor) {
        this.auth = auth;
        this.twoFactor = twoFactor;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** Second login step: the pending token from /login plus a 6-digit TOTP code (or a recovery code). */
    @PostMapping("/2fa/verify")
    public LoginResponse verify(@Valid @RequestBody VerifyRequest request) {
        return twoFactor.verifyLogin(request.mfaToken(), request.code());
    }

    @PostMapping("/2fa/setup")
    public SetupResponse setup() {
        return twoFactor.setup(CurrentUser.id());
    }

    @PostMapping("/2fa/enable")
    public EnableResponse enable(@Valid @RequestBody CodeRequest request) {
        return twoFactor.enable(CurrentUser.id(), request.code());
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody DisableRequest request) {
        twoFactor.disable(CurrentUser.id(), request.password(), request.code());
    }
}
