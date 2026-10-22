package br.com.digitado.web.rest.vm;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.service.ConquistaEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A porcentagem da tela de conquistas. Antes a tela mostrava o progresso cru como
 * se fosse porcentagem: 3 de 10 vitórias virava "3%".
 */
class ConquistaUsuarioVMTest {

    @Test
    void progressoSobreAMeta() {
        assertThat(ConquistaUsuarioVM.percentual(false, 3, 10)).isEqualTo(30);
        assertThat(ConquistaUsuarioVM.percentual(false, 150, 1000)).isEqualTo(15);
        assertThat(ConquistaUsuarioVM.percentual(false, 0, 5)).isZero();
        assertThat(ConquistaUsuarioVM.percentual(false, null, 5)).isZero();
    }

    @Test
    @DisplayName("bloqueada nunca mostra 100%; desbloqueada sempre mostra")
    void cemPorCentoSoDesbloqueada() {
        assertThat(ConquistaUsuarioVM.percentual(false, 999, 1000)).isEqualTo(99);
        assertThat(ConquistaUsuarioVM.percentual(false, 5000, 1000)).isEqualTo(99);
        assertThat(ConquistaUsuarioVM.percentual(true, 1, 1)).isEqualTo(100);
    }

    @Test
    void semMetaConhecidaNaoInventaPorcentagem() {
        assertThat(ConquistaUsuarioVM.percentual(false, 3, null)).isNull();
    }

    @Test
    void metasVemDoMotorDeConquistas() {
        assertThat(ConquistaEngineService.metaDe("Aquecendo os Dedos")).isEqualTo(10);
        assertThat(ConquistaEngineService.metaDe("Vença de um Desenvolvedor")).isEqualTo(1);
        // as quatro que não tinham regra e ficavam em 0% para sempre
        assertThat(ConquistaEngineService.metaDe("Aprendendo com os Erros")).isEqualTo(1);
        assertThat(ConquistaEngineService.metaDe("Superação")).isEqualTo(10);
        assertThat(ConquistaEngineService.metaDe("Explorador de Salas")).isEqualTo(5);
        assertThat(ConquistaEngineService.metaDe("Aluno Dedicado")).isEqualTo(10);
        assertThat(ConquistaEngineService.metaDe("Inexistente")).isNull();
    }
}
