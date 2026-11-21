package com.estudaenade.api.infra.security;

import com.estudaenade.api.domain.enums.Papel;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Quem está fazendo a requisição (principal do Spring Security, montado a partir do JWT).
 *
 * @param aprovado falso só para professor com cadastro pendente; ele não acessa as rotas do professor
 */
public record UsuarioLogado(UUID id, Papel papel, boolean aprovado) {

    /**
     * Usuário da requisição atual. Os controllers implementam interfaces geradas do contrato OpenAPI,
     * que não têm parâmetro para o usuário; por isso ele é lido do contexto de segurança.
     */
    public static UsuarioLogado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioLogado usuario) {
            return usuario;
        }
        throw new AuthenticationCredentialsNotFoundException("Requisição sem usuário autenticado");
    }

    /** Papel do Spring Security. Professor pendente não recebe o papel de professor. */
    public String autoridade() {
        return papel == Papel.PROFESSOR && !aprovado ? "ROLE_PROFESSOR_PENDENTE" : "ROLE_" + papel.name();
    }
}
