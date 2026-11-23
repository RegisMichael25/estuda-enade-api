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

    /** Conta criada com senha: vincula pelo e-mail e mantém a senha e as sessões. */
    private void vincular(Usuario usuario, DadosGoogle google) {
        log.info("Conta {} vinculada ao Google", usuario.getId());
        usuario.setGoogleSub(google.sub());
        usuario.setEmailVerificado(true);
    }
}
