import React, { MutableRefObject, useEffect, useRef } from 'react';
import { PlacarEntry } from './hooks/useSalaWebSocket';

interface Props {
  placar: PlacarEntry[];
  // Login do usuário atual — destaca a caixa dele; omitido na visão do professor
  meuLogin?: string;
  // Posições da rodada anterior (login -> índice), mantidas pelo componente pai
  // para que a animação de ultrapassagem funcione mesmo entre rodadas.
  posRef: MutableRefObject<Map<string, number>>;
}

// Altura de cada caixa + espaçamento — deve casar com o CSS (.rn-nuvem)
const ROW_H = 74;

/**
 * Top 5 do placar em caixas "nuvem" com animação de ULTRAPASSAGEM (FLIP):
 * quando um jogador passa outro entre rodadas, a caixa desliza da posição
 * anterior para a nova, dando o efeito de um passando o outro.
 */
export const RankingNuvem: React.FC<Props> = ({ placar, meuLogin, posRef }) => {
  const top5 = placar.slice(0, 5);
  const boxRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  const chaveOrdem = top5.map(p => `${p.login}:${p.pontos}`).join('|');

  useEffect(() => {
    // FLIP: para cada caixa, se ela estava em outra posição na rodada anterior,
    // começa deslocada (da posição antiga) e anima até a nova (translateY 0).
    top5.forEach((p, i) => {
      const el = boxRefs.current.get(p.login);
      if (!el) return;
      const anterior = posRef.current.get(p.login);
      if (anterior !== undefined && anterior !== i) {
        const delta = (anterior - i) * ROW_H;
        el.style.transition = 'none';
        el.style.transform = `translateY(${delta}px)`;
        // força reflow e depois anima de volta ao lugar
        void el.offsetHeight;
        requestAnimationFrame(() => {
          el.style.transition = 'transform 0.7s cubic-bezier(0.22, 1, 0.36, 1)';
          el.style.transform = 'translateY(0)';
        });
      }
    });
    // Atualiza o mapa de posições para a próxima rodada
    const novo = new Map<string, number>();
    top5.forEach((p, i) => novo.set(p.login, i));
    posRef.current = novo;
  }, [chaveOrdem]);

  return (
    <div className="rn-lista" style={{ height: top5.length * ROW_H }}>
      {top5.map((p, i) => (
        <div
          key={p.login}
          ref={el => {
            if (el) boxRefs.current.set(p.login, el);
          }}
          className={`rn-nuvem rn-medalha-${i}${p.login === meuLogin ? ' rn-eu' : ''}`}
          style={{ top: i * ROW_H }}
        >
          <span className="rn-rank">{i + 1}º</span>
          <span className="rn-avatar">{(p.nome || p.login).charAt(0).toUpperCase()}</span>
          <span className="rn-nome">
            {p.nome || p.login}
            {p.login === meuLogin ? ' (você)' : ''}
          </span>
          <span className="rn-pts">{p.pontos} pts</span>
        </div>
      ))}
    </div>
  );
};

export default RankingNuvem;
