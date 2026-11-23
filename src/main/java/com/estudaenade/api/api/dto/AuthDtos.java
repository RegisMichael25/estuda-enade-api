package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.model.Usuario;

import java.util.UUID;

/** Respostas desta área. As requisições são geradas do contrato OpenAPI (pacote {@code api.contrato.dto}). */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * @param aprovado        falso só para professor que ainda aguarda aprovação
     * @param temSenha        falso quando a conta só entra com Google ou ainda precisa redefinir a senha
     * @param googleVinculado se a conta está vinculada a uma conta Google
     */
    public record UsuarioResponse(UUID id, String nome, String email, Papel papel, boolean aprovado,
                                  boolean temSenha, boolean googleVinculado) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAprovado(),
                    u.getSenhaHash() != null, u.getGoogleSub() != null);
        }
    }

    public record SessaoResponse(String accessToken, String refreshToken, long expiraEmSegundos,
                                 UsuarioResponse usuario) {
    }
}
