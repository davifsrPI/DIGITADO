package br.com.digitado.service;

import br.com.digitado.repository.PalavraDoDiaTentativaRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotina de retenção de dados (LGPD arts. 15/16): dados pessoais não podem ser
 * guardados além da finalidade. Roda todo dia de madrugada.
 *
 * Hoje cobre as tentativas da palavra do dia: o login registrado só serve para
 * impedir a segunda tentativa NO PRÓPRIO DIA — depois de DIAS_RETENCAO ele é
 * anonimizado (vira nulo), preservando apenas a estatística agregada.
 */
@Service
public class LgpdRetencaoService {

    private static final Logger LOG = LoggerFactory.getLogger(LgpdRetencaoService.class);

    // Janela de retenção do vínculo login ↔ tentativa (ajuste conforme a política)
    private static final int DIAS_RETENCAO = 30;

    private final PalavraDoDiaTentativaRepository tentativaRepository;

    public LgpdRetencaoService(PalavraDoDiaTentativaRepository tentativaRepository) {
        this.tentativaRepository = tentativaRepository;
    }

    // Todo dia às 03:30 (horário do servidor)
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void anonimizarTentativasAntigas() {
        LocalDate corte = LocalDate.now(PalavraDoDiaService.FUSO).minusDays(DIAS_RETENCAO);
        int anonimizadas = tentativaRepository.anonimizarAnterioresA(corte);
        if (anonimizadas > 0) {
            LOG.info("LGPD: {} tentativa(s) da palavra do dia anonimizada(s) (anteriores a {})", anonimizadas, corte);
        }
    }
}
