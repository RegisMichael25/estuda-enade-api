package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.AuthApi;
import com.estudaenade.api.api.contrato.dto.CadastrarRequest;
import com.estudaenade.api.api.contrato.dto.EntrarRequest;
import com.estudaenade.api.api.contrato.dto.GoogleCadastrarRequest;
import com.estudaenade.api.api.contrato.dto.GoogleEntrarRequest;
import com.estudaenade.api.api.contrato.dto.NomeRequest;
import com.estudaenade.api.api.contrato.dto.RedefinirSenhaRequest;
import com.estudaenade.api.api.contrato.dto.RefreshRequest;
import com.estudaenade.api.api.contrato.dto.SolicitarCodigoSenhaRequest;
import com.estudaenade.api.api.contrato.dto.TrocarSenhaRequest;
import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.api.dto.AuthDtos.UsuarioResponse;
import com.estudaenade.api.domain.service.AuthService;
import com.estudaenade.api.domain.service.CadastroService;
import com.estudaenade.api.domain.service.LoginGoogleService;
import com.estudaenade.api.domain.service.RecuperacaoSenhaService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro e login (com e-mail e senha ou com Google), "esqueci a senha" (código por e-mail), sessão e dados
 * da conta. Rotas em {@link AuthApi}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CadastroService cadastroService;
    private final LoginGoogleService loginGoogleService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;

    @Override
    public SessaoResponse cadastrar(CadastrarRequest req) {
        return cadastroService.cadastrar(req);
    }

    @Override
    public SessaoResponse entrar(EntrarRequest req) {
        return authService.entrar(req.getEmail(), req.getSenha());
    }

    @Override
    public SessaoResponse entrarComGoogle(GoogleEntrarRequest req) {
        return loginGoogleService.entrar(req.getIdToken());
    }

    @Override
    public SessaoResponse cadastrarComGoogle(GoogleCadastrarRequest req) {
        return cadastroService.cadastrarComGoogle(req);
    }

    @Override
    public void solicitarCodigoSenha(SolicitarCodigoSenhaRequest req) {
        recuperacaoSenhaService.solicitarCodigo(req.getEmail());
    }

    @Override
    public void redefinirSenha(RedefinirSenhaRequest req) {
        recuperacaoSenhaService.redefinir(req.getEmail(), req.getCodigo(), req.getNovaSenha());
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

    @Override
    public void solicitarCodigoTrocaSenha() {
        authService.solicitarCodigoTrocaSenha(UsuarioLogado.atual().id());
    }

    @Override
    public SessaoResponse trocarSenha(TrocarSenhaRequest req) {
        return authService.trocarSenha(UsuarioLogado.atual().id(), req.getCodigo(), req.getNovaSenha());
    }
}
