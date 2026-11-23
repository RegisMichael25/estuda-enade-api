package com.estudaenade.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP e um código estável para os clientes (app e portal). */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public static ApiException naoEncontrado(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", mensagem);
    }

    public static ApiException acessoNegado(String mensagem) {
        return new ApiException(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", mensagem);
    }

    /** Regra de negócio violada (422). */
    public static ApiException regra(String codigo, String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem);
    }

    public static ApiException naoAutorizado(String codigo, String mensagem) {
        return new ApiException(HttpStatus.UNAUTHORIZED, codigo, mensagem);
    }

    public static ApiException muitasSolicitacoes(String mensagem) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MUITAS_SOLICITACOES", mensagem);
    }

    public static ApiException conflito(String codigo, String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensagem);
    }

    public static ApiException indisponivel(String codigo, String mensagem) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensagem);
    }
}
