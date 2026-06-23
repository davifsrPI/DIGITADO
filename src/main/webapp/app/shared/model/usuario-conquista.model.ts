import dayjs from 'dayjs';
import { IUsuario } from 'app/shared/model/usuario.model';
import { IConquista } from 'app/shared/model/conquista.model';

export interface IUsuarioConquista {
  id?: number;
  dataConquista?: dayjs.Dayjs | null;
  progresso?: number | null;
  concluida?: boolean | null;
  aluno?: IUsuario | null;
  conquista?: IConquista | null;
}

export const defaultValue: Readonly<IUsuarioConquista> = {
  concluida: false,
};
