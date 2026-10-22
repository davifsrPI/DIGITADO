import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Copia pelo comando de cópia do próprio navegador, de forma SÍNCRONA - ainda
 * dentro do clique.
 *
 * Funciona em página não segura (o DIGITADO na rede da escola, aberto por
 * http://IP:8080, onde navigator.clipboard nem existe) e no Safari do iPhone. O
 * texto vai para a área de transferência por dois caminhos ao mesmo tempo:
 * - o evento "copy" recebe o texto direto (clipboardData), que é o que Chrome,
 *   Edge e Firefox usam;
 * - um trecho invisível com o texto fica SELECIONADO, que é o que o Safari copia.
 *   A versão anterior selecionava um campo somente leitura, e o Safari do iPhone
 *   não seleciona campo somente leitura: nada era copiado.
 */
const copiarPeloComando = (texto: string): boolean => {
  if (typeof document === 'undefined' || typeof document.execCommand !== 'function') return false;
  const trecho = document.createElement('span');
  trecho.textContent = texto;
  // Invisível, sem rolar a página e selecionável mesmo onde a página bloqueia seleção
  Object.assign(trecho.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    clip: 'rect(0, 0, 0, 0)',
    whiteSpace: 'pre',
    userSelect: 'text',
    webkitUserSelect: 'text',
  });
  const aoCopiar = (e: ClipboardEvent) => {
    if (e.clipboardData) {
      e.clipboardData.setData('text/plain', texto);
      e.preventDefault();
    }
  };
  const selecao = typeof window.getSelection === 'function' ? window.getSelection() : null;
  // Devolve a seleção que a pessoa tinha antes de clicar
  const anteriores: Range[] = [];
  if (selecao) {
    for (let i = 0; i < selecao.rangeCount; i++) anteriores.push(selecao.getRangeAt(i));
  }
  document.body.appendChild(trecho);
  document.addEventListener('copy', aoCopiar, true);
  try {
    if (selecao && typeof document.createRange === 'function') {
      const intervalo = document.createRange();
      intervalo.selectNodeContents(trecho);
      selecao.removeAllRanges();
      selecao.addRange(intervalo);
    }
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    document.removeEventListener('copy', aoCopiar, true);
    if (selecao) {
      selecao.removeAllRanges();
      anteriores.forEach(r => selecao.addRange(r));
    }
    trecho.remove();
  }
};

/**
 * Copia um texto para a área de transferência. Devolve se deu certo.
 *
 * O comando de cópia vai PRIMEIRO, dentro do clique: ele vale em qualquer página
 * (segura ou não) desde que aconteça durante o gesto da pessoa. A API moderna
 * (navigator.clipboard) fica de reserva - ela só existe em página segura e, como é
 * assíncrona, deixá-la na frente empurrava o caminho antigo para depois de uma
 * espera, quando o navegador já não considera mais que houve um clique.
 *
 * O que vai para a área de transferência é o texto puro: aparece no histórico de
 * colagem do sistema (Win+V no Windows, barra do teclado no celular) e cola em
 * qualquer campo, inclusive nas caixas do código no lobby.
 */
export const copiarTexto = async (texto: string): Promise<boolean> => {
  if (copiarPeloComando(texto)) return true;
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(texto);
      return true;
    }
  } catch {
    // Permissão negada: não há mais o que tentar, a tela pede para anotar
  }
  return false;
};

export type EstadoCopia = 'idle' | 'copiado' | 'falhou';

/**
 * Botão de copiar com retorno: "copiado" (ou "falhou") por alguns segundos e depois
 * volta ao normal. Falhar também é dito - o aluno precisa saber que tem de anotar.
 */
export const useCopiar = (duracaoMs = 2000) => {
  const [estado, setEstado] = useState<EstadoCopia>('idle');
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const copiar = useCallback(
    async (texto: string) => {
      const ok = await copiarTexto(texto);
      setEstado(ok ? 'copiado' : 'falhou');
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setEstado('idle'), duracaoMs);
      return ok;
    },
    [duracaoMs],
  );

  return { estado, copiar };
};
