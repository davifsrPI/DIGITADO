import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getSalas } from 'app/entities/sala/sala.reducer';
import { getEntities as getUsuarios } from 'app/entities/usuario/usuario.reducer';
import { createEntity, getEntity, reset, updateEntity } from './ranking.reducer';

export const RankingUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const salas = useAppSelector(state => state.sala.entities);
  const usuarios = useAppSelector(state => state.usuario.entities);
  const rankingEntity = useAppSelector(state => state.ranking.entity);
  const loading = useAppSelector(state => state.ranking.loading);
  const updating = useAppSelector(state => state.ranking.updating);
  const updateSuccess = useAppSelector(state => state.ranking.updateSuccess);

  const handleClose = () => {
    navigate('/ranking');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getSalas({}));
    dispatch(getUsuarios({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.posicao !== undefined && typeof values.posicao !== 'number') {
      values.posicao = Number(values.posicao);
    }
    if (values.pontuacaoTotal !== undefined && typeof values.pontuacaoTotal !== 'number') {
      values.pontuacaoTotal = Number(values.pontuacaoTotal);
    }
    values.ultimaAtualizacao = convertDateTimeToServer(values.ultimaAtualizacao);

    const entity = {
      ...rankingEntity,
      ...values,
      sala: salas.find(it => it.id.toString() === values.sala?.toString()),
      aluno: usuarios.find(it => it.id.toString() === values.aluno?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          ultimaAtualizacao: displayDefaultDateTime(),
        }
      : {
          ...rankingEntity,
          ultimaAtualizacao: convertDateTimeFromServer(rankingEntity.ultimaAtualizacao),
          sala: rankingEntity?.sala?.id,
          aluno: rankingEntity?.aluno?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.ranking.home.createOrEditLabel" data-cy="RankingCreateUpdateHeading">
            <Translate contentKey="digitadoApp.ranking.home.createOrEditLabel">Create or edit a Ranking</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="ranking-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.ranking.posicao')}
                id="ranking-posicao"
                name="posicao"
                data-cy="posicao"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.ranking.pontuacaoTotal')}
                id="ranking-pontuacaoTotal"
                name="pontuacaoTotal"
                data-cy="pontuacaoTotal"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.ranking.ultimaAtualizacao')}
                id="ranking-ultimaAtualizacao"
                name="ultimaAtualizacao"
                data-cy="ultimaAtualizacao"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField id="ranking-sala" name="sala" data-cy="sala" label={translate('digitadoApp.ranking.sala')} type="select">
                <option value="" key="0" />
                {salas
                  ? salas.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField id="ranking-aluno" name="aluno" data-cy="aluno" label={translate('digitadoApp.ranking.aluno')} type="select">
                <option value="" key="0" />
                {usuarios
                  ? usuarios.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/ranking" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default RankingUpdate;
