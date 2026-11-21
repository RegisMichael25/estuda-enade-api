package com.estudaenade.api.infra.codigo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Envia o código por e-mail via SMTP (configure MAIL_HOST, MAIL_PORT, MAIL_USERNAME e MAIL_PASSWORD). */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "otp.provider", havingValue = "smtp")
public class SmtpEnviadorCodigo implements EnviadorCodigo {

    private final JavaMailSender mailSender;

    @Value("${otp.remetente}")
    private String remetente;

    @Override
    public void enviar(String email, String codigo, int validadeMinutos) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(email);
        mensagem.setSubject(codigo + " é o seu código do Estuda ENADE");
        mensagem.setText("""
                Seu código de acesso ao Estuda ENADE é %s.

                Ele vale por %d minutos e só pode ser usado uma vez.
                Se você não pediu este código, ignore este e-mail.
                """.formatted(codigo, validadeMinutos));
        mailSender.send(mensagem);
    }
}
