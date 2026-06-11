package com.ragstudy.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    public boolean matches(String password, String storedHash) {
        return passwordEncoder.matches(password, storedHash);
    }
}
