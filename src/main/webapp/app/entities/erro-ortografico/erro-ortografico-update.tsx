import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getRespostas } from 'app/entities/resposta/resposta.reducer';
import { TipoErro } from 'app/shared/model/enumerations/tipo-erro.model';
import { createEntity, getEntity, reset, updateEntity } from './erro-ortografico.reducer';

export const ErroOrtograficoUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const respostas = useAppSelector(state => state.resposta.entities);
  const erroOrtograficoEntity = useAppSelector(state => state.erroOrtografico.entity);
  const loading = useAppSelector(state => state.erroOrtografico.loading);
  const updating = useAppSelector(state => state.erroOrtografico.updating);
  const updateSuccess = useAppSelector(state => state.erroOrtografico.updateSuccess);
  const tipoErroValues = Object.keys(TipoErro);

  const handleClose = () => {
    navigate('/erro-ortografico');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getRespostas({}));
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

    const entity = {
      ...erroOrtograficoEntity,
      ...values,
      resposta: respostas.find(it => it.id.toString() === values.resposta?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          tipoErro: 'ACENTUACAO',
          ...erroOrtograficoEntity,
          resposta: erroOrtograficoEntity?.resposta?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.erroOrtografico.home.createOrEditLabel" data-cy="ErroOrtograficoCreateUpdateHeading">
            <Translate contentKey="digitadoApp.erroOrtografico.home.createOrEditLabel">Create or edit a ErroOrtografico</Translate>
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
                  id="erro-ortografico-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.erroOrtografico.tipoErro')}
                id="erro-ortografico-tipoErro"
                name="tipoErro"
                data-cy="tipoErro"
                type="select"
              >
                {tipoErroValues.map(tipoErro => (
                  <option value={tipoErro} key={tipoErro}>
                    {translate(`digitadoApp.TipoErro.${tipoErro}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('digitadoApp.erroOrtografico.descricao')}
                id="erro-ortografico-descricao"
                name="descricao"
                data-cy="descricao"
                type="textarea"
              />
              <ValidatedField
                id="erro-ortografico-resposta"
                name="resposta"
                data-cy="resposta"
                label={translate('digitadoApp.erroOrtografico.resposta')}
                type="select"
              >
                <option value="" key="0" />
                {respostas
                  ? respostas.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/erro-ortografico" replace color="info">
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

export default ErroOrtograficoUpdate;
