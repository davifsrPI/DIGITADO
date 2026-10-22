package br.com.digitado.service;

import br.com.digitado.domain.Usuario;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concessão de XP ao usuário - alimenta o Ranking Mundial.
 *
 * O XP fica numa coluna persistente (usuario.xp) em vez de ser calculado das
 * tentativas, porque os logins de palavra_do_dia_tentativa são anonimizados
 * após o prazo de retenção (LGPD) - o XP precisa sobreviver a isso.
 *
 * Chamado apenas pelo backend, nos pontos onde o acerto já foi validado.
 */
@Service
public class XpService {

    // Recompensa por acertar a palavra do dia
    public static final long XP_ACERTO_PALAVRA_DIA = 300;

    // ===== XP de partida, por modo de jogo =====
    //
    // SALA (turma), creditado no fim da partida a quem respondeu ao menos uma palavra:
    //   xp = round((10 x acertos + 2 x palavrasJogadas) x multiplicador) + 5 x superados
    //   multiplicador = 1 + 0,05 x (jogadores - 1), no máximo 2,0
    // A sala cheia vale mais (cada colega a mais é concorrência a mais), a partida
    // longa vale mais que a curta, e ficar à frente dos outros rende um extra.
    //
    // DUELO 1v1, para os dois jogadores:
    //   xp = 10 x acertos + 2 x palavrasJogadas + 50 (vitória) | 20 (empate) | 0 (derrota)
    //
    // INDIVIDUAL, creditado a cada acerto, na hora:
    //   xp = 5 + round(10 x dificuldade da palavra para o jogador), de 5 a 15
    //   e +20 a cada 10 acertos na mesma partida
    // Sem plateia nem adversário, o acerto vale menos que numa sala - e a palavra
    // que é difícil PARA AQUELE JOGADOR vale mais que a que ele já domina.

    public static final int XP_POR_ACERTO = 10;
    public static final int XP_POR_PALAVRA_JOGADA = 2;
    public static final int XP_POR_JOGADOR_SUPERADO = 5;
    public static final double BONUS_POR_JOGADOR_NA_SALA = 0.05;
    public static final double MULTIPLICADOR_MAXIMO_SALA = 2.0;
    public static final int XP_VITORIA_DUELO = 50;
    public static final int XP_EMPATE_DUELO = 20;
    public static final int XP_ACERTO_INDIVIDUAL_BASE = 5;
    public static final int XP_ACERTO_INDIVIDUAL_POR_DIFICULDADE = 10;
    public static final int XP_MARCO_INDIVIDUAL = 20;
    public static final int ACERTOS_POR_MARCO_INDIVIDUAL = 10;

    /** Como o duelo terminou para um dos dois jogadores. */
    public enum ResultadoDuelo {
        VITORIA,
        EMPATE,
        DERROTA,
    }

    /** O multiplicador da sala: 1,0 sozinho, +0,05 por colega, até 2,0. */
    public static double multiplicadorSala(int jogadores) {
        double mult = 1.0 + BONUS_POR_JOGADOR_NA_SALA * Math.max(0, jogadores - 1);
        return Math.min(MULTIPLICADOR_MAXIMO_SALA, mult);
    }

    /** XP de um jogador no fim de uma partida de sala (turma). */
    public static long xpPartidaSala(int acertos, int palavrasJogadas, int jogadores, int superados) {
        double base = (double) XP_POR_ACERTO * Math.max(0, acertos) + (double) XP_POR_PALAVRA_JOGADA * Math.max(0, palavrasJogadas);
        return Math.round(base * multiplicadorSala(jogadores)) + (long) XP_POR_JOGADOR_SUPERADO * Math.max(0, superados);
    }

