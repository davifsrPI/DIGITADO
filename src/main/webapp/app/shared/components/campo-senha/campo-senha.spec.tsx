import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { ValidatedForm } from 'react-jhipster';
import { CampoSenha, CampoSenhaValidado } from './campo-senha';

describe('CampoSenha (olhinho da senha)', () => {
  it('começa escondida e alterna entre mostrar e esconder', () => {
    render(<CampoSenha placeholder="Sua senha" defaultValue="segredo" />);
    const campo = screen.getByPlaceholderText('Sua senha');
    expect(campo.getAttribute('type')).toBe('password');

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(campo.getAttribute('type')).toBe('text');
    expect(screen.getByRole('button', { name: 'Ocultar senha' }).getAttribute('aria-pressed')).toBe('true');

    fireEvent.click(screen.getByRole('button', { name: 'Ocultar senha' }));
    expect(campo.getAttribute('type')).toBe('password');
  });

  it('o botão não envia o formulário', () => {
    const enviar = jest.fn(e => e.preventDefault());
    render(
      <form onSubmit={enviar}>
        <CampoSenha placeholder="Sua senha" />
      </form>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(enviar).not.toHaveBeenCalled();
  });

  // Dentro do ValidatedForm o campo continua registrado: a validação segue valendo
  it('dentro do ValidatedForm continua validando', async () => {
    const enviar = jest.fn();
    render(
      <ValidatedForm onSubmit={enviar}>
        <CampoSenhaValidado
          name="senha"
          label="Senha"
          placeholder="Senha nova"
          validate={{ required: { value: true, message: 'Informe a senha' } }}
        />
        <button type="submit">Salvar</button>
      </ValidatedForm>,
    );
    const campo = screen.getByPlaceholderText('Senha nova');
    expect(campo.getAttribute('type')).toBe('password');
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(campo.getAttribute('type')).toBe('text');

    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Informe a senha')).toBeTruthy();
    expect(enviar).not.toHaveBeenCalled();
  });
});
