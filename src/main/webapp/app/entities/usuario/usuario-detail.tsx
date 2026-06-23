import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './usuario.reducer';

export const UsuarioDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const usuarioEntity = useAppSelector(state => state.usuario.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="usuarioDetailsHeading">
          <Translate contentKey="digitadoApp.usuario.detail.title">Usuario</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.id}</dd>
          <dt>
            <span id="nome">
              <Translate contentKey="digitadoApp.usuario.nome">Nome</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.nome}</dd>
          <dt>
            <span id="sobrenome">
              <Translate contentKey="digitadoApp.usuario.sobrenome">Sobrenome</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.sobrenome}</dd>
          <dt>
            <span id="email">
              <Translate contentKey="digitadoApp.usuario.email">Email</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.email}</dd>
          <dt>
            <span id="senha">
              <Translate contentKey="digitadoApp.usuario.senha">Senha</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.senha}</dd>
          <dt>
            <span id="tipoUsuario">
              <Translate contentKey="digitadoApp.usuario.tipoUsuario">Tipo Usuario</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.tipoUsuario}</dd>
          <dt>
            <span id="ativo">
              <Translate contentKey="digitadoApp.usuario.ativo">Ativo</Translate>
            </span>
          </dt>
          <dd>{usuarioEntity.ativo ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="digitadoApp.usuario.salasAluno">Salas Aluno</Translate>
          </dt>
          <dd>
            {usuarioEntity.salasAlunos
              ? usuarioEntity.salasAlunos.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {usuarioEntity.salasAlunos && i === usuarioEntity.salasAlunos.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/usuario" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/usuario/${usuarioEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default UsuarioDetail;
