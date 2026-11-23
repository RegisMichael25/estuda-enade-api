package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.CadastrarRequest;
import com.estudaenade.api.api.contrato.dto.GoogleCadastrarRequest;
import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle;
import com.estudaenade.api.infra.google.VerificadorTokenGoogle.DadosGoogle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.estudaenade.api.domain.service.SessaoService.normalizarEmail;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final SenhaService senhaService;
    private final CadastroProfessorService cadastroProfessorService;
    private final VerificadorTokenGoogle verificadorGoogle;
    private final SessaoService sessaoService;

    @Transactional
    public SessaoResponse cadastrar(CadastrarRequest req) {
        String email = normalizarEmail(req.getEmail());
        senhaService.validarForca(req.getSenha());
        if (usuarioRepository.existsByEmail(email)) {
            throw emailEmUso();
        }
        Usuario novo = Usuario.builder()
                .nome(req.getNome().trim()).email(email).papel(req.getPapel())
                .emailVerificado(false)
                .build();
        senhaService.definir(novo, req.getSenha());
        return sessaoService.abrir(criar(novo, req.getCodigoConvite()));
    }

    /** O e-mail vem do token; o nome vem do corpo (o usuário pode editar o do Google). */
    @Transactional
    public SessaoResponse cadastrarComGoogle(GoogleCadastrarRequest req) {
        DadosGoogle google = verificadorGoogle.verificar(req.getIdToken());
        String email = normalizarEmail(google.email());
        senhaService.validarForca(req.getSenha());
        if (usuarioRepository.existsByEmail(email) || usuarioRepository.findByGoogleSub(google.sub()).isPresent()) {
            throw emailEmUso();
        }
        Usuario novo = Usuario.builder()
                .nome(req.getNome().trim()).email(email).papel(req.getPapel())
                .googleSub(google.sub())
                .emailVerificado(true)
                .build();
        senhaService.definir(novo, req.getSenha());
        return sessaoService.abrir(criar(novo, req.getCodigoConvite()));
    }

    /** Para aluno, o convite é ignorado. */
    private Usuario criar(Usuario novo, String codigoConvite) {
        if (novo.getPapel() == Papel.PROFESSOR) {
            return cadastroProfessorService.cadastrar(novo, codigoConvite);
        }
        novo.setAprovado(true);
        return usuarioRepository.save(novo);
    }

    private static ApiException emailEmUso() {
        return ApiException.conflito("EMAIL_EM_USO", "Já existe uma conta com este e-mail. Entre ou redefina a senha.");
    }
}
