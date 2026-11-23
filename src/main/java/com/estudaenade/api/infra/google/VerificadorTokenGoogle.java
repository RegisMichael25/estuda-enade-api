package com.estudaenade.api.infra.google;

/** Valida o ID token do Google Identity Services. */
public interface VerificadorTokenGoogle {

    /** Lança 401 GOOGLE_TOKEN_INVALIDO (token inválido) ou 503 GOOGLE_DESABILITADO (sem configuração ou Google fora). */
    DadosGoogle verificar(String idToken);

    /** {@code nome} pode ser nulo. */
    record DadosGoogle(String sub, String email, String nome) {
    }
}
