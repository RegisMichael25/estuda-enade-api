package com.estudaenade.api.infra.codigo;

/** Entrega ao usuário o código para redefinir a senha. */
public interface EnviadorCodigo {
    void enviar(String email, String codigo, int validadeMinutos);
}
