package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.AuthApi;
import com.estudaenade.api.api.contrato.dto.EntrarRequest;
import com.estudaenade.api.api.contrato.dto.NomeRequest;
import com.estudaenade.api.api.contrato.dto.RefreshRequest;
import com.estudaenade.api.api.contrato.dto.SolicitarCodigoRequest;
import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.api.dto.AuthDtos.UsuarioResponse;
import com.estudaenade.api.domain.service.AuthService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/** Login sem senha: o usuário pede um código por e-mail e entra com ele. Rotas em {@link AuthApi}. */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public void solicitarCodigo(SolicitarCodigoRequest req) {
        authService.solicitarCodigo(req.getEmail());
    }

    @Override
    public SessaoResponse entrar(EntrarRequest req) {
        return authService.entrar(req);
    }

    @Override
    public SessaoResponse renovar(RefreshRequest req) {
        return authService.renovar(req.getRefreshToken());
    }

    @Override
    public void sair(RefreshRequest req) {
        authService.sair(req.getRefreshToken());
    }

    @Override
    public UsuarioResponse eu() {
        return authService.eu(UsuarioLogado.atual().id());
    }

    @Override
    public UsuarioResponse atualizarNome(NomeRequest req) {
        return authService.atualizarNome(UsuarioLogado.atual().id(), req.getNome());
    }
}
