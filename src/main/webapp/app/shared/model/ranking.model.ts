import dayjs from 'dayjs';
import { ISala } from 'app/shared/model/sala.model';
import { IUsuario } from 'app/shared/model/usuario.model';

export interface IRanking {
  id?: number;
  posicao?: number | null;
  pontuacaoTotal?: number | null;
  ultimaAtualizacao?: dayjs.Dayjs | null;
  sala?: ISala | null;
  aluno?: IUsuario | null;
}

export const defaultValue: Readonly<IRanking> = {};
