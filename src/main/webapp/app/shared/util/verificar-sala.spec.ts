import axios from 'axios';
import { verificarSala } from './verificar-sala';

jest.mock('axios');
const get = axios.get as jest.Mock;

describe('verificarSala (código conferido antes de sair do lobby)', () => {
  afterEach(() => get.mockReset());

  it('sala de turma existente', async () => {
    get.mockResolvedValue({ data: { codigo: 'ABC234', tipo: 'TURMA' } });
    expect(await verificarSala('ABC234')).toEqual({ status: 'ok', tipo: 'TURMA' });
    expect(get).toHaveBeenCalledWith('/api/salas/ABC234');
  });

  it('duelo 1v1 existente', async () => {
    get.mockResolvedValue({ data: { codigo: 'XYZ789', tipo: 'UM_V_UM' } });
    expect(await verificarSala('XYZ789')).toEqual({ status: 'ok', tipo: 'UM_V_UM' });
  });

  it('código que não existe (404)', async () => {
    get.mockRejectedValue({ response: { status: 404 } });
    expect(await verificarSala('ERRADO')).toEqual({ status: 'inexistente' });
  });

  // Rede caída não pode acusar o código do aluno de errado
  it('falha de rede não é "código errado"', async () => {
    get.mockRejectedValue(new Error('Network Error'));
    expect(await verificarSala('ABC234')).toEqual({ status: 'falha' });
    get.mockRejectedValue({ response: { status: 503 } });
    expect(await verificarSala('ABC234')).toEqual({ status: 'falha' });
  });
});