    /** XP de um jogador no fim de um duelo 1v1. */
    public static long xpDuelo(int acertos, int palavrasJogadas, ResultadoDuelo resultado) {
        long bonus =
            switch (resultado) {
                case VITORIA -> XP_VITORIA_DUELO;
                case EMPATE -> XP_EMPATE_DUELO;
                case DERROTA -> 0;
            };
        return (long) XP_POR_ACERTO * Math.max(0, acertos) + (long) XP_POR_PALAVRA_JOGADA * Math.max(0, palavrasJogadas) + bonus;
    }

    /**
     * XP de um acerto no modo individual. dificuldade é a da palavra PARA O
     * JOGADOR (0 = já domina, 1 = nunca acerta), a mesma que a equação de escolha
     * usa; acertosNaPartida já conta este acerto e decide o bônus de marco.
     */
    public static long xpAcertoIndividual(double dificuldade, int acertosNaPartida) {
        double d = Math.max(0, Math.min(1, dificuldade));
        long xp = XP_ACERTO_INDIVIDUAL_BASE + Math.round(XP_ACERTO_INDIVIDUAL_POR_DIFICULDADE * d);
        if (acertosNaPartida > 0 && acertosNaPartida % ACERTOS_POR_MARCO_INDIVIDUAL == 0) {
            xp += XP_MARCO_INDIVIDUAL;
        }
        return xp;
    }

    private static final Logger LOG = LoggerFactory.getLogger(XpService.class);

    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public XpService(UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Credita o XP da palavra do dia ao usuário do login informado.
     * Retorna o XP concedido (0 se o login não tiver Usuario correspondente -
     * ex.: conta de admin sem cadastro de aluno). Falha aqui não pode derrubar
     * o fluxo da tentativa - XP é acessório, então o erro é só logado.
     */
    @Transactional
    public long premiarAcertoPalavraDoDia(String login) {
        try {
            // Mesmo caminho de resolução usado nas conquistas: login -> User -> Usuario (pelo e-mail)
            Optional<Usuario> usuario = userRepository.findOneByLogin(login).flatMap(u -> usuarioRepository.findByEmail(u.getEmail()));
            if (usuario.isEmpty()) {
                LOG.warn("XP não creditado: login {} não tem Usuario correspondente", login);
                return 0;
            }
            creditar(usuario.orElseThrow().getId(), XP_ACERTO_PALAVRA_DIA, "acerto na palavra do dia");
            return XP_ACERTO_PALAVRA_DIA;
        } catch (Exception e) {
            LOG.error("Falha ao creditar XP da palavra do dia para {}: {}", login, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Credita XP pelo LOGIN - é a identidade que o jogo tem em mãos no fim de uma
     * partida. Devolve o XP creditado (0 quando o login não tem Usuario, como a
     * conta admin sem cadastro de aluno). Como no XP da palavra do dia, a falha só
     * é logada: XP é acessório e não pode derrubar o fim de uma partida.
     */
    @Transactional
    public long creditarPorLogin(String login, long xp, String motivo) {
        if (login == null || xp <= 0) {
            return 0;
        }
        try {
            Optional<Usuario> usuario = userRepository.findOneByLogin(login).flatMap(u -> usuarioRepository.findByEmail(u.getEmail()));
            if (usuario.isEmpty()) {
                LOG.warn("XP não creditado: login {} não tem Usuario correspondente", login);
                return 0;
            }
            creditar(usuario.orElseThrow().getId(), xp, motivo);
            return xp;
        } catch (Exception e) {
            LOG.error("Falha ao creditar XP ({}) para {}: {}", motivo, login, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Crédito genérico de XP (update atômico) - usado pela palavra do dia e pelo
     * motor de conquistas ao desbloquear uma conquista.
     */
    @Transactional
    public void creditar(Long usuarioId, long xp, String motivo) {
        if (usuarioId == null || xp <= 0) {
            return;
        }
        usuarioRepository.incrementarXp(usuarioId, xp);
        LOG.info("XP: +{} para usuario {} ({})", xp, usuarioId, motivo);
    }
}
