package com.carent.auth.web;

import com.carent.auth.token.IssuedToken;
import com.carent.auth.token.TokenService;
import com.carent.auth.user.AuthenticatedUser;
import com.carent.auth.user.UserDirectory;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/auth/token}: username and password in, HS256 JWT with roles out (specs/05). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserDirectory users;
    private final TokenService tokens;

    public AuthController(UserDirectory users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        AuthenticatedUser user = users.authenticate(request.username(), request.password())
                .orElseThrow(InvalidCredentialsException::new);
        IssuedToken token = tokens.issue(user.username(), user.roles());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.value(), "Bearer", token.expiresInSeconds()));
    }
}
