package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.com.digitado.repository.SalaRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * O formato do codigo de sala agora existe em UM lugar so: este servico. O
 * sorteio saiu da tela de criacao (o cliente escolhia a chave primaria da sala),
 * e estes testes fixam o contrato que o front passou a consumir.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodigoSalaServiceTest {

    @Mock
    private SalaRepository salaRepository;

    private CodigoSalaService service;

    private CodigoSalaService comBancoVazio() {
        when(salaRepository.existsById(anyString())).thenReturn(false);
        service = new CodigoSalaService(salaRepository);
        return service;
    }

    @Test
    @DisplayName("o codigo tem 6 caracteres do alfabeto sem O, I, 1 e 0")
    void formatoDoCodigo() {
        CodigoSalaService gerador = comBancoVazio();

        // Amostra grande: o alfabeto errado so apareceria de vez em quando
        for (int i = 0; i < 500; i++) {
            String codigo = gerador.gerarDisponivel();
            assertThat(codigo).hasSize(CodigoSalaService.TAMANHO);
            assertThat(codigo).matches("[A-HJ-NP-Z2-9]{6}");
            // Os quatro que o aluno confunde ao copiar do quadro
            assertThat(codigo).doesNotContain("O").doesNotContain("I").doesNotContain("1").doesNotContain("0");
        }
    }

    @Test
    @DisplayName("o alfabeto publicado e o mesmo que o sorteio usa")
    void alfabetoPublicado() {
        assertThat(CodigoSalaService.ALFABETO).isEqualTo("ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
        assertThat(CodigoSalaService.ALFABETO).doesNotContain("O", "I", "1", "0");
        assertThat(CodigoSalaService.TAMANHO).isEqualTo(6);
    }

    @Test
    @DisplayName("nao devolve codigo que ja existe no banco")
    void pulaCodigoOcupado() {
        // Os dois primeiros sorteios caem em salas existentes; o terceiro esta livre
        Set<String> ocupados = new HashSet<>();
        CodigoSalaService gerador = new CodigoSalaService(salaRepository);
        String primeiro = gerador.sortear();
        String segundo = gerador.sortear();
        ocupados.add(primeiro);
        ocupados.add(segundo);
        when(salaRepository.existsById(anyString())).thenAnswer(inv -> ocupados.contains(inv.getArgument(0, String.class)));

        String codigo = gerador.gerarDisponivel();

        assertThat(codigo).isNotIn(ocupados);
    }

    @Test
    @DisplayName("falha alto quando nenhum codigo esta livre, em vez de girar para sempre")
    void semCodigoDisponivel() {
        when(salaRepository.existsById(anyString())).thenReturn(true);
        CodigoSalaService gerador = new CodigoSalaService(salaRepository);

        assertThatThrownBy(gerador::gerarDisponivel).isInstanceOf(CodigoSalaService.SemCodigoDisponivelException.class);
    }

    @Test
    @DisplayName("sorteia codigos diferentes entre chamadas")
    void codigosVariam() {
        CodigoSalaService gerador = comBancoVazio();

        Set<String> distintos = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> distintos.add(gerador.gerarDisponivel()));

        // 32^6 combinacoes: 200 sorteios repetidos seriam sinal de gerador quebrado
        assertThat(distintos).hasSizeGreaterThan(190);
    }
}
