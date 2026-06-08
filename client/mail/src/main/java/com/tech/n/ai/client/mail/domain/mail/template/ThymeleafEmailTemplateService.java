package com.tech.n.ai.client.mail.domain.mail.template;

import lombok.RequiredArgsConstructor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RequiredArgsConstructor
public class ThymeleafEmailTemplateService implements EmailTemplateService {
    
    private final TemplateEngine templateEngine;
    
    @Override
    public String renderVerificationEmail(String email, String token, String verifyUrl) {
        Context context = baseContext(email, token);
        context.setVariable("verifyUrl", verifyUrl);
        return templateEngine.process("email/verification", context);
    }

    @Override
    public String renderPasswordResetEmail(String email, String token, String resetUrl) {
        Context context = baseContext(email, token);
        context.setVariable("resetUrl", resetUrl);
        return templateEngine.process("email/password-reset", context);
    }

    private Context baseContext(String email, String token) {
        Context context = new Context();
        context.setVariable("email", email);
        context.setVariable("token", token);
        return context;
    }
}
