import dayjs from 'dayjs';
import { IAtividade } from 'app/shared/model/atividade.model';
import { IUsuario } from 'app/shared/model/usuario.model';
import { IPalavra } from 'app/shared/model/palavra.model';

export interface IResposta {
  id?: number;
  respostaDigitada?: string | null;
  correta?: boolean | null;
  tempoResposta?: number | null;
  pontuacao?: number | null;
  dataResposta?: dayjs.Dayjs | null;
  atividade?: IAtividade | null;
  aluno?: IUsuario | null;
  palavra?: IPalavra | null;
}

export const defaultValue: Readonly<IResposta> = {
  correta: false,
};
