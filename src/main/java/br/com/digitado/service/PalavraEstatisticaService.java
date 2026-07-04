package br.com.digitado.service;

import br.com.digitado.repository.PalavraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contabiliza tentativas e acertos nas colunas de estatística da tabela palavra
 * (total_tentativas e total_acertos).
 *
 * Chamado apenas pelo backend nos pontos onde a resposta é validada:
 * - JogoSalaService.responder (partidas via WebSocket)
 * - PalavraDoDiaService.tentar (palavra do dia)
 *
 * O frontend nunca envia contadores — só a resposta digitada.
 */
@Service
public class PalavraEstatisticaService {

    private static final Logger LOG = LoggerFactory.getLogger(PalavraEstatisticaService.class);

    private final PalavraRepository palavraRepository;

    public PalavraEstatisticaService(PalavraRepository palavraRepository) {
        this.palavraRepository = palavraRepository;
    }

    // Incrementa os contadores da palavra (update atômico). Falha aqui não pode
    // derrubar a partida — estatística é acessória, então o erro é só logado.
    @Transactional
    public void registrarTentativa(Long palavraId, boolean acertou) {
        if (palavraId == null) {
            return;
        }
        try {
            palavraRepository.incrementarEstatistica(palavraId, acertou ? 1 : 0);
        } catch (Exception e) {
            LOG.error("Falha ao registrar estatística da palavra {}: {}", palavraId, e.getMessage(), e);
        }
    }
}
