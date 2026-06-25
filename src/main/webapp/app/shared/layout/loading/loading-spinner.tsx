import './loading-spinner.scss';
import React from 'react';

export const LoadingSpinner = () => (
  <div className="ls-wrapper">
    <div className="ls-bg">
      <div className="ls-shape one" />
      <div className="ls-shape two" />
    </div>
    <div className="ls-content">
      <div className="ls-logo">✦ DIGITADO</div>
      <div className="ls-spinner">
        <span />
        <span />
        <span />
      </div>
      <p className="ls-text">Carregando...</p>
    </div>
  </div>
);

export default LoadingSpinner;
