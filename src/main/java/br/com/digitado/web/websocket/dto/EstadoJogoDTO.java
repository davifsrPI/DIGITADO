package br.com.digitado.web.websocket.dto;

import java.util.List;

// DTO enviado via WebSocket para todos os participantes da sala a cada evento do jogo.
// Contém o estado completo: tipo do evento, palavra atual, placar, alunos conectados e progresso.
//
// timestampInicio e timestampServidor são os DOIS do relógio do servidor: o cliente
// compara um com o outro para descobrir o quanto o próprio relógio está adiantado ou
// atrasado. Sem esse par, um celular com a hora fora de sincronia acha que a rodada
// já venceu no instante em que ela começa.
public record EstadoJogoDTO(
    String tipo,
    PalavraDTO palavraAtual,
    int indiceAtual,
    int totalPalavras,
    int tempoLimite,
    long timestampInicio,
    long timestampServidor,
    // Quando a rodada fechou (todos responderam) ou fecha (fim do tempo), e quantos
    // segundos de ranking vêm depois dela. Quem vira a palavra é o SERVIDOR: antes o
    // tempo era contado no navegador de quem comandava a sala, e a aba dele fechando
    // deixava a turma parada no ranking para sempre. A tela só desenha a contagem.
    long timestampFechamento,
    int tempoRanking,
    List<PlacarEntry> placar,
    String nomeSala,
    String codigoSala,
    List<EntradaAluno> alunosConectados
) {}
