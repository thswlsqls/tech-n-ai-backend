package com.tech.n.ai.api.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 시드 데이터용 BCrypt 해시를 만든다. 비밀번호는 저장소에 두지 않으므로 첫 번째 인자로 받는다.
 */
public class BcryptHashGenerator {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: BcryptHashGenerator <raw-password>");
            System.exit(1);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String rawPassword = args[0];
        String encoded = encoder.encode(rawPassword);

        System.out.println("BCrypt Hash : " + encoded);
        System.out.println("Matches     : " + encoder.matches(rawPassword, encoded));
    }
}
