import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getSalas } from 'app/entities/sala/sala.reducer';
import { getEntities as getListaPalavras } from 'app/entities/lista-palavras/lista-palavras.reducer';
import { ModoAtividade } from 'app/shared/model/enumerations/modo-atividade.model';
import { StatusAtividade } from 'app/shared/model/enumerations/status-atividade.model';
import { createEntity, getEntity, reset, updateEntity } from './atividade.reducer';

export const AtividadeUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const salas = useAppSelector(state => state.sala.entities);
  const listaPalavras = useAppSelector(state => state.listaPalavras.entities);
  const atividadeEntity = useAppSelector(state => state.atividade.entity);
  const loading = useAppSelector(state => state.atividade.loading);
  const updating = useAppSelector(state => state.atividade.updating);
  const updateSuccess = useAppSelector(state => state.atividade.updateSuccess);
  const modoAtividadeValues = Object.keys(ModoAtividade);
  const statusAtividadeValues = Object.keys(StatusAtividade);

  const handleClose = () => {
    navigate('/atividade');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getSalas({}));
    dispatch(getListaPalavras({}));
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
    values.dataInicio = convertDateTimeToServer(values.dataInicio);
    values.dataFim = convertDateTimeToServer(values.dataFim);
    if (values.tempoLimite !== undefined && typeof values.tempoLimite !== 'number') {
      values.tempoLimite = Number(values.tempoLimite);
    }

    const entity = {
      ...atividadeEntity,
      ...values,
      sala: salas.find(it => it.id.toString() === values.sala?.toString()),
      lista: listaPalavras.find(it => it.id.toString() === values.lista?.toString()),
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
          dataInicio: displayDefaultDateTime(),
          dataFim: displayDefaultDateTime(),
        }
      : {
          modo: 'INDIVIDUAL',
          status: 'PENDENTE',
          ...atividadeEntity,
          dataInicio: convertDateTimeFromServer(atividadeEntity.dataInicio),
          dataFim: convertDateTimeFromServer(atividadeEntity.dataFim),
          sala: atividadeEntity?.sala?.id,
          lista: atividadeEntity?.lista?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.atividade.home.createOrEditLabel" data-cy="AtividadeCreateUpdateHeading">
            <Translate contentKey="digitadoApp.atividade.home.createOrEditLabel">Create or edit a Atividade</Translate>
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
                  id="atividade-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.atividade.titulo')}
                id="atividade-titulo"
                name="titulo"
                data-cy="titulo"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField label={translate('digitadoApp.atividade.modo')} id="atividade-modo" name="modo" data-cy="modo" type="select">
                {modoAtividadeValues.map(modoAtividade => (
                  <option value={modoAtividade} key={modoAtividade}>
                    {translate(`digitadoApp.ModoAtividade.${modoAtividade}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('digitadoApp.atividade.dataInicio')}
                id="atividade-dataInicio"
                name="dataInicio"
                data-cy="dataInicio"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                label={translate('digitadoApp.atividade.dataFim')}
                id="atividade-dataFim"
                name="dataFim"
                data-cy="dataFim"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                label={translate('digitadoApp.atividade.tempoLimite')}
                id="atividade-tempoLimite"
                name="tempoLimite"
                data-cy="tempoLimite"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.atividade.status')}
                id="atividade-status"
                name="status"
                data-cy="status"
                type="select"
              >
                {statusAtividadeValues.map(statusAtividade => (
                  <option value={statusAtividade} key={statusAtividade}>
                    {translate(`digitadoApp.StatusAtividade.${statusAtividade}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField id="atividade-sala" name="sala" data-cy="sala" label={translate('digitadoApp.atividade.sala')} type="select">
                <option value="" key="0" />
                {salas
                  ? salas.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="atividade-lista"
                name="lista"
                data-cy="lista"
                label={translate('digitadoApp.atividade.lista')}
                type="select"
              >
                <option value="" key="0" />
                {listaPalavras
                  ? listaPalavras.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/atividade" replace color="info">
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

export default AtividadeUpdate;
