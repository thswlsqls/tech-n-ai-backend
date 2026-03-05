package com.tech.n.ai.api.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Admin1234!";
        String encoded = encoder.encode(rawPassword);

        System.out.println("Raw Password: " + rawPassword);
        System.out.println("BCrypt Hash : " + encoded);
        System.out.println("Matches     : " + encoder.matches(rawPassword, encoded));
    }
}
