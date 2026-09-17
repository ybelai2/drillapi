package com.os439.drillapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
class AuthController {
    private final Accounts accounts;
    private final AuthSessions sessions;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    AuthController(Accounts accounts, AuthSessions sessions, PasswordEncoder encoder) {
        this.accounts=accounts; this.sessions=sessions; this.encoder=encoder;
        this.dummyHash=encoder.encode(UUID.randomUUID().toString());
    }
    record Signup(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) String password, @NotBlank @Size(max=80) String name) {}
    record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    record User(UUID id, String email, String name) {}
    record Session(String token, Instant expiresAt, User user) {}
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private User user(Account a) { return new User(a.id,a.email,a.name); }
    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    Session signup(@Valid @RequestBody Signup input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes.");
        Account a=new Account(); a.id=UUID.randomUUID(); a.email=email(input.email());
        a.name=input.name().trim(); a.passwordHash=encoder.encode(input.password());
        if(accounts.findByEmail(a.email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");
        accounts.saveAndFlush(a);
        return issue(a);
    }
    @PostMapping("/login") Session login(@Valid @RequestBody Login input) {
        Account a=accounts.findByEmail(email(input.email())).orElse(null);
        boolean valid=input.password().getBytes(StandardCharsets.UTF_8).length<=72
            && encoder.matches(input.password(), a==null ? dummyHash : a.passwordHash);
        if(a==null || !valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        return issue(a);
    }
    private Session issue(Account a) {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthSession s=new AuthSession(); s.tokenHash=hash(token); s.accountId=a.id;
        s.expiresAt=Instant.now().plus(7,ChronoUnit.DAYS); sessions.save(s);
        return new Session(token,s.expiresAt,user(a));
    }
    @GetMapping("/me") User me(Principal principal) {
        return user(accounts.findById(UUID.fromString(principal.getName())).orElseThrow());
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("Authorization") String authorization) { sessions.deleteById(hash(authorization.substring(7))); }
}
