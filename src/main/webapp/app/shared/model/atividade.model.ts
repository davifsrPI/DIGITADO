import dayjs from 'dayjs';
import { ISala } from 'app/shared/model/sala.model';
import { IListaPalavras } from 'app/shared/model/lista-palavras.model';
import { ModoAtividade } from 'app/shared/model/enumerations/modo-atividade.model';
import { StatusAtividade } from 'app/shared/model/enumerations/status-atividade.model';

export interface IAtividade {
  id?: number;
  titulo?: string;
  modo?: keyof typeof ModoAtividade;
  dataInicio?: dayjs.Dayjs | null;
  dataFim?: dayjs.Dayjs | null;
  tempoLimite?: number | null;
  status?: keyof typeof StatusAtividade | null;
  sala?: ISala | null;
  lista?: IListaPalavras | null;
}

export const defaultValue: Readonly<IAtividade> = {};
