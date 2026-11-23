package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle.DadosGoogle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.estudaenade.api.domain.service.SessaoService.normalizarEmail;

/** Login com a conta Google, inclusive em conta criada com e-mail e senha (vínculo automático pelo e-mail). */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginGoogleService {

    private final UsuarioRepository usuarioRepository;
    private final VerificadorTokenGoogle verificadorGoogle;
    private final SessaoService sessaoService;

    @Transactional
    public SessaoResponse entrar(String idToken) {
        DadosGoogle google = verificadorGoogle.verificar(idToken);
        Usuario usuario = usuarioRepository.findByGoogleSub(google.sub()).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findByEmail(normalizarEmail(google.email()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTA_NAO_ENCONTRADA",
                            "Não há conta para este Google. Conclua o cadastro."));
            vincular(usuario, google);
        }
        return sessaoService.abrir(usuario);
    }

    /**
     * O Google comprova o dono do e-mail: a conta passa a ter o e-mail comprovado. A senha e as sessões existentes
     * são mantidas, inclusive quando o e-mail ainda não estava comprovado (decisão de produto: quem se cadastrou
     * com senha e depois entra com Google continua podendo usar a senha).
     */
    private void vincular(Usuario usuario, DadosGoogle google) {
        log.info("Conta {} vinculada ao Google{}", usuario.getId(),
                usuario.isEmailVerificado() ? "" : " (e-mail comprovado agora)");
        usuario.setGoogleSub(google.sub());
        usuario.setEmailVerificado(true);
    }
}
