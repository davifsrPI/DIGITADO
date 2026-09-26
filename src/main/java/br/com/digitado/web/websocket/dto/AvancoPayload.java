package br.com.digitado.web.websocket.dto;

/**
 * Pedido de passar para a próxima palavra, feito por quem comanda a sala.
 *
 * indiceAtual é a rodada que a tela dele estava mostrando. O servidor só vira a
 * rodada se ela ainda for a aberta: assim um clique que chega junto com a virada
 * automática do relógio (ver JogoSalaRodadaScheduler) não pula uma palavra nem conta
 * as estatísticas da rodada duas vezes. Nulo significa "vire a que estiver aberta".
 */
public record AvancoPayload(Integer indiceAtual) {}
