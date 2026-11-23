package com.estudaenade.api.support;

import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Google falso: o "ID token" é {@code google|sub|email|nome}; qualquer outro texto é inválido. */
@TestConfiguration(proxyBeanMethods = false)
public class GoogleFalsoConfiguration {

    public static String token(String sub, String email, String nome) {
        return "google|%s|%s|%s".formatted(sub, email, nome);
    }

    public static String tokenNaoVerificado(String sub, String email, String nome) {
        return "google-nao-verificado|%s|%s|%s".formatted(sub, email, nome);
    }

    @Bean
    @Primary
    VerificadorTokenGoogle verificadorTokenGoogleFalso() {
        return idToken -> {
            String[] partes = idToken.split("\\|", -1);
            if (partes.length != 4 || !partes[0].equals("google")) {
                throw ApiException.naoAutorizado("GOOGLE_TOKEN_INVALIDO", "Token do Google inválido.");
            }
            return new VerificadorTokenGoogle.DadosGoogle(partes[1], partes[2], partes[3].isEmpty() ? null : partes[3]);
        };
    }
}
