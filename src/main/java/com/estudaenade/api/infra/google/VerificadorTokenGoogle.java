package com.estudaenade.api.infra.google;

/** Valida o ID token que o cliente recebeu do Google Identity Services. */
public interface VerificadorTokenGoogle {

    /**
     * Dados da conta Google, só se o token for válido para este aplicativo e o e-mail estiver verificado.
     *
     * @throws com.estudaenade.api.exception.ApiException {@code 401 GOOGLE_TOKEN_INVALIDO} para token inválido,
     *         ou {@code 503 GOOGLE_DESABILITADO} quando o login com Google não está configurado ou o Google
     *         não pôde ser consultado
     */
    DadosGoogle verificar(String idToken);

    /**
     * @param sub   identificador estável da conta Google
     * @param email e-mail da conta, já verificado pelo Google
     * @param nome  nome da conta (claim {@code name}); pode ser nulo
     */
    record DadosGoogle(String sub, String email, String nome) {
    }
}
