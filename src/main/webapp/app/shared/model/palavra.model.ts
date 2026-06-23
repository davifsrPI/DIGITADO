import { IUsuario } from 'app/shared/model/usuario.model';
import { IListaPalavras } from 'app/shared/model/lista-palavras.model';
import { Dificuldade } from 'app/shared/model/enumerations/dificuldade.model';

export interface IPalavra {
  id?: number;
  texto?: string;
  dificuldade?: keyof typeof Dificuldade;
  categoria?: string | null;
  idioma?: string | null;
  possuiAcento?: boolean | null;
  ativa?: boolean | null;
  criador?: IUsuario | null;
  listas?: IListaPalavras[] | null;
}

export const defaultValue: Readonly<IPalavra> = {
  possuiAcento: false,
  ativa: false,
};
