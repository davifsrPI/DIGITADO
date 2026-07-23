/**
 * Paleta e rótulos OFICIAIS das dificuldades - fonte única para todas as telas
 * (criação de sala, painel do professor, lobby do jogo...).
 *
 * Antes, o trio verde/âmbar/vermelho e os rótulos "Fácil/Médio/Difícil" estavam
 * copiados em vários componentes; mudar uma cor exigia caçar todas as cópias.
 */
export type DificuldadeKey = 'FACIL' | 'MEDIO' | 'DIFICIL';

export const CORES_DIFICULDADE: Record<DificuldadeKey, string> = {
  FACIL: '#4ade80',
  MEDIO: '#fbbf24',
  DIFICIL: '#f87171',
};

export const LABELS_DIFICULDADE: Record<DificuldadeKey, string> = {
  FACIL: 'Fácil',
  MEDIO: 'Médio',
  DIFICIL: 'Difícil',
};
