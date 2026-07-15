import React, { useEffect, useState } from 'react';
import axios from 'axios';

import { useAppSelector } from 'app/config/store';
import { EntradaPalavra } from 'app/shared/components/entrada-palavra/entrada-palavra';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';
import { falarPalavra } from 'app/modules/sala/utils/falar-palavra';

// Estado do desafio vindo do backend. O desafio é um DITADO: textoAudio serve
// só para a síntese de voz do navegador e nunca é exibido na tela — a validação
// da resposta e o controle de chance única continuam no servidor
interface PalavraDoDia {
  disponivel: boolean;
  data: string;
  tamanho: number;
  textoAudio?: string;
  dificuldade?: string;
  categoria?: string;
  // Dica cadastrada no banco (coluna dica da tabela palavra) — pista para o jogador
  dica?: string;
  jaTentou: boolean;
  resultado?: Resultado;
}

interface Resultado {
  acertou: boolean;
  palavraCorreta: string;
  totalTentativas: number;
  totalAcertos: number;
  // XP creditado pelo acerto (0 para erro ou visitante anônimo)
  xpGanho: number;
}

// Card "Palavra do Dia" da tela inicial — um ditado com chance única.
// A validação da resposta mora no backend; o front recebe o texto apenas para a
// síntese de voz e nunca o exibe. O controle de "uma chance" é do servidor —
// cookie httpOnly para visitantes e registro no banco para contas logadas.
export const PalavraDoDia = () => {
  const account = useAppSelector(state => state.authentication.account);
  const [desafio, setDesafio] = useState<PalavraDoDia | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [palpite, setPalpite] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [falando, setFalando] = useState(false);

  // Busca o estado no backend; refaz quando o usuário loga/desloga, porque
  // entrar na conta dá direito à chance da conta (o cookie anônimo é ignorado)
  useEffect(() => {
    setResultado(null);
    setErro(null);
    axios
      .get<PalavraDoDia>('/api/public/palavra-do-dia')
      .then(res => {
        setDesafio(res.data);
        if (res.data.resultado) setResultado(res.data.resultado);
      })
      .catch(() => setDesafio(null));
  }, [account?.login]);

  const enviar = (e: React.FormEvent) => {
    e.preventDefault();
    if (!palpite.trim() || enviando) return;
    setEnviando(true);
    setErro(null);
    axios
      .post<Resultado>('/api/public/palavra-do-dia/tentar', { resposta: palpite })
      .then(res => {
        setResultado(res.data);
        setDesafio(prev => (prev ? { ...prev, jaTentou: true } : prev));
      })
      .catch(err => {
        // "jatentou" vem do backend quando a chance já foi usada (ex.: em outra aba)
        const key = `${err?.response?.data?.message ?? ''} ${err?.response?.headers?.['x-digitado-error'] ?? ''}`;
        setErro(key.includes('jatentou') ? 'Você já usou sua chance de hoje.' : 'Não foi possível enviar. Tente novamente.');
      })
      .finally(() => setEnviando(false));
  };

  // Sem palavra cadastrada/back fora do ar: não mostra o card
  if (!desafio || !desafio.disponivel) return null;

  const percentual =
    resultado && resultado.totalTentativas > 0 ? Math.round((resultado.totalAcertos / resultado.totalTentativas) * 100) : 0;

  return (
    <section className="pdd-section">
      <div className="section-label">Desafio diário</div>
      <h2 className="section-title">Palavra do Dia</h2>
      <p className="pdd-sub">Ouça a palavra e escreva a grafia correta. Todo mundo pode jogar — mas só uma chance por dia!</p>

      <div className="pdd-card">
        <div className="pdd-meta">
          {desafio.dificuldade && <span className={`pdd-chip pdd-chip--${desafio.dificuldade.toLowerCase()}`}>{desafio.dificuldade}</span>}
          {desafio.categoria && <span className="pdd-chip">{desafio.categoria}</span>}
          <span className="pdd-chip">{desafio.tamanho} letras</span>
        </div>

        {/* Ditado: o jogador ouve a palavra via síntese de voz — nada dela aparece na tela */}
        {!desafio.jaTentou && !resultado && (
          <div className="pdd-audio">
            <button
              type="button"
              className={`pdd-audio-btn${falando ? ' pdd-audio-btn--playing' : ''}`}
              onClick={() => {
                if (!desafio.textoAudio) return;
                setFalando(true);
                falarPalavra(desafio.textoAudio, { onEnd: () => setFalando(false) });
              }}
              aria-label="Ouvir palavra"
            >
              {/* Alto-falante compartilhado (IconeAudio): mesmo SVG dos botões das
                  telas de jogo — as ondas pulsam sozinhas enquanto o áudio toca */}
              <IconeAudio tocando={falando} className="pdd-audio-svg" />
            </button>
            <span className="pdd-audio-hint">{falando ? 'Reproduzindo...' : 'Clique para ouvir · pode ouvir quantas vezes quiser'}</span>
          </div>
        )}

        {/* Dica vinda do banco de dados — só aparece se a palavra tiver uma cadastrada */}
        {desafio.dica && (
          <div className="pdd-dica">
            <span className="pdd-dica-icone">💡</span>
            <span>
              <strong>Dica:</strong> {desafio.dica}
            </span>
          </div>
        )}

        {resultado ? (
          <div className={`pdd-resultado${resultado.acertou ? ' pdd-resultado--acerto' : ' pdd-resultado--erro'}`}>
            <div className="pdd-resultado-icone">{resultado.acertou ? '🎉' : '😅'}</div>
            <div className="pdd-resultado-titulo">{resultado.acertou ? 'Você acertou!' : 'Não foi dessa vez...'}</div>
            {resultado.acertou && resultado.xpGanho > 0 && <div className="pdd-xp-badge">⭐ +{resultado.xpGanho} XP</div>}
            <div className="pdd-resultado-palavra">
              A palavra era <strong>{resultado.palavraCorreta}</strong>
            </div>
            <div className="pdd-stats">
              <span>
                👥 <strong>{resultado.totalTentativas}</strong> {resultado.totalTentativas === 1 ? 'pessoa tentou' : 'pessoas tentaram'}
              </span>
              <span>
                ✅ <strong>{percentual}%</strong> de acerto
              </span>
            </div>
            {!account?.login && <p className="pdd-dica-login">Entre na sua conta para ganhar uma nova chance amanhã e acumular XP!</p>}
          </div>
        ) : desafio.jaTentou ? (
          <div className="pdd-resultado">
            <div className="pdd-resultado-icone">⏳</div>
            <div className="pdd-resultado-titulo">Você já usou sua chance de hoje</div>
            <div className="pdd-resultado-palavra">Volte amanhã para uma nova palavra!</div>
          </div>
        ) : (
          <form className="pdd-form" onSubmit={enviar}>
            <EntradaPalavra
              className="pdd-input"
              placeholder="Digite a palavra..."
              value={palpite}
              onChange={setPalpite}
              maxLength={desafio.tamanho + 5}
              ariaLabel="Palavra do dia"
            />
            <button type="submit" className="pdd-btn" disabled={!palpite.trim() || enviando}>
              {enviando ? 'Enviando...' : 'Tentar'}
            </button>
          </form>
        )}

        {erro && <div className="pdd-erro">{erro}</div>}
      </div>
    </section>
  );
};

export default PalavraDoDia;
