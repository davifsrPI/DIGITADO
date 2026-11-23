import React from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { AccountMenu } from './account';

describe('AccountMenu', () => {
  let mountedWrapper;

  const authenticatedWrapper = () => {
    if (!mountedWrapper) {
      const { container } = render(
        <MemoryRouter>
          <AccountMenu isAuthenticated />
        </MemoryRouter>,
      );
      mountedWrapper = container.innerHTML;
    }
    return mountedWrapper;
  };
  const guestWrapper = () => {
    if (!mountedWrapper) {
      const { container } = (mountedWrapper = render(
        <MemoryRouter>
          <AccountMenu />
        </MemoryRouter>,
      ));
      mountedWrapper = container.innerHTML;
    }
    return mountedWrapper;
  };

  beforeEach(() => {
    mountedWrapper = undefined;
  });

  // All tests will go here

  it('Renders a authenticated AccountMenu component', () => {
    const html = authenticatedWrapper();

    expect(html).not.toContain('/login');
    expect(html).toContain('/logout');
  });

  it('a conta admin não vê as telas de jogador no menu', () => {
    const { container } = render(
      <MemoryRouter>
        <AccountMenu isAuthenticated isAdmin />
      </MemoryRouter>,
    );
    expect(container.innerHTML).not.toContain('/minhas-salas');
    expect(container.innerHTML).not.toContain('/conquistas');
    expect(container.innerHTML).toContain('/admin/desempenho');
  });

  it('o jogador vê as salas e as conquistas no menu', () => {
    const html = authenticatedWrapper();
    expect(html).toContain('/minhas-salas');
    expect(html).toContain('/conquistas');
  });

  it('Renders a guest AccountMenu component', () => {
    const html = guestWrapper();

    expect(html).toContain('/login');
    expect(html).not.toContain('/logout');
  });
});
