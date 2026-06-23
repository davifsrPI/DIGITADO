import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './palavra.reducer';

export const PalavraDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const palavraEntity = useAppSelector(state => state.palavra.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="palavraDetailsHeading">
          <Translate contentKey="digitadoApp.palavra.detail.title">Palavra</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.id}</dd>
          <dt>
            <span id="texto">
              <Translate contentKey="digitadoApp.palavra.texto">Texto</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.texto}</dd>
          <dt>
            <span id="dificuldade">
              <Translate contentKey="digitadoApp.palavra.dificuldade">Dificuldade</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.dificuldade}</dd>
          <dt>
            <span id="categoria">
              <Translate contentKey="digitadoApp.palavra.categoria">Categoria</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.categoria}</dd>
          <dt>
            <span id="idioma">
              <Translate contentKey="digitadoApp.palavra.idioma">Idioma</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.idioma}</dd>
          <dt>
            <span id="possuiAcento">
              <Translate contentKey="digitadoApp.palavra.possuiAcento">Possui Acento</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.possuiAcento ? 'true' : 'false'}</dd>
          <dt>
            <span id="ativa">
              <Translate contentKey="digitadoApp.palavra.ativa">Ativa</Translate>
            </span>
          </dt>
          <dd>{palavraEntity.ativa ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="digitadoApp.palavra.criador">Criador</Translate>
          </dt>
          <dd>{palavraEntity.criador ? palavraEntity.criador.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.palavra.listas">Listas</Translate>
          </dt>
          <dd>
            {palavraEntity.listas
              ? palavraEntity.listas.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {palavraEntity.listas && i === palavraEntity.listas.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/palavra" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/palavra/${palavraEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default PalavraDetail;
