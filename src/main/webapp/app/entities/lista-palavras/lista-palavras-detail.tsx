import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './lista-palavras.reducer';

export const ListaPalavrasDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const listaPalavrasEntity = useAppSelector(state => state.listaPalavras.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="listaPalavrasDetailsHeading">
          <Translate contentKey="digitadoApp.listaPalavras.detail.title">ListaPalavras</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{listaPalavrasEntity.id}</dd>
          <dt>
            <span id="nomeLista">
              <Translate contentKey="digitadoApp.listaPalavras.nomeLista">Nome Lista</Translate>
            </span>
          </dt>
          <dd>{listaPalavrasEntity.nomeLista}</dd>
          <dt>
            <span id="descricao">
              <Translate contentKey="digitadoApp.listaPalavras.descricao">Descricao</Translate>
            </span>
          </dt>
          <dd>{listaPalavrasEntity.descricao}</dd>
          <dt>
            <span id="ativo">
              <Translate contentKey="digitadoApp.listaPalavras.ativo">Ativo</Translate>
            </span>
          </dt>
          <dd>{listaPalavrasEntity.ativo ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="digitadoApp.listaPalavras.palavras">Palavras</Translate>
          </dt>
          <dd>
            {listaPalavrasEntity.palavras
              ? listaPalavrasEntity.palavras.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {listaPalavrasEntity.palavras && i === listaPalavrasEntity.palavras.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="digitadoApp.listaPalavras.professor">Professor</Translate>
          </dt>
          <dd>{listaPalavrasEntity.professor ? listaPalavrasEntity.professor.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/lista-palavras" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/lista-palavras/${listaPalavrasEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ListaPalavrasDetail;
