#!/bin/bash
# Cria o usuario do MySQL que a aplicacao usa para conectar.
#
# O perfil 'prod' nao conecta como root (application-prod.yml usa o usuario
# 'digitado'), entao ele precisa existir. A criacao e feita aqui, e nao pelas
# variaveis MYSQL_USER/MYSQL_PASSWORD da imagem, porque o dump 10-dados.sql
# roda antes e faz DROP DATABASE - o usuario ate sobreviveria, mas depender
# dessa ordem seria fragil.
#
# Roda uma unica vez, na primeira subida, quando o volume do banco esta vazio.

mysql --protocol=socket -u root -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'digitado'@'%' IDENTIFIED BY '${DIGITADO_DB_PASSWORD}';
ALTER USER 'digitado'@'%' IDENTIFIED BY '${DIGITADO_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON digitado.* TO 'digitado'@'%';
FLUSH PRIVILEGES;
SQL
