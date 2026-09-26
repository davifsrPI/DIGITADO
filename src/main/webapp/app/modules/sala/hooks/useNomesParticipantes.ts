import { useEffect, useState } from 'react';
import axios from 'axios';

// Uma linha de GET /api/salas/{codigo}/participantes (restrito ao dono da sala)
interface ParticipanteVM {
  login: string;
  nome: string;
  turma: string;
  usarApelido: boolean;
  apelido: string | null;
  // Nome verdadeiro e, quando o aluno joga de apelido, o apelido entre parênteses
  nomeProfessor: string;
}

/**
 * Traduz, nas telas do PROFESSOR, o login de cada linha do placar para o nome
 * verdadeiro do aluno e a turma dele.
 *
 * O placar e o relatório trafegam com o nome PÚBLICO - o apelido, quando o
 * aluno escolheu se esconder dos colegas. O nome real nunca vai para o tópico
 * do WebSocket (senão bastaria abrir o console para descobrir quem é quem);
 * ele sai só por este endpoint, que o servidor restringe ao dono da sala.
 *
 * Sala antiga, sem ninguém identificado, devolve mapas vazios e as telas seguem
 * mostrando o nome público, como antes.
 *
 * logins: quem está na tela agora (placar, lista de conectados). Sempre que
 * aparecer alguém que ainda não está no mapa, a lista é buscada de novo - o
 * aluno que se identifica DEPOIS de a tela do professor abrir (o caso normal:
 * ela abre vazia e a turma vai entrando) ficava fora do mapa para sempre, e o
 * professor via o apelido dele até recarregar a página.
 */
export function useNomesParticipantes(codigoSala?: string, habilitado = true, logins: string[] = []) {
  const [nomes, setNomes] = useState<Record<string, string>>({});
  const [turmas, setTurmas] = useState<Record<string, string>>({});

  // Chave estável de quem está na tela e ainda não foi traduzido. Continua a
  // mesma quando a busca não trouxe o nome (aluno que nunca se identificou),
  // então não vira uma requisição por render.
  const desconhecidos = logins
    .filter(login => login && !(login in nomes))
    .sort()
    .join('|');

  useEffect(() => {
    if (!codigoSala || !habilitado) return;
    let cancelado = false;
    axios
      .get<ParticipanteVM[]>(`/api/salas/${codigoSala}/participantes`)
      .then(res => {
        if (cancelado) return;
        setNomes(Object.fromEntries(res.data.map(p => [p.login, p.nomeProfessor])));
        setTurmas(Object.fromEntries(res.data.map(p => [p.login, p.turma])));
      })
      .catch(() => {
        // Sem acesso ou sem identificações: as telas caem no nome público
      });
    return () => {
      cancelado = true;
    };
  }, [codigoSala, habilitado, desconhecidos]);

  return { nomes, turmas };
}

export default useNomesParticipantes;
