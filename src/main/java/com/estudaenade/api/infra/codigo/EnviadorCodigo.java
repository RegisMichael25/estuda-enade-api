package com.estudaenade.api.infra.codigo;

/** Entrega o código de acesso ao usuário. */
public interface EnviadorCodigo {
    void enviar(String email, String codigo, int validadeMinutos);
}
