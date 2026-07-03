package br.com.digitado.web.rest.vm;

import java.util.List;

// ViewModel da tela "Minhas Conquistas": resumo (total, desbloqueadas, XP ganho)
// mais a lista completa de conquistas com o estado do usuário logado
public record MinhasConquistasVM(long total, long desbloqueadas, long xpGanho, List<ConquistaUsuarioVM> conquistas) {}
