package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.SalaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * A sala pode ser preparada dias antes da aula ("criar e deixar pronta"), e a
 * configuracao guarda so os ids das palavras. Estes testes fixam o contrato da
 * lista que a tela de espera do professor usa para conferir a atividade.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfiguracaoPartidaServiceTest {

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private PalavraRepository palavraRepository;

    private ConfiguracaoPartidaService service;

    private ConfiguracaoPartidaService comConfiguracao(String json) {
        Sala sala = new Sala();
        sala.setCodigo("H4XEZ7");
        sala.setConfiguracao(json);
        when(salaRepository.findById("H4XEZ7")).thenReturn(Optional.of(sala));
        service = new ConfiguracaoPartidaService(salaRepository, palavraRepository, new ObjectMapper());
        return service;
    }

    private static Palavra palavra(long id, String texto, Dificuldade dificuldade) {
        Palavra p = new Palavra();
        p.setId(id);
        p.setTexto(texto);
        p.setDificuldadeCadastrada(dificuldade);
        return p;
    }

    @Test
    @DisplayName("devolve as sorteadas e as extras na ordem em que foram gravadas")
    void palavrasNaOrdemGravada() {
        ConfiguracaoPartidaService s = comConfiguracao(
            "{\"tempoFacil\":20,\"tempoMedio\":30,\"tempoDificil\":45,\"qtdFacil\":2,\"qtdMedio\":0,\"qtdDificil\":0," +
            "\"palavrasExtrasIds\":[30],\"palavrasIds\":[10,20]}"
        );
        // O banco devolve em qualquer ordem - quem manda e a ordem dos ids
        when(palavraRepository.findAllById(any())).thenAnswer(inv -> {
            List<Long> ids = (List<Long>) inv.getArgument(0);
            return List.of(
                palavra(30L, "abacaxi", Dificuldade.MEDIO),
                palavra(20L, "cachorro", Dificuldade.DIFICIL),
                palavra(10L, "casa", Dificuldade.FACIL)
            )
                .stream()
                .filter(p -> ids.contains(p.getId()))
                .toList();
        });

        ConfiguracaoPartidaService.PalavrasDaSala palavras = s.palavras("H4XEZ7");

        assertThat(palavras.sorteadas()).extracting(ConfiguracaoPartidaService.PalavraDaSala::texto).containsExactly("casa", "cachorro");
        assertThat(palavras.sorteadas())
            .extracting(ConfiguracaoPartidaService.PalavraDaSala::dificuldade)
            .containsExactly("FACIL", "DIFICIL");
        assertThat(palavras.extras()).extracting(ConfiguracaoPartidaService.PalavraDaSala::texto).containsExactly("abacaxi");
    }

    @Test
    @DisplayName("palavra apagada do acervo sai da lista, o resto continua")
    void ignoraPalavraQueNaoExisteMais() {
        ConfiguracaoPartidaService s = comConfiguracao(
            "{\"tempoFacil\":20,\"tempoMedio\":30,\"tempoDificil\":45,\"qtdFacil\":2,\"qtdMedio\":0,\"qtdDificil\":0," +
            "\"palavrasExtrasIds\":[],\"palavrasIds\":[10,99]}"
        );
        when(palavraRepository.findAllById(any())).thenReturn(List.of(palavra(10L, "casa", Dificuldade.FACIL)));

        assertThat(s.palavras("H4XEZ7").sorteadas()).extracting(ConfiguracaoPartidaService.PalavraDaSala::texto).containsExactly("casa");
    }

    @Test
    @DisplayName("sala sem configuracao gravada devolve listas vazias, sem ir ao banco de palavras")
    void salaSemConfiguracao() {
        ConfiguracaoPartidaService s = comConfiguracao(null);

        ConfiguracaoPartidaService.PalavrasDaSala palavras = s.palavras("H4XEZ7");

        assertThat(palavras.sorteadas()).isEmpty();
        assertThat(palavras.extras()).isEmpty();
    }
}
