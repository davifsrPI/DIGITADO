import React, { useEffect, useRef, useState } from 'react';
import { Translate, ValidatedField, ValidatedForm, isEmail, translate } from 'react-jhipster';
import { Button } from 'reactstrap';
import { toast } from 'react-toastify';
import { Link as RouterLink } from 'react-router-dom';
import axios from 'axios';
import './register.scss';

import PasswordStrengthBar from 'app/shared/layout/password/password-strength-bar';
import { validarSenhaForte } from 'app/shared/util/senha-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { handleRegister, reset } from './register.reducer';

type EmailCheck = { status: 'idle' | 'loading' | 'found' | 'notfound'; nome?: string };

export const RegisterPage = () => {
  const [password, setPassword] = useState('');
  const [emailCheck, setEmailCheck] = useState<EmailCheck>({ status: 'idle' });
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dispatch = useAppDispatch();

  useEffect(() => {
    document.body.classList.add('register-page');
    return () => {
      document.body.classList.remove('register-page');
      dispatch(reset());
    };
  }, []);

  const currentLocale = useAppSelector(state => state.locale.currentLocale);

  const handleValidSubmit = ({ username, apelido, email, firstPassword }) => {
    dispatch(handleRegister({ login: username, apelido, email, password: firstPassword, langKey: currentLocale }));
  };

  const updatePassword = event => setPassword(event.target.value);

  const handleEmailChange = (value: string) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!value || !isEmail(value)) {
      setEmailCheck({ status: 'idle' });
      return;
    }
    setEmailCheck({ status: 'loading' });
    debounceRef.current = setTimeout(async () => {
      try {
        const { data } = await axios.get<{ encontrado: boolean; nome?: string }>('/api/public/verificar-email', {
          params: { email: value.trim().toLowerCase() },
        });
        setEmailCheck(data.encontrado ? { status: 'found', nome: data.nome } : { status: 'notfound' });
      } catch {
        setEmailCheck({ status: 'idle' });
      }
    }, 600);
  };

  const successMessage = useAppSelector(state => state.register.successMessage);

  useEffect(() => {
    if (successMessage) {
      toast.success(translate(successMessage));
    }
  }, [successMessage]);

  return (
    <div className="register-wrapper">
      <div className="register-bg">
        <div className="rg-shape one" />
        <div className="rg-shape two" />
        <div className="rg-shape three" />
      </div>

      <div className="register-center">
        <div className="register-brand">
          <span className="register-brand-icon">✦</span>
          DIGITADO
        </div>

        <div className="register-card">
          <h2>Criar conta</h2>
          <p className="register-sub">Preencha seus dados para se cadastrar</p>

          <ValidatedForm id="register-form" onSubmit={handleValidSubmit}>
            <ValidatedField
              name="username"
              label={translate('global.form.username.label')}
              placeholder={translate('global.form.username.placeholder')}
              validate={{
                required: { value: true, message: translate('register.messages.validate.login.required') },
                pattern: {
                  value: /^[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$|^[_.@A-Za-z0-9-]+$/,
                  message: translate('register.messages.validate.login.pattern'),
                },
                minLength: { value: 1, message: translate('register.messages.validate.login.minlength') },
                maxLength: { value: 50, message: translate('register.messages.validate.login.maxlength') },
              }}
              data-cy="username"
            />
            {/* Apelido: o nome público do jogador — é o que aparece no ranking,
                no placar das salas e no pódio */}
            <ValidatedField
              name="apelido"
              label="Apelido"
              placeholder="Como você quer aparecer no jogo"
              validate={{
                required: { value: true, message: 'Escolha um apelido.' },
                minLength: { value: 2, message: 'O apelido precisa de ao menos 2 caracteres.' },
                maxLength: { value: 30, message: 'O apelido pode ter no máximo 30 caracteres.' },
              }}
              data-cy="apelido"
            />
            <ValidatedField
              name="email"
              label={translate('global.form.email.label')}
              placeholder={translate('global.form.email.placeholder')}
              type="email"
              onChange={e => handleEmailChange(e.target.value)}
              validate={{
                required: { value: true, message: translate('global.messages.validate.email.required') },
                minLength: { value: 5, message: translate('global.messages.validate.email.minlength') },
                maxLength: { value: 254, message: translate('global.messages.validate.email.maxlength') },
                validate: v => isEmail(v) || translate('global.messages.validate.email.invalid'),
              }}
              data-cy="email"
            />
            {emailCheck.status === 'loading' && <p className="rg-email-check rg-email-check--loading">Verificando cadastro...</p>}
            {emailCheck.status === 'found' && (
              <p className="rg-email-check rg-email-check--found">
                ✓ Cadastro encontrado: <strong>{emailCheck.nome}</strong>
              </p>
            )}
            {emailCheck.status === 'notfound' && (
              <p className="rg-email-check rg-email-check--notfound">Nenhum cadastro encontrado com este e-mail.</p>
            )}
            <ValidatedField
              name="firstPassword"
              label={translate('global.form.newpassword.label')}
              placeholder={translate('global.form.newpassword.placeholder')}
              type="password"
              onChange={updatePassword}
              validate={{
                required: { value: true, message: translate('global.messages.validate.newpassword.required') },
                minLength: { value: 8, message: translate('global.messages.validate.newpassword.minlength') },
                maxLength: { value: 50, message: translate('global.messages.validate.newpassword.maxlength') },
                validate: validarSenhaForte,
              }}
              data-cy="firstPassword"
            />
            <PasswordStrengthBar password={password} />
            <ValidatedField
              name="secondPassword"
              label={translate('global.form.confirmpassword.label')}
              placeholder={translate('global.form.confirmpassword.placeholder')}
              type="password"
              validate={{
                required: { value: true, message: translate('global.messages.validate.confirmpassword.required') },
                minLength: { value: 8, message: translate('global.messages.validate.confirmpassword.minlength') },
                maxLength: { value: 50, message: translate('global.messages.validate.confirmpassword.maxlength') },
                validate: v => v === password || translate('global.messages.error.dontmatch'),
              }}
              data-cy="secondPassword"
            />
            {/* LGPD art. 9º: informação clara no ato da coleta (a base legal do
                cadastro é execução de contrato — por isso aviso, não checkbox) */}
            <p className="register-aviso-privacidade">
              Ao criar a conta, seus dados (nome, e-mail e desempenho no jogo) serão tratados conforme a{' '}
              <RouterLink to="/privacidade">Política de Privacidade</RouterLink>.
            </p>
            <Button id="register-submit" color="primary" type="submit" data-cy="submit">
              <Translate contentKey="register.form.button">Register</Translate>
            </Button>
          </ValidatedForm>

          <div className="register-footer-link">
            <span>Já tem uma conta?</span>
            <RouterLink to="/login">Entrar</RouterLink>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
