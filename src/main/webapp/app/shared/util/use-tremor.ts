import { useCallback, useEffect, useRef, useState } from 'react';

/** Duração da animação .tremer (ver app.scss) */
export const DURACAO_TREMOR_MS = 500;

/**
 * Faz um elemento tremer ("não!") - o código de sala errado sacode as caixas, que é
 * onde o aluno está olhando, em vez de só aparecer um texto embaixo.
 *
 * Cada chamada de tremer() reinicia a animação, mesmo com a anterior ainda rodando:
 * errar duas vezes seguidas treme duas vezes.
 */
export const useTremor = () => {
  const [tremendo, setTremendo] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const tremer = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setTremendo(false);
    // Próximo quadro: tirar e recolocar a classe no mesmo render não reinicia a animação
    requestAnimationFrame(() => {
      setTremendo(true);
      timer.current = setTimeout(() => setTremendo(false), DURACAO_TREMOR_MS);
    });
    // Celular que vibra: o aviso chega também na mão
    try {
      navigator.vibrate?.(120);
    } catch {
      // sem vibração, segue só a animação
    }
  }, []);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  return { tremendo, tremer };
};
