import React, { useEffect, useRef, useState } from 'react';
import { PlacarEntry } from './hooks/useSalaWebSocket';

interface Props {
  placar: PlacarEntry[];
  // Login do usuário atual — destaca a coluna dele no pódio; omitido na visão do professor
  meuLogin?: string;
  // Chamado quando a vinheta termina — o pai então mostra o placar final completo
  onFim: () => void;
}

// Fases da vinheta de suspense exibida quando a partida termina.
// A ordem numérica importa: uma posição é revelada quando fase >= fase de revelação dela.
const FASE_SLAM = 0;
const FASE_SUSPENSE = 1;
const FASE_TERCEIRO = 2;
const FASE_SEGUNDO = 3;
const FASE_RUFAR = 4;
const FASE_PRIMEIRO = 5;

// Colunas do pódio na ordem visual 2º-1º-3º (o campeão fica no centro, mais alto)
const COLUNAS: Array<{ pos: number; classe: string; medalha: string; revelaFase: number }> = [
  { pos: 1, classe: 'vp-col-2', medalha: '🥈', revelaFase: FASE_SEGUNDO },
  { pos: 0, classe: 'vp-col-1', medalha: '🥇', revelaFase: FASE_PRIMEIRO },
  { pos: 2, classe: 'vp-col-3', medalha: '🥉', revelaFase: FASE_TERCEIRO },
];

const CORES_CONFETE = ['#fbbf24', '#6366f1', '#4ade80', '#f87171', '#38bdf8'];

/**
 * Vinheta de suspense do fim da partida: "FIM DE JOGO!" com impacto, suspense,
 * e o pódio revelando 3º → 2º → rufar de tambores → 1º com chuva de confete.
 * Após a revelação do campeão os vencedores comemoram saltando em arco e um
 * botão "Continuar" avança para o placar final completo (via onFim).
 */
export const VinhetaPodio: React.FC<Props> = ({ placar, meuLogin, onFim }) => {
  const top3 = placar.slice(0, 3);
  const [fase, setFase] = useState(FASE_SLAM);
  // Ref para o callback — a linha do tempo é agendada uma única vez na montagem
  const onFimRef = useRef(onFim);
  onFimRef.current = onFim;

  // Confetes gerados uma única vez por montagem, com posição/tempo/cor aleatórios
  const confetes = useRef(
    Array.from({ length: 40 }, (_, i) => ({
      left: Math.random() * 100,
      delay: Math.random() * 0.8,
      dur: 2.2 + Math.random() * 2,
      cor: CORES_CONFETE[i % CORES_CONFETE.length],
    })),
  ).current;

  useEffect(() => {
    // Sem ninguém no placar não há o que revelar — encerra logo após o "fim de jogo"
    if (top3.length === 0) {
      const id = setTimeout(() => onFimRef.current(), 1400);
      return () => clearTimeout(id);
    }
    // Linha do tempo da vinheta — pula a revelação de posições que não existem.
    // Depois do campeão a vinheta fica em festa até o clique em "Continuar".
    const passos: Array<{ t: number; fase: number }> = [];
    let t = 1100;
    passos.push({ t, fase: FASE_SUSPENSE });
    t += 1000;
    if (top3.length >= 3) {
      passos.push({ t, fase: FASE_TERCEIRO });
      t += 1100;
    }
    if (top3.length >= 2) {
      passos.push({ t, fase: FASE_SEGUNDO });
      t += 1100;
    }
    passos.push({ t, fase: FASE_RUFAR });
    t += 1400;
    passos.push({ t, fase: FASE_PRIMEIRO });
    const ids = passos.map(p => setTimeout(() => setFase(p.fase), p.t));
    return () => ids.forEach(clearTimeout);
  }, []);

  return (
    <div className="vp-overlay">
      {fase === FASE_SLAM && <div className="vp-slam">🏁 FIM DE JOGO!</div>}

      {fase === FASE_SUSPENSE && (
        <div className="vp-suspense">
          E os grandes campeões são<span className="vp-reticencias">...</span>
        </div>
      )}

      {fase >= FASE_TERCEIRO && (
        <div className="vp-podio-area">
          <h2 className="vp-titulo">🏆 Pódio da partida</h2>
          <div className={`vp-rufar${fase === FASE_RUFAR ? ' vp-rufar--on' : ''}`}>
            {fase === FASE_RUFAR ? '🥁 E o grande campeão é...' : ' '}
          </div>
          <div className="vp-podio">
            {COLUNAS.map(({ pos, classe, medalha, revelaFase }) => {
              const p = top3[pos];
              if (!p) return null;
              const revelado = fase >= revelaFase;
              // Com o pódio completo os vencedores comemoram saltando em arco
              const saltando = revelado && fase >= FASE_PRIMEIRO;
              return (
                <div key={pos} className={`vp-col ${classe}${revelado ? ' vp-revelado' : ''}`}>
                  <div className={`vp-jogador${saltando ? ' vp-jogador--saltando' : ''}`} style={{ animationDelay: `${pos * 0.2}s` }}>
                    {revelado ? (
                      <>
                        <span className="vp-medalha">{medalha}</span>
                        <span className="vp-avatar">{(p.nome || p.login).charAt(0).toUpperCase()}</span>
                        <span className={`vp-nome${p.login === meuLogin ? ' vp-nome--eu' : ''}`}>
                          {p.nome || p.login}
                          {p.login === meuLogin ? ' (você)' : ''}
                        </span>
                        <span className="vp-pts">{p.pontos} pts</span>
                      </>
                    ) : (
                      <span className="vp-incognita">?</span>
                    )}
                  </div>
                  <div className="vp-pilar">
                    <span className="vp-pilar-num">{pos + 1}º</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {fase >= FASE_PRIMEIRO && (
        <button type="button" className="vp-continuar" onClick={() => onFimRef.current()}>
          Continuar →
        </button>
      )}

      {fase >= FASE_PRIMEIRO && (
        <div className="vp-confetti">
          {confetes.map((c, i) => (
            <span
              key={i}
              className="vp-confete"
              style={{ left: `${c.left}%`, background: c.cor, animationDelay: `${c.delay}s`, animationDuration: `${c.dur}s` }}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default VinhetaPodio;
