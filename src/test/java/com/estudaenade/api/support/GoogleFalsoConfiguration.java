package com.estudaenade.api.support;

import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Substitui a validação real do Google nos testes de integração: nenhum teste chama o Google.
 * O "ID token" é um texto {@code google|sub|email|nome}; {@code google-nao-verificado|...} simula e-mail não
 * verificado. Qualquer outro texto é recusado como token inválido. A validação real (assinatura, iss, aud, exp)
 * é testada em {@code NimbusVerificadorTokenGoogleTest}.
 */
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
