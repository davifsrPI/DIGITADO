import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './usuario.reducer';

export const Usuario = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const usuarioList = useAppSelector(state => state.usuario.entities);
  const loading = useAppSelector(state => state.usuario.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const order = sortState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <div>
      <h2 id="usuario-heading" data-cy="UsuarioHeading">
        <Translate contentKey="digitadoApp.usuario.home.title">Usuarios</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.usuario.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/usuario/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.usuario.home.createLabel">Create new Usuario</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {usuarioList && usuarioList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.usuario.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('nome')}>
                  <Translate contentKey="digitadoApp.usuario.nome">Nome</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('nome')} />
                </th>
                <th className="hand" onClick={sort('sobrenome')}>
                  <Translate contentKey="digitadoApp.usuario.sobrenome">Sobrenome</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('sobrenome')} />
                </th>
                <th className="hand" onClick={sort('email')}>
                  <Translate contentKey="digitadoApp.usuario.email">Email</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('email')} />
                </th>
                <th className="hand" onClick={sort('senha')}>
                  <Translate contentKey="digitadoApp.usuario.senha">Senha</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('senha')} />
                </th>
                <th className="hand" onClick={sort('tipoUsuario')}>
                  <Translate contentKey="digitadoApp.usuario.tipoUsuario">Tipo Usuario</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tipoUsuario')} />
                </th>
                <th className="hand" onClick={sort('ativo')}>
                  <Translate contentKey="digitadoApp.usuario.ativo">Ativo</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('ativo')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.usuario.salasAluno">Salas Aluno</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {usuarioList.map((usuario, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/usuario/${usuario.id}`} color="link" size="sm">
                      {usuario.id}
                    </Button>
                  </td>
                  <td>{usuario.nome}</td>
                  <td>{usuario.sobrenome}</td>
                  <td>{usuario.email}</td>
                  <td>{usuario.senha}</td>
                  <td>
                    <Translate contentKey={`digitadoApp.TipoUsuario.${usuario.tipoUsuario}`} />
                  </td>
                  <td>{usuario.ativo ? 'true' : 'false'}</td>
                  <td>
                    {usuario.salasAlunos
                      ? usuario.salasAlunos.map((val, j) => (
                          <span key={j}>
                            <Link to={`/sala/${val.codigo}`}>{val.codigo}</Link>
                            {j === usuario.salasAlunos.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/usuario/${usuario.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/usuario/${usuario.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/usuario/${usuario.id}/delete`)}
                        color="danger"
                        size="sm"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="digitadoApp.usuario.home.notFound">No Usuarios found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Usuario;
