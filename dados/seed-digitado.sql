-- MySQL dump 10.13  Distrib 9.2.0, for Linux (x86_64)
--
-- Host: localhost    Database: digitado
-- ------------------------------------------------------
-- Server version	9.2.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `digitado`
--

/*!40000 DROP DATABASE IF EXISTS `digitado`*/;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `digitado` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `digitado`;

--
-- Table structure for table `atividade`
--

DROP TABLE IF EXISTS `atividade`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `atividade` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `titulo` varchar(255) NOT NULL,
  `modo` varchar(255) NOT NULL,
  `data_inicio` datetime(6) DEFAULT NULL,
  `data_fim` datetime(6) DEFAULT NULL,
  `tempo_limite` int DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `lista_id` bigint DEFAULT NULL,
  `sala_codigo` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_atividade__lista_id` (`lista_id`),
  KEY `fk_atividade__sala_codigo` (`sala_codigo`),
  CONSTRAINT `fk_atividade__lista_id` FOREIGN KEY (`lista_id`) REFERENCES `lista_palavras` (`id`),
  CONSTRAINT `fk_atividade__sala_codigo` FOREIGN KEY (`sala_codigo`) REFERENCES `sala` (`codigo`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `atividade`
--

LOCK TABLES `atividade` WRITE;
/*!40000 ALTER TABLE `atividade` DISABLE KEYS */;
/*!40000 ALTER TABLE `atividade` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conquista`
--

DROP TABLE IF EXISTS `conquista`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conquista` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nome` varchar(255) NOT NULL,
  `descricao` longtext,
  `xp_recompensa` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conquista`
--

LOCK TABLES `conquista` WRITE;
/*!40000 ALTER TABLE `conquista` DISABLE KEYS */;
INSERT INTO `conquista` VALUES (1,'Primeiras Teclas','Complete sua primeira partida no DIGITADO.',50),(2,'Bem-vindo à Turma','Entre em uma sala pela primeira vez.',25),(3,'Aquecendo os Dedos','Digite 10 palavras corretamente.',50),(4,'Vocabulário em Construção','Digite 50 palavras corretamente.',100),(5,'Centena Digitada','Digite 100 palavras corretamente.',150),(6,'Dicionário Ambulante','Digite 500 palavras corretamente.',300),(7,'Mestre das Palavras','Digite 1000 palavras corretamente.',500),(8,'Sem Pressa e Sem Erro','Termine uma partida sem errar nenhuma palavra.',150),(9,'Perfeccionista','Complete 5 partidas sem nenhum erro.',300),(10,'Lenda da Ortografia','Complete 20 partidas sem nenhum erro.',600),(11,'Raio Veloz','Acerte uma palavra em menos de 3 segundos.',75),(12,'Dedos de Foguete','Acerte 10 palavras em menos de 3 segundos cada.',150),(13,'Supersônico','Acerte 50 palavras em menos de 3 segundos cada.',300),(14,'Primeira Vitória','Termine uma partida em 1º lugar no placar.',100),(15,'Campeão em Série','Vença 5 partidas.',250),(16,'Invencível','Vença 10 partidas.',400),(17,'Pódio Garantido','Termine entre os 3 primeiros em 10 partidas.',200),(18,'Maratonista do Teclado','Participe de 25 partidas.',200),(19,'Viciado em Digitar','Participe de 50 partidas.',350),(20,'Frequência Perfeita','Jogue em 7 dias diferentes.',150),(21,'Sequência de Ouro','Acerte 10 palavras seguidas na mesma partida.',100),(22,'Sequência de Diamante','Acerte 25 palavras seguidas na mesma partida.',250),(23,'Imparável','Acerte 50 palavras seguidas na mesma partida.',500),(24,'Aprendendo com os Erros','Acerte uma palavra que você já tinha errado antes.',50),(25,'Superação','Acerte 10 palavras que você já tinha errado antes.',150),(26,'Acentuação Nota 10','Acerte 20 palavras com acento.',100),(27,'Caçador de Cedilhas','Acerte 15 palavras com a letra ç.',100),(28,'Explorador de Salas','Participe de 5 salas diferentes.',100),(29,'Aluno Dedicado','Complete 10 atividades.',150),(30,'Subindo no Ranking','Entre no top 10 do ranking geral.',200),(31,'Elite do Teclado','Alcance o top 3 do ranking geral.',400),(32,'Colecionador de XP','Acumule 1000 pontos de experiência.',150),(33,'Milionário de XP','Acumule 5000 pontos de experiência.',300),(34,'Madrugador','Jogue uma partida antes das 8 horas da manhã.',75),(35,'Coruja do Teclado','Jogue uma partida depois das 22 horas.',75),(36,'Primeiro Duelo','Jogue sua primeira partida 1 contra 1.',75),(37,'Duelista','Vença 5 duelos 1 contra 1.',250),(38,'Lenda dos Duelos','Vença 25 duelos 1 contra 1.',500),(39,'Vença de um Desenvolvedor','Vença um duelo 1 contra 1 contra o desenvolvedor do DIGITADO (usuário administrador).',1000);
/*!40000 ALTER TABLE `conquista` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `databasechangelog`
--

DROP TABLE IF EXISTS `databasechangelog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `databasechangelog` (
  `ID` varchar(255) NOT NULL,
  `AUTHOR` varchar(255) NOT NULL,
  `FILENAME` varchar(255) NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) NOT NULL,
  `MD5SUM` varchar(35) DEFAULT NULL,
  `DESCRIPTION` varchar(255) DEFAULT NULL,
  `COMMENTS` varchar(255) DEFAULT NULL,
  `TAG` varchar(255) DEFAULT NULL,
  `LIQUIBASE` varchar(20) DEFAULT NULL,
  `CONTEXTS` varchar(255) DEFAULT NULL,
  `LABELS` varchar(255) DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `databasechangelog`
--

LOCK TABLES `databasechangelog` WRITE;
/*!40000 ALTER TABLE `databasechangelog` DISABLE KEYS */;
INSERT INTO `databasechangelog` VALUES ('00000000000001','jhipster','config/liquibase/changelog/00000000000000_initial_schema.xml','2026-06-23 20:08:02',1,'EXECUTED','9:c7fb86a72d1815e00aff2981ae71c464','createTable tableName=jhi_user; createTable tableName=jhi_authority; createTable tableName=jhi_user_authority; addPrimaryKey tableName=jhi_user_authority; addForeignKeyConstraint baseTableName=jhi_user_authority, constraintName=fk_authority_name, ...','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221029-1','jhipster','config/liquibase/changelog/20260623221029_added_entity_Usuario.xml','2026-06-23 20:08:02',2,'EXECUTED','9:de333eacce4e21ba09072133b36e1b78','createTable tableName=usuario','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221029-1-relations','jhipster','config/liquibase/changelog/20260623221029_added_entity_Usuario.xml','2026-06-23 20:08:02',3,'EXECUTED','9:53828464a097cc1ba04d342a1676fb75','createTable tableName=rel_usuario__salas_aluno; addPrimaryKey tableName=rel_usuario__salas_aluno','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221029-1-data','jhipster','config/liquibase/changelog/20260623221029_added_entity_Usuario.xml','2026-06-23 20:08:02',4,'EXECUTED','9:da262b358d872ccd563150eff3292343','loadData tableName=usuario','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221030-1','jhipster','config/liquibase/changelog/20260623221030_added_entity_Sala.xml','2026-06-23 20:08:02',5,'EXECUTED','9:e48622f68bb6ac4b963158a90e39a067','createTable tableName=sala','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221030-1-data','jhipster','config/liquibase/changelog/20260623221030_added_entity_Sala.xml','2026-06-23 20:08:03',6,'EXECUTED','9:0cd5e59553cd6f53c3c4f3731c0ea0cc','loadData tableName=sala','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221031-1','jhipster','config/liquibase/changelog/20260623221031_added_entity_ListaPalavras.xml','2026-06-23 20:08:03',7,'EXECUTED','9:04cef8f4629f3f2bff771d7b8e761952','createTable tableName=lista_palavras','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221031-1-relations','jhipster','config/liquibase/changelog/20260623221031_added_entity_ListaPalavras.xml','2026-06-23 20:08:03',8,'EXECUTED','9:1d388cac664eeebc3b67e361140fe003','createTable tableName=rel_lista_palavras__palavras; addPrimaryKey tableName=rel_lista_palavras__palavras','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221031-1-data','jhipster','config/liquibase/changelog/20260623221031_added_entity_ListaPalavras.xml','2026-06-23 20:08:03',9,'EXECUTED','9:2b4833b0298c8a4ade7f381ac01eef69','loadData tableName=lista_palavras','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221032-1','jhipster','config/liquibase/changelog/20260623221032_added_entity_Palavra.xml','2026-06-23 20:08:03',10,'EXECUTED','9:451e988693755df2fac6ea10e31d4d93','createTable tableName=palavra','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221032-1-data','jhipster','config/liquibase/changelog/20260623221032_added_entity_Palavra.xml','2026-06-23 20:08:03',11,'EXECUTED','9:4cd03f10c18a912b249885bc7e15f9e8','loadData tableName=palavra','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221033-1','jhipster','config/liquibase/changelog/20260623221033_added_entity_Atividade.xml','2026-06-23 20:08:03',12,'EXECUTED','9:f3d2b5ec0d438f696466838ba0c2e007','createTable tableName=atividade; dropDefaultValue columnName=data_inicio, tableName=atividade; dropDefaultValue columnName=data_fim, tableName=atividade','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221033-1-data','jhipster','config/liquibase/changelog/20260623221033_added_entity_Atividade.xml','2026-06-23 20:08:03',13,'EXECUTED','9:dd73cd93b6c4e59f7c3ae294dee36405','loadData tableName=atividade','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221034-1','jhipster','config/liquibase/changelog/20260623221034_added_entity_Resposta.xml','2026-06-23 20:08:03',14,'EXECUTED','9:ff7f726152782f0bf5dc4d9c0e864e21','createTable tableName=resposta; dropDefaultValue columnName=data_resposta, tableName=resposta','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221034-1-data','jhipster','config/liquibase/changelog/20260623221034_added_entity_Resposta.xml','2026-06-23 20:08:03',15,'EXECUTED','9:8ae2736777fef9208ce1e7f9eb7b6ed8','loadData tableName=resposta','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221035-1','jhipster','config/liquibase/changelog/20260623221035_added_entity_ErroOrtografico.xml','2026-06-23 20:08:03',16,'EXECUTED','9:623b653739ac99ae9bcc351629ea002a','createTable tableName=erro_ortografico','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221035-1-data','jhipster','config/liquibase/changelog/20260623221035_added_entity_ErroOrtografico.xml','2026-06-23 20:08:03',17,'EXECUTED','9:8a0c3165738a29eda5973c09e0c1d265','loadData tableName=erro_ortografico','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221036-1','jhipster','config/liquibase/changelog/20260623221036_added_entity_Ranking.xml','2026-06-23 20:08:03',18,'EXECUTED','9:ead071b4f883ef8dd5e6501d4a70a6b5','createTable tableName=ranking; dropDefaultValue columnName=ultima_atualizacao, tableName=ranking','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221036-1-data','jhipster','config/liquibase/changelog/20260623221036_added_entity_Ranking.xml','2026-06-23 20:08:03',19,'EXECUTED','9:44bbf435fc67de675dde7cab7346c983','loadData tableName=ranking','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221037-1','jhipster','config/liquibase/changelog/20260623221037_added_entity_Conquista.xml','2026-06-23 20:08:03',20,'EXECUTED','9:4570603dc5733d924fc9afb49041a3ab','createTable tableName=conquista','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221037-1-data','jhipster','config/liquibase/changelog/20260623221037_added_entity_Conquista.xml','2026-06-23 20:08:03',21,'EXECUTED','9:6a122f86abcfddef3ccb0555102d94ee','loadData tableName=conquista','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221038-1','jhipster','config/liquibase/changelog/20260623221038_added_entity_UsuarioConquista.xml','2026-06-23 20:08:03',22,'EXECUTED','9:5ca4aa3084f1cbb8ab7c73e7640124a0','createTable tableName=usuario_conquista; dropDefaultValue columnName=data_conquista, tableName=usuario_conquista','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221038-1-data','jhipster','config/liquibase/changelog/20260623221038_added_entity_UsuarioConquista.xml','2026-06-23 20:08:03',23,'EXECUTED','9:4c771803b764b2f42e03236966b623e1','loadData tableName=usuario_conquista','',NULL,'4.29.2','faker',NULL,'2256081837'),('20260623221029-2','jhipster','config/liquibase/changelog/20260623221029_added_entity_constraints_Usuario.xml','2026-06-23 20:08:04',24,'EXECUTED','9:cba40c2a394b5b20238c28ee436e5e89','addForeignKeyConstraint baseTableName=rel_usuario__salas_aluno, constraintName=fk_rel_usuario__salas_aluno__usuario_id, referencedTableName=usuario; addForeignKeyConstraint baseTableName=rel_usuario__salas_aluno, constraintName=fk_rel_usuario__sal...','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221030-2','jhipster','config/liquibase/changelog/20260623221030_added_entity_constraints_Sala.xml','2026-06-23 20:08:04',25,'EXECUTED','9:7ead69c9a80f7d4fe152cc4e21cf68c1','addForeignKeyConstraint baseTableName=sala, constraintName=fk_sala__professor_id, referencedTableName=usuario','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221031-2','jhipster','config/liquibase/changelog/20260623221031_added_entity_constraints_ListaPalavras.xml','2026-06-23 20:08:04',26,'EXECUTED','9:6be1483fdd721fa8f9c9cf1e54189ca5','addForeignKeyConstraint baseTableName=rel_lista_palavras__palavras, constraintName=fk_rel_lista_palavras__palavras__lista_palavras_id, referencedTableName=lista_palavras; addForeignKeyConstraint baseTableName=rel_lista_palavras__palavras, constrai...','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221032-2','jhipster','config/liquibase/changelog/20260623221032_added_entity_constraints_Palavra.xml','2026-06-23 20:08:04',27,'EXECUTED','9:57157091cb908c5ed5f40f1d53d431b3','addForeignKeyConstraint baseTableName=palavra, constraintName=fk_palavra__criador_id, referencedTableName=usuario','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221033-2','jhipster','config/liquibase/changelog/20260623221033_added_entity_constraints_Atividade.xml','2026-06-23 20:08:05',28,'EXECUTED','9:b036220f34aa927b85477c8cf0c1bab8','addForeignKeyConstraint baseTableName=atividade, constraintName=fk_atividade__sala_id, referencedTableName=sala; addForeignKeyConstraint baseTableName=atividade, constraintName=fk_atividade__lista_id, referencedTableName=lista_palavras','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221034-2','jhipster','config/liquibase/changelog/20260623221034_added_entity_constraints_Resposta.xml','2026-06-23 20:08:05',29,'EXECUTED','9:b8a686bfd4add2bad73ce54a157bb15d','addForeignKeyConstraint baseTableName=resposta, constraintName=fk_resposta__atividade_id, referencedTableName=atividade; addForeignKeyConstraint baseTableName=resposta, constraintName=fk_resposta__aluno_id, referencedTableName=usuario; addForeignK...','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221035-2','jhipster','config/liquibase/changelog/20260623221035_added_entity_constraints_ErroOrtografico.xml','2026-06-23 20:08:05',30,'EXECUTED','9:fa0afb640d77b2b6dc67a044bed0b706','addForeignKeyConstraint baseTableName=erro_ortografico, constraintName=fk_erro_ortografico__resposta_id, referencedTableName=resposta','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221036-2','jhipster','config/liquibase/changelog/20260623221036_added_entity_constraints_Ranking.xml','2026-06-23 20:08:05',31,'EXECUTED','9:30ae5f67a8994f1f9a2cc7b011cf1b54','addForeignKeyConstraint baseTableName=ranking, constraintName=fk_ranking__sala_id, referencedTableName=sala; addForeignKeyConstraint baseTableName=ranking, constraintName=fk_ranking__aluno_id, referencedTableName=usuario','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260623221038-2','jhipster','config/liquibase/changelog/20260623221038_added_entity_constraints_UsuarioConquista.xml','2026-06-23 20:08:06',32,'EXECUTED','9:3f32986f448467e8751f7d9ea1340280','addForeignKeyConstraint baseTableName=usuario_conquista, constraintName=fk_usuario_conquista__aluno_id, referencedTableName=usuario; addForeignKeyConstraint baseTableName=usuario_conquista, constraintName=fk_usuario_conquista__conquista_id, refere...','',NULL,'4.29.2',NULL,NULL,'2256081837'),('20260702120000-1','digitado','config/liquibase/changelog/20260702120000_seed_conquistas.xml','2026-07-02 21:45:25',33,'EXECUTED','9:b3b5456266996ddec7e826e3352948c1','delete tableName=usuario_conquista; delete tableName=conquista; loadData tableName=conquista','',NULL,'4.29.2',NULL,NULL,'3039525372'),('20260703120000-1','digitado','config/liquibase/changelog/20260703120000_add_palavra_do_dia.xml','2026-07-04 00:44:38',34,'EXECUTED','9:7ee6db6161657fa6b0c78305109114bf','addColumn tableName=palavra','',NULL,'4.29.2',NULL,NULL,'3136678667'),('20260703120000-2','digitado','config/liquibase/changelog/20260703120000_add_palavra_do_dia.xml','2026-07-04 00:44:38',35,'EXECUTED','9:ca9bbfc3e995b1c86c5725da93aeade8','createTable tableName=palavra_do_dia_tentativa; addForeignKeyConstraint baseTableName=palavra_do_dia_tentativa, constraintName=fk_pdd_tentativa_palavra, referencedTableName=palavra; addUniqueConstraint constraintName=ux_pdd_tentativa_data_login, t...','',NULL,'4.29.2',NULL,NULL,'3136678667'),('20260704120000-1','digitado','config/liquibase/changelog/20260704120000_remove_dificuldade_palavra.xml','2026-07-06 01:27:21',36,'EXECUTED','9:aa5b6214479c3bda6b5080d30b801e3b','dropColumn columnName=dificuldade, tableName=palavra','',NULL,'4.29.2',NULL,NULL,'3312041485'),('20260706120000-1','digitado','config/liquibase/changelog/20260706120000_add_data_criacao_sala.xml','2026-07-06 01:27:21',37,'EXECUTED','9:e3dcade7f2fd849acb4d3c09a5a4a2e2','addColumn tableName=sala','',NULL,'4.29.2',NULL,NULL,'3312041485'),('20260706150000-1','digitado','config/liquibase/changelog/20260706150000_usuario_sobrenome_opcional.xml','2026-07-06 22:36:41',38,'EXECUTED','9:aa85b1317d6fdcdcf8523ba6449a6f0d','dropNotNullConstraint columnName=sobrenome, tableName=usuario','',NULL,'4.29.2',NULL,NULL,'3388201679'),('20260707120000-1','digitado','config/liquibase/changelog/20260707120000_add_xp_usuario.xml','2026-07-07 16:26:14',39,'EXECUTED','9:634caf30bf79a2f90bff8bfd46abde81','addColumn tableName=usuario','',NULL,'4.29.2',NULL,NULL,'3452374833'),('20260708090000-1','digitado','config/liquibase/changelog/20260708090000_sala_pk_codigo.xml','2026-07-08 21:49:06',40,'EXECUTED','9:bcb5f0e346b3c847ebd13c811a43b0c3','dropForeignKeyConstraint baseTableName=atividade, constraintName=fk_atividade__sala_id; dropForeignKeyConstraint baseTableName=ranking, constraintName=fk_ranking__sala_id; dropForeignKeyConstraint baseTableName=rel_usuario__salas_aluno, constraint...','',NULL,'4.29.2',NULL,NULL,'3558145816'),('20260708090000-2','digitado','config/liquibase/changelog/20260708090000_sala_pk_codigo.xml','2026-07-08 21:49:06',41,'EXECUTED','9:edaf9b849d14d2e33fc48df7b6d1b8f3','addColumn tableName=atividade; addColumn tableName=ranking; addColumn tableName=rel_usuario__salas_aluno; sql; sql; sql; dropColumn columnName=sala_id, tableName=atividade; dropColumn columnName=sala_id, tableName=ranking','',NULL,'4.29.2',NULL,NULL,'3558145816'),('20260708090000-3','digitado','config/liquibase/changelog/20260708090000_sala_pk_codigo.xml','2026-07-08 21:57:15',42,'EXECUTED','9:960264ca55874828a904c79f965ebbd6','dropForeignKeyConstraint baseTableName=rel_usuario__salas_aluno, constraintName=fk_rel_usuario__salas_aluno__usuario_id; dropPrimaryKey tableName=rel_usuario__salas_aluno; dropColumn columnName=salas_aluno_id, tableName=rel_usuario__salas_aluno; a...','',NULL,'4.29.2',NULL,NULL,'3558634846'),('20260708090000-4','digitado','config/liquibase/changelog/20260708090000_sala_pk_codigo.xml','2026-07-08 21:57:15',43,'EXECUTED','9:620264078a9173b6533a88835bfc1f65','sql; sql; dropColumn columnName=id, tableName=sala; addPrimaryKey constraintName=pk_sala, tableName=sala','',NULL,'4.29.2',NULL,NULL,'3558634846'),('20260708090000-5','digitado','config/liquibase/changelog/20260708090000_sala_pk_codigo.xml','2026-07-08 21:57:16',44,'EXECUTED','9:6b1ed24d16a4fbce5e83a10997f29122','addForeignKeyConstraint baseTableName=atividade, constraintName=fk_atividade__sala_codigo, referencedTableName=sala; addForeignKeyConstraint baseTableName=ranking, constraintName=fk_ranking__sala_codigo, referencedTableName=sala; addForeignKeyCons...','',NULL,'4.29.2',NULL,NULL,'3558634846'),('20260708150000-1','digitado','config/liquibase/changelog/20260708150000_sala_1v1_conquistas.xml','2026-07-08 23:16:17',45,'EXECUTED','9:eeef70a99a7102d98ed9e8171f3fc9ce','addColumn tableName=sala','',NULL,'4.29.2',NULL,NULL,'3563377510'),('20260708150000-2','digitado','config/liquibase/changelog/20260708150000_sala_1v1_conquistas.xml','2026-07-08 23:16:17',46,'EXECUTED','9:088683864ff1ee0dd9d8a8ebd3fbc1a0','loadData tableName=conquista','',NULL,'4.29.2',NULL,NULL,'3563377510'),('20260709120000-1','digitado','config/liquibase/changelog/20260709120000_sala_descricao_json.xml','2026-07-09 10:37:44',47,'EXECUTED','9:23c3423b3b33cd559200f986179b19d7','sql; modifyDataType columnName=descricao, tableName=sala','',NULL,'4.29.2',NULL,NULL,'3604264034'),('20260709150000-1','digitado','config/liquibase/changelog/20260709150000_palavra_dica.xml','2026-07-09 11:17:51',48,'MARK_RAN','9:1432e5a58b84367468e12cae7709d6c7','addColumn tableName=palavra','',NULL,'4.29.2',NULL,NULL,'3606671858'),('20260710120000-1','digitado','config/liquibase/changelog/20260710120000_usuario_apelido.xml','2026-07-13 12:28:58',49,'EXECUTED','9:ed327cc5326519fd7bf3d3dbfa889b2d','addColumn tableName=usuario','',NULL,'4.29.2',NULL,NULL,'3956538449'),('20260713120000-1','digitado','config/liquibase/changelog/20260713120000_readd_dificuldade_palavra.xml','2026-07-13 12:40:57',50,'MARK_RAN','9:ab54756fce06e9f29d9a2bd39764a2e0','addColumn tableName=palavra','',NULL,'4.29.2',NULL,NULL,'3957257306'),('20260713150000-1','digitado','config/liquibase/changelog/20260713150000_normaliza_dificuldade_palavra.xml','2026-07-13 12:40:57',51,'EXECUTED','9:40f695ccab06484c30989b09d722eef0','sql','',NULL,'4.29.2',NULL,NULL,'3957257306'),('20260714120000-1','digitado','config/liquibase/changelog/20260714120000_indices_performance.xml','2026-07-15 16:02:19',52,'EXECUTED','9:2fc66e27d3fde2aad2c2d7b09d682b3b','sql','',NULL,'4.29.2',NULL,NULL,'4142138972'),('20260714120000-2','digitado','config/liquibase/changelog/20260714120000_indices_performance.xml','2026-07-15 16:02:19',53,'EXECUTED','9:667c8bfdfef3996d3fdce5f0fd31525c','createIndex indexName=ix_usuario_xp, tableName=usuario','',NULL,'4.29.2',NULL,NULL,'4142138972'),('20260714120000-3','digitado','config/liquibase/changelog/20260714120000_indices_performance.xml','2026-07-15 16:02:19',54,'EXECUTED','9:eb849dab94127d978554a845b10b141f','createIndex indexName=ix_palavra_texto, tableName=palavra','',NULL,'4.29.2',NULL,NULL,'4142138972'),('20260810120000-1','digitado','config/liquibase/changelog/20260810120000_senha_admin.xml','2026-08-14 19:31:08',55,'EXECUTED','9:de6e2314412f131deb305193454bcc57','update tableName=jhi_user','',NULL,'4.29.2',NULL,NULL,'6735868457');
/*!40000 ALTER TABLE `databasechangelog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `databasechangeloglock`
--

DROP TABLE IF EXISTS `databasechangeloglock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `databasechangeloglock` (
  `ID` int NOT NULL,
  `LOCKED` tinyint NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `databasechangeloglock`
--

LOCK TABLES `databasechangeloglock` WRITE;
/*!40000 ALTER TABLE `databasechangeloglock` DISABLE KEYS */;
INSERT INTO `databasechangeloglock` VALUES (1,0,NULL,NULL);
/*!40000 ALTER TABLE `databasechangeloglock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `erro_ortografico`
--

DROP TABLE IF EXISTS `erro_ortografico`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `erro_ortografico` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tipo_erro` varchar(255) DEFAULT NULL,
  `descricao` longtext,
  `resposta_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_erro_ortografico__resposta_id` (`resposta_id`),
  CONSTRAINT `fk_erro_ortografico__resposta_id` FOREIGN KEY (`resposta_id`) REFERENCES `resposta` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `erro_ortografico`
--

LOCK TABLES `erro_ortografico` WRITE;
/*!40000 ALTER TABLE `erro_ortografico` DISABLE KEYS */;
/*!40000 ALTER TABLE `erro_ortografico` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_authority`
--

DROP TABLE IF EXISTS `jhi_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jhi_authority` (
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_authority`
--

LOCK TABLES `jhi_authority` WRITE;
/*!40000 ALTER TABLE `jhi_authority` DISABLE KEYS */;
INSERT INTO `jhi_authority` VALUES ('ROLE_ADMIN'),('ROLE_USER');
/*!40000 ALTER TABLE `jhi_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user`
--

DROP TABLE IF EXISTS `jhi_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jhi_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `login` varchar(50) NOT NULL,
  `password_hash` varchar(60) NOT NULL,
  `first_name` varchar(50) DEFAULT NULL,
  `last_name` varchar(50) DEFAULT NULL,
  `email` varchar(191) DEFAULT NULL,
  `image_url` varchar(256) DEFAULT NULL,
  `activated` tinyint NOT NULL,
  `lang_key` varchar(10) DEFAULT NULL,
  `activation_key` varchar(20) DEFAULT NULL,
  `reset_key` varchar(20) DEFAULT NULL,
  `created_by` varchar(50) NOT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_user_login` (`login`),
  UNIQUE KEY `ux_user_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=1060 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user`
--

LOCK TABLES `jhi_user` WRITE;
/*!40000 ALTER TABLE `jhi_user` DISABLE KEYS */;
INSERT INTO `jhi_user` VALUES (1,'admin','$2a$10$U48jnw6Wm85UAyKF1uHSSODUofaC/KtDWnHBPcuS8e.cZKuQx8pyW','Administrator','Administrator','admin@localhost','',1,'pt-br',NULL,NULL,'system',NULL,NULL,'system',NULL),(2,'user','$2a$10$VEjxo0jq2YG9Rbk2HmX9S.k1uZBGYUHdUcid3g/vfiEl7lwWgOH/K','User','User','user@localhost','',1,'pt-br',NULL,NULL,'system',NULL,NULL,'system',NULL);
/*!40000 ALTER TABLE `jhi_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user_authority`
--

DROP TABLE IF EXISTS `jhi_user_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jhi_user_authority` (
  `user_id` bigint NOT NULL,
  `authority_name` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`,`authority_name`),
  KEY `fk_authority_name` (`authority_name`),
  CONSTRAINT `fk_authority_name` FOREIGN KEY (`authority_name`) REFERENCES `jhi_authority` (`name`),
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user_authority`
--

LOCK TABLES `jhi_user_authority` WRITE;
/*!40000 ALTER TABLE `jhi_user_authority` DISABLE KEYS */;
INSERT INTO `jhi_user_authority` VALUES (1,'ROLE_ADMIN'),(1,'ROLE_USER'),(2,'ROLE_USER');
/*!40000 ALTER TABLE `jhi_user_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lista_palavras`
--

DROP TABLE IF EXISTS `lista_palavras`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lista_palavras` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nome_lista` varchar(255) NOT NULL,
  `descricao` longtext,
  `ativo` tinyint DEFAULT NULL,
  `professor_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lista_palavras__professor_id` (`professor_id`),
  CONSTRAINT `fk_lista_palavras__professor_id` FOREIGN KEY (`professor_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lista_palavras`
--

LOCK TABLES `lista_palavras` WRITE;
/*!40000 ALTER TABLE `lista_palavras` DISABLE KEYS */;
INSERT INTO `lista_palavras` VALUES (1,'gadzooks qua','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',1,NULL),(2,'yum emotional monocle','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(3,'quantify schlep treasure','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(4,'brood wonderfully permafrost','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',1,NULL),(5,'scar','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(6,'kindheartedly nightlife','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(7,'pro every trial','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(8,'yawn forenenst','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(9,'toe fooey hover','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL),(10,'filter topsail','JHipster is a development platform to generate, develop and deploy Spring Boot + Angular / React / Vue Web applications and Spring microservices.',0,NULL);
/*!40000 ALTER TABLE `lista_palavras` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `palavra`
--

DROP TABLE IF EXISTS `palavra`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `palavra` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `texto` varchar(255) NOT NULL,
  `categoria` varchar(255) DEFAULT NULL,
  `idioma` varchar(255) DEFAULT NULL,
  `possui_acento` tinyint DEFAULT NULL,
  `ativa` tinyint DEFAULT NULL,
  `criador_id` bigint DEFAULT NULL,
  `total_tentativas` bigint NOT NULL DEFAULT '0',
  `total_acertos` bigint NOT NULL DEFAULT '0',
  `dica` varchar(100) DEFAULT NULL,
  `dificuldade` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_palavra__criador_id` (`criador_id`),
  KEY `ix_palavra_texto` (`texto`),
  CONSTRAINT `fk_palavra__criador_id` FOREIGN KEY (`criador_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1806 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `palavra`
--

LOCK TABLES `palavra` WRITE;
/*!40000 ALTER TABLE `palavra` DISABLE KEYS */;
INSERT INTO `palavra` VALUES (1500,'casa','SUBSTANTIVO','PT',0,1,NULL,1,1,'Lugar onde as pessoas moram.','FACIL'),(1501,'bola','SUBSTANTIVO','PT',0,1,NULL,0,0,'Objeto redondo usado em esportes e brincadeiras.','FACIL'),(1502,'gato','ANIMAL','PT',0,1,NULL,0,0,'Animal doméstico conhecido por miar.','FACIL'),(1503,'cão','ANIMAL','PT',1,1,NULL,0,0,'Animal doméstico considerado o melhor amigo do homem.','FACIL'),(1504,'mesa','SUBSTANTIVO','PT',0,1,NULL,0,0,'Móvel usado para refeições ou trabalho.','FACIL'),(1505,'cadeira','SUBSTANTIVO','PT',0,1,NULL,0,0,'Móvel utilizado para sentar.','FACIL'),(1506,'porta','SUBSTANTIVO','PT',0,1,NULL,2,0,'Entrada ou saída de um ambiente.','FACIL'),(1507,'janela','SUBSTANTIVO','PT',0,1,NULL,1,1,'Abertura que permite entrada de luz e ventilação.','FACIL'),(1508,'livro','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto de páginas com histórias ou informações.','FACIL'),(1509,'lápis','SUBSTANTIVO','PT',1,1,NULL,1,1,'Material usado para escrever ou desenhar.','FACIL'),(1510,'caneta','SUBSTANTIVO','PT',0,1,NULL,0,0,'Objeto utilizado para escrever com tinta.','FACIL'),(1511,'papel','SUBSTANTIVO','PT',0,1,NULL,0,0,'Material usado para escrever, desenhar ou imprimir.','FACIL'),(1512,'carro','SUBSTANTIVO','PT',0,1,NULL,1,1,'Veículo utilizado para transporte.','FACIL'),(1513,'ônibus','SUBSTANTIVO','PT',1,1,NULL,0,0,'Transporte coletivo de passageiros.','FACIL'),(1514,'moto','SUBSTANTIVO','PT',0,1,NULL,2,0,'Veículo de duas rodas motorizado.','FACIL'),(1515,'avião','SUBSTANTIVO','PT',1,1,NULL,0,0,'Meio de transporte que voa.','FACIL'),(1516,'rio','NATUREZA','PT',0,1,NULL,0,0,'Curso natural de água doce.','FACIL'),(1517,'mar','NATUREZA','PT',0,1,NULL,0,0,'Grande extensão de água salgada.','FACIL'),(1518,'sol','NATUREZA','PT',0,1,NULL,0,0,'Estrela que ilumina a Terra.','FACIL'),(1519,'lua','NATUREZA','PT',0,1,NULL,0,0,'Satélite natural da Terra.','FACIL'),(1520,'flor','NATUREZA','PT',0,1,NULL,2,2,'Parte colorida de muitas plantas.','FACIL'),(1521,'árvore','NATUREZA','PT',1,1,NULL,0,0,'Planta de grande porte com tronco.','FACIL'),(1522,'fruta','SUBSTANTIVO','PT',0,1,NULL,0,0,'Alimento produzido por plantas.','FACIL'),(1523,'maçã','SUBSTANTIVO','PT',1,1,NULL,0,0,'Fruta muito associada à cor vermelha.','FACIL'),(1524,'banana','SUBSTANTIVO','PT',0,1,NULL,0,0,'Fruta amarela e alongada.','FACIL'),(1525,'uva','SUBSTANTIVO','PT',0,1,NULL,0,0,'Fruta pequena que cresce em cachos.','FACIL'),(1526,'pera','SUBSTANTIVO','PT',0,1,NULL,0,0,'Fruta doce de formato arredondado.','FACIL'),(1527,'leite','SUBSTANTIVO','PT',0,1,NULL,0,0,'Bebida produzida por mamíferos.','FACIL'),(1528,'queijo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Alimento derivado do leite.','FACIL'),(1529,'pão','SUBSTANTIVO','PT',1,1,NULL,0,0,'Alimento comum no café da manhã.','FACIL'),(1530,'ovo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Alimento produzido por aves.','FACIL'),(1531,'peixe','ANIMAL','PT',0,1,NULL,0,0,'Animal que vive na água.','FACIL'),(1532,'pato','ANIMAL','PT',0,1,NULL,0,0,'Ave que gosta de nadar.','FACIL'),(1533,'vaca','ANIMAL','PT',0,1,NULL,0,0,'Animal criado para produção de leite.','FACIL'),(1534,'cavalo','ANIMAL','PT',0,1,NULL,0,0,'Animal muito usado para montaria.','FACIL'),(1535,'ovelha','ANIMAL','PT',0,1,NULL,1,1,'Animal conhecido por produzir lã.','FACIL'),(1536,'rato','ANIMAL','PT',0,1,NULL,0,0,'Pequeno roedor.','FACIL'),(1537,'urso','ANIMAL','PT',0,1,NULL,0,0,'Mamífero grande encontrado em regiões frias e florestas.','FACIL'),(1538,'tigre','ANIMAL','PT',0,1,NULL,0,0,'Grande felino com listras.','FACIL'),(1539,'leão','ANIMAL','PT',1,1,NULL,2,2,'Felino conhecido como rei da selva.','FACIL'),(1540,'sapato','SUBSTANTIVO','PT',0,1,NULL,0,0,'Calçado usado nos pés.','FACIL'),(1541,'camisa','SUBSTANTIVO','PT',0,1,NULL,1,1,'Peça de roupa para a parte superior do corpo.','FACIL'),(1542,'calça','SUBSTANTIVO','PT',1,1,NULL,0,0,'Peça de roupa usada nas pernas.','FACIL'),(1543,'meia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Vestuário usado dentro do calçado.','FACIL'),(1544,'boné','SUBSTANTIVO','PT',1,1,NULL,0,0,'Acessório usado na cabeça.','FACIL'),(1545,'anel','SUBSTANTIVO','PT',0,1,NULL,1,0,'Acessório usado nos dedos.','FACIL'),(1546,'relógio','SUBSTANTIVO','PT',1,1,NULL,0,0,'Objeto que mostra as horas.','FACIL'),(1547,'telefone','SUBSTANTIVO','PT',0,1,NULL,0,0,'Aparelho utilizado para comunicação.','FACIL'),(1548,'tablet','SUBSTANTIVO','PT',0,1,NULL,0,0,'Dispositivo eletrônico com tela sensível ao toque.','FACIL'),(1549,'mouse','SUBSTANTIVO','PT',0,1,NULL,0,0,'Periférico usado para controlar o computador.','FACIL'),(1550,'teclado','SUBSTANTIVO','PT',0,1,NULL,0,0,'Periférico utilizado para digitar.','FACIL'),(1551,'monitor','SUBSTANTIVO','PT',0,1,NULL,0,0,'Tela de exibição do computador.','FACIL'),(1552,'escola','SUBSTANTIVO','PT',0,1,NULL,0,0,'Local destinado ao ensino.','FACIL'),(1553,'igreja','SUBSTANTIVO','PT',0,1,NULL,0,0,'Local de celebrações religiosas.','FACIL'),(1554,'praça','SUBSTANTIVO','PT',1,1,NULL,0,0,'Espaço público para lazer.','FACIL'),(1555,'parque','SUBSTANTIVO','PT',0,1,NULL,2,2,'Área destinada ao lazer e à natureza.','FACIL'),(1556,'mercado','SUBSTANTIVO','PT',0,1,NULL,0,0,'Local onde se compram alimentos e produtos.','FACIL'),(1557,'hospital','SUBSTANTIVO','PT',0,1,NULL,0,0,'Local de atendimento médico.','FACIL'),(1558,'farmácia','SUBSTANTIVO','PT',1,1,NULL,0,0,'Estabelecimento onde se compram medicamentos.','FACIL'),(1559,'cidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Área urbana com muitos habitantes.','FACIL'),(1560,'bairro','SUBSTANTIVO','PT',0,1,NULL,0,0,'Divisão de uma cidade.','FACIL'),(1561,'rua','SUBSTANTIVO','PT',0,1,NULL,0,0,'Via pública para circulação.','FACIL'),(1562,'amigo','NOME','PT',0,1,NULL,0,0,'Pessoa com quem se tem amizade.','FACIL'),(1563,'família','NOME','PT',1,1,NULL,0,0,'Grupo de pessoas ligadas por parentesco.','FACIL'),(1564,'mãe','NOME','PT',1,1,NULL,0,0,'Mulher que deu à luz ou cria um filho.','FACIL'),(1565,'pai','NOME','PT',0,1,NULL,0,0,'Homem que é responsável por um filho.','FACIL'),(1566,'irmão','NOME','PT',1,1,NULL,0,0,'Filho dos mesmos pais.','FACIL'),(1567,'irmã','NOME','PT',1,1,NULL,2,2,'Filha dos mesmos pais.','FACIL'),(1568,'bebê','NOME','PT',1,1,NULL,0,0,'Criança nos primeiros meses ou anos de vida.','FACIL'),(1569,'criança','NOME','PT',1,1,NULL,0,0,'Pessoa na fase inicial da vida.','FACIL'),(1570,'moço','NOME','PT',1,1,NULL,0,0,'Homem jovem.','FACIL'),(1571,'moça','NOME','PT',1,1,NULL,0,0,'Mulher jovem.','FACIL'),(1572,'água','SUBSTANTIVO','PT',1,1,NULL,0,0,'Líquido essencial para a vida.','FACIL'),(1573,'suco','SUBSTANTIVO','PT',0,1,NULL,0,0,'Bebida feita a partir de frutas.','FACIL'),(1574,'café','SUBSTANTIVO','PT',1,1,NULL,2,2,'Bebida feita a partir de grãos torrados.','FACIL'),(1575,'bolo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Doce assado muito servido em festas.','FACIL'),(1576,'doce','SUBSTANTIVO','PT',0,1,NULL,0,0,'Alimento com sabor açucarado.','FACIL'),(1577,'sopa','SUBSTANTIVO','PT',0,1,NULL,0,0,'Prato preparado com caldo e ingredientes cozidos.','FACIL'),(1578,'arroz','SUBSTANTIVO','PT',0,1,NULL,0,0,'Alimento básico presente em muitas refeições.','FACIL'),(1579,'feijão','SUBSTANTIVO','PT',1,1,NULL,0,0,'Grão muito consumido no Brasil.','FACIL'),(1580,'chuva','NATUREZA','PT',0,1,NULL,0,0,'Água que cai das nuvens.','FACIL'),(1581,'vento','NATUREZA','PT',0,1,NULL,0,0,'Movimento natural do ar.','FACIL'),(1582,'nuvem','NATUREZA','PT',0,1,NULL,0,0,'Conjunto de gotículas de água no céu.','FACIL'),(1583,'pedra','NATUREZA','PT',0,1,NULL,2,2,'Material sólido encontrado na natureza.','FACIL'),(1584,'areia','NATUREZA','PT',0,1,NULL,0,0,'Grãos finos encontrados em praias e desertos.','FACIL'),(1585,'campo','NATUREZA','PT',0,1,NULL,0,0,'Área aberta, geralmente com vegetação.','FACIL'),(1586,'praia','NATUREZA','PT',0,1,NULL,0,0,'Faixa de areia à beira do mar.','FACIL'),(1587,'montanha','NATUREZA','PT',0,1,NULL,1,0,'Grande elevação natural do terreno.','FACIL'),(1588,'verde','COR','PT',0,1,NULL,1,0,'Cor associada à natureza.','FACIL'),(1589,'azul','COR','PT',0,1,NULL,0,0,'Cor do céu em dias claros.','FACIL'),(1590,'preto','COR','PT',0,1,NULL,0,0,'Cor da ausência de luz.','FACIL'),(1591,'branco','COR','PT',0,1,NULL,0,0,'Cor associada à paz e à neve.','FACIL'),(1592,'rosa','COR','PT',0,1,NULL,0,0,'Cor também associada a uma flor.','FACIL'),(1593,'amarelo','COR','PT',0,1,NULL,0,0,'Cor do sol e de muitas frutas.','FACIL'),(1594,'roxo','COR','PT',0,1,NULL,0,0,'Cor entre o azul e o vermelho.','FACIL'),(1595,'cinza','COR','PT',0,1,NULL,0,0,'Cor semelhante à das nuvens em dias chuvosos.','FACIL'),(1596,'laranja','COR','PT',0,1,NULL,2,1,'Cor e também nome de uma fruta.','FACIL'),(1597,'coração','SUBSTANTIVO','PT',1,1,NULL,0,0,'Órgão que bombeia o sangue.','FACIL'),(1598,'ação','VERBO','PT',1,1,NULL,0,0,'Algo que é feito ou realizado.','FACIL'),(1599,'lição','SUBSTANTIVO','PT',1,1,NULL,0,0,'Conteúdo aprendido em uma aula.','FACIL'),(1600,'emoção','SUBSTANTIVO','PT',1,1,NULL,0,0,'Sentimento intenso.','MEDIO'),(1601,'abacaxi','SUBSTANTIVO','PT',0,1,NULL,0,0,'Fruta tropical com casca espinhosa.','MEDIO'),(1602,'helicóptero','SUBSTANTIVO','PT',1,1,NULL,0,0,'Aeronave com hélices.','MEDIO'),(1603,'biblioteca','SUBSTANTIVO','PT',0,1,NULL,1,1,'Local onde livros são organizados para leitura e empréstimo.','MEDIO'),(1604,'computador','SUBSTANTIVO','PT',0,1,NULL,0,0,'Máquina eletrônica usada para processar informações.','MEDIO'),(1605,'universidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Instituição de ensino superior.','MEDIO'),(1606,'professor','NOME','PT',0,1,NULL,0,0,'Profissional responsável por ensinar.','MEDIO'),(1607,'engenheiro','NOME','PT',0,1,NULL,1,1,'Profissional que projeta e desenvolve soluções técnicas.','MEDIO'),(1608,'dentista','NOME','PT',0,1,NULL,0,0,'Profissional especializado na saúde bucal.','MEDIO'),(1609,'advogado','NOME','PT',0,1,NULL,0,0,'Profissional que atua na defesa de direitos.','MEDIO'),(1610,'arquiteto','NOME','PT',0,1,NULL,2,0,'Profissional que projeta construções.','MEDIO'),(1611,'astronauta','NOME','PT',0,1,NULL,1,1,'Pessoa treinada para viajar ao espaço.','MEDIO'),(1612,'camaleão','ANIMAL','PT',1,1,NULL,0,0,'Réptil famoso por mudar de cor.','MEDIO'),(1613,'jacaré','ANIMAL','PT',1,1,NULL,0,0,'Réptil de grande porte encontrado em rios.','MEDIO'),(1614,'borboleta','ANIMAL','PT',0,1,NULL,0,0,'Inseto conhecido por suas asas coloridas.','MEDIO'),(1615,'crocodilo','ANIMAL','PT',0,1,NULL,0,0,'Grande réptil semelhante ao jacaré.','MEDIO'),(1616,'tartaruga','ANIMAL','PT',0,1,NULL,0,0,'Animal protegido por um casco.','MEDIO'),(1617,'girafa','ANIMAL','PT',0,1,NULL,0,0,'Animal conhecido pelo pescoço comprido.','MEDIO'),(1618,'elefante','ANIMAL','PT',0,1,NULL,0,0,'Maior mamífero terrestre.','MEDIO'),(1619,'hipopótamo','ANIMAL','PT',1,1,NULL,0,0,'Grande mamífero que vive próximo à água.','MEDIO'),(1620,'rinoceronte','ANIMAL','PT',0,1,NULL,0,0,'Mamífero de grande porte com chifre.','MEDIO'),(1621,'macaco','ANIMAL','PT',0,1,NULL,0,0,'Primata conhecido por escalar árvores.','MEDIO'),(1622,'papagaio','ANIMAL','PT',0,1,NULL,0,0,'Ave capaz de imitar sons e palavras.','MEDIO'),(1623,'pinguim','ANIMAL','PT',0,1,NULL,0,0,'Ave que não voa e vive em regiões frias.','MEDIO'),(1624,'golfinho','ANIMAL','PT',0,1,NULL,0,0,'Mamífero marinho muito inteligente.','MEDIO'),(1625,'tubarão','ANIMAL','PT',1,1,NULL,1,1,'Grande peixe predador.','MEDIO'),(1626,'abelha','ANIMAL','PT',0,1,NULL,0,0,'Inseto responsável pela produção de mel.','MEDIO'),(1627,'formiga','ANIMAL','PT',0,1,NULL,0,0,'Inseto conhecido pelo trabalho em equipe.','MEDIO'),(1628,'escorpião','ANIMAL','PT',1,1,NULL,0,0,'Aracnídeo que possui ferrão.','MEDIO'),(1629,'lagarto','ANIMAL','PT',0,1,NULL,0,0,'Réptil de pequeno porte.','MEDIO'),(1630,'camelo','ANIMAL','PT',0,1,NULL,0,0,'Animal adaptado aos desertos.','MEDIO'),(1631,'deserto','NATUREZA','PT',0,1,NULL,0,0,'Região com pouca chuva e vegetação.','MEDIO'),(1632,'floresta','NATUREZA','PT',0,1,NULL,0,0,'Grande área coberta por árvores.','MEDIO'),(1633,'cachoeira','NATUREZA','PT',0,1,NULL,0,0,'Queda natural de água.','MEDIO'),(1634,'vulcão','NATUREZA','PT',1,1,NULL,0,0,'Montanha que pode expelir lava.','MEDIO'),(1635,'ilha','NATUREZA','PT',0,1,NULL,0,0,'Porção de terra cercada por água.','MEDIO'),(1636,'oceano','NATUREZA','PT',0,1,NULL,0,0,'Maior extensão de água salgada do planeta.','MEDIO'),(1637,'tempestade','NATUREZA','PT',0,1,NULL,0,0,'Fenômeno climático com chuva e ventos fortes.','MEDIO'),(1638,'relâmpago','NATUREZA','PT',1,1,NULL,0,0,'Descarga elétrica visível no céu.','MEDIO'),(1639,'neblina','NATUREZA','PT',0,1,NULL,0,0,'Fenômeno que reduz a visibilidade.','MEDIO'),(1640,'planície','NATUREZA','PT',1,1,NULL,0,0,'Terreno extenso e pouco elevado.','MEDIO'),(1641,'montanhista','NOME','PT',0,1,NULL,0,0,'Pessoa que pratica escalada em montanhas.','MEDIO'),(1642,'explorador','NOME','PT',0,1,NULL,0,0,'Pessoa que desbrava lugares desconhecidos.','MEDIO'),(1643,'fotógrafo','NOME','PT',1,1,NULL,2,0,'Profissional que registra imagens.','MEDIO'),(1644,'jornalista','NOME','PT',0,1,NULL,0,0,'Profissional que produz notícias.','MEDIO'),(1645,'veterinário','NOME','PT',1,1,NULL,1,0,'Profissional que cuida da saúde dos animais.','MEDIO'),(1646,'médico','NOME','PT',1,1,NULL,0,0,'Profissional responsável por cuidar da saúde das pessoas.','MEDIO'),(1647,'enfermeiro','NOME','PT',0,1,NULL,0,0,'Profissional que auxilia nos cuidados médicos.','MEDIO'),(1648,'cozinheiro','NOME','PT',0,1,NULL,1,1,'Profissional que prepara alimentos.','MEDIO'),(1649,'policial','NOME','PT',0,1,NULL,2,2,'Profissional responsável pela segurança pública.','MEDIO'),(1650,'bombeiro','NOME','PT',0,1,NULL,0,0,'Profissional que combate incêndios e realiza resgates.','MEDIO'),(1651,'motorista','NOME','PT',0,1,NULL,0,0,'Pessoa que conduz veículos.','MEDIO'),(1652,'programador','NOME','PT',0,1,NULL,1,1,'Profissional que desenvolve softwares.','MEDIO'),(1653,'tecnologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto de conhecimentos aplicados para criar soluções.','MEDIO'),(1654,'software','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto de programas de um computador.','MEDIO'),(1655,'hardware','SUBSTANTIVO','PT',0,1,NULL,0,0,'Parte física de um equipamento eletrônico.','MEDIO'),(1656,'internet','SUBSTANTIVO','PT',0,1,NULL,0,0,'Rede mundial de computadores.','MEDIO'),(1657,'celular','SUBSTANTIVO','PT',0,1,NULL,0,0,'Telefone portátil.','MEDIO'),(1658,'tablet','SUBSTANTIVO','PT',0,0,NULL,0,0,'Dispositivo eletrônico com tela sensível ao toque.','MEDIO'),(1659,'aplicativo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Programa desenvolvido para realizar tarefas específicas.','MEDIO'),(1660,'programa','SUBSTANTIVO','PT',0,1,NULL,2,2,'Conjunto de instruções executadas por um computador.','MEDIO'),(1661,'arquivo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Documento ou conjunto de dados armazenado.','MEDIO'),(1662,'sistema','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto de elementos que funcionam em conjunto.','MEDIO'),(1663,'energia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Capacidade de realizar trabalho ou movimento.','MEDIO'),(1664,'eletricidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Forma de energia utilizada para alimentar aparelhos.','MEDIO'),(1665,'vento','NATUREZA','PT',0,0,NULL,0,0,'Movimento natural do ar.','MEDIO'),(1666,'chuva','NATUREZA','PT',0,0,NULL,0,0,'Água que cai das nuvens.','MEDIO'),(1667,'neve','NATUREZA','PT',0,1,NULL,1,0,'Precipitação formada por cristais de gelo.','MEDIO'),(1668,'granizo','NATUREZA','PT',0,1,NULL,0,0,'Precipitação em forma de pedras de gelo.','MEDIO'),(1669,'clima','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto das condições atmosféricas de uma região.','MEDIO'),(1670,'temperatura','SUBSTANTIVO','PT',0,1,NULL,0,0,'Medida do calor ou frio.','MEDIO'),(1671,'estação','SUBSTANTIVO','PT',1,1,NULL,0,0,'Cada uma das quatro divisões do ano.','MEDIO'),(1672,'primavera','NATUREZA','PT',0,1,NULL,0,0,'Estação conhecida pelo florescimento das plantas.','MEDIO'),(1673,'verão','NATUREZA','PT',1,1,NULL,0,0,'Estação mais quente do ano.','MEDIO'),(1674,'outono','NATUREZA','PT',0,1,NULL,3,3,'Estação marcada pela queda das folhas.','MEDIO'),(1675,'inverno','NATUREZA','PT',0,1,NULL,1,1,'Estação mais fria do ano.','MEDIO'),(1676,'cidade','SUBSTANTIVO','PT',0,0,NULL,0,0,'Área urbana com muitos habitantes.','MEDIO'),(1677,'metrópole','SUBSTANTIVO','PT',1,1,NULL,1,0,'Cidade de grande porte e influência.','MEDIO'),(1678,'vilarejo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Pequena comunidade rural.','MEDIO'),(1679,'estrada','SUBSTANTIVO','PT',0,1,NULL,1,0,'Via utilizada para deslocamento entre locais.','MEDIO'),(1680,'rodovia','SUBSTANTIVO','PT',0,1,NULL,2,0,'Estrada destinada ao tráfego de veículos.','MEDIO'),(1681,'ponte','SUBSTANTIVO','PT',0,1,NULL,0,0,'Construção que permite atravessar obstáculos.','MEDIO'),(1682,'túnel','SUBSTANTIVO','PT',1,1,NULL,0,0,'Passagem construída abaixo da superfície.','MEDIO'),(1683,'aeroporto','SUBSTANTIVO','PT',0,1,NULL,0,0,'Local de pouso e decolagem de aviões.','MEDIO'),(1684,'estação','SUBSTANTIVO','PT',1,0,NULL,1,0,'Cada uma das quatro divisões do ano.','MEDIO'),(1685,'trânsito','SUBSTANTIVO','PT',1,1,NULL,1,1,'Movimento de veículos e pessoas nas vias.','MEDIO'),(1686,'viagem','SUBSTANTIVO','PT',0,1,NULL,0,0,'Deslocamento entre diferentes lugares.','MEDIO'),(1687,'aventura','SUBSTANTIVO','PT',0,1,NULL,0,0,'Experiência cheia de desafios.','MEDIO'),(1688,'descoberta','SUBSTANTIVO','PT',0,1,NULL,0,0,'Encontrar algo desconhecido.','MEDIO'),(1689,'conhecimento','SUBSTANTIVO','PT',0,1,NULL,0,0,'Conjunto de informações adquiridas.','MEDIO'),(1690,'educação','SUBSTANTIVO','PT',1,1,NULL,0,0,'Processo de ensino e aprendizagem.','MEDIO'),(1691,'ciência','SUBSTANTIVO','PT',1,1,NULL,0,0,'Área dedicada ao estudo e pesquisa.','MEDIO'),(1692,'matemática','SUBSTANTIVO','PT',1,1,NULL,0,0,'Ciência dos números e cálculos.','MEDIO'),(1693,'história','SUBSTANTIVO','PT',1,1,NULL,0,0,'Estudo dos acontecimentos do passado.','MEDIO'),(1694,'geografia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estudo da Terra e seus fenômenos.','MEDIO'),(1695,'física','SUBSTANTIVO','PT',1,1,NULL,2,2,'Ciência que estuda matéria e energia.','MEDIO'),(1696,'química','SUBSTANTIVO','PT',1,1,NULL,0,0,'Ciência que estuda as substâncias.','MEDIO'),(1697,'biologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ciência que estuda os seres vivos.','MEDIO'),(1698,'laboratório','SUBSTANTIVO','PT',1,1,NULL,0,0,'Local destinado a experimentos científicos.','MEDIO'),(1699,'experimento','SUBSTANTIVO','PT',0,1,NULL,0,0,'Teste realizado para obter resultados.','MEDIO'),(1700,'resultado','SUBSTANTIVO','PT',0,1,NULL,0,0,'Consequência obtida após uma ação.','DIFICIL'),(1701,'análise','SUBSTANTIVO','PT',1,1,NULL,2,0,'Estudo detalhado de um assunto.','DIFICIL'),(1702,'pesquisa','SUBSTANTIVO','PT',0,1,NULL,0,0,'Investigação para obter novos conhecimentos.','DIFICIL'),(1703,'otorrinolaringologista','NOME','PT',0,1,NULL,0,0,'Médico especializado em ouvido, nariz e garganta.','DIFICIL'),(1704,'inconstitucionalissimamente','ADJETIVO','PT',0,1,NULL,2,1,'De forma contrária à Constituição.','DIFICIL'),(1705,'anticonstitucionalidade','SUBSTANTIVO','PT',0,1,NULL,1,0,'Característica do que contraria a Constituição.','DIFICIL'),(1706,'eletroencefalograma','SUBSTANTIVO','PT',0,1,NULL,0,0,'Exame que registra a atividade elétrica do cérebro.','DIFICIL'),(1707,'eletrocardiograma','SUBSTANTIVO','PT',0,1,NULL,0,0,'Exame que registra a atividade elétrica do coração.','DIFICIL'),(1708,'gastroenterologista','NOME','PT',0,1,NULL,2,2,'Médico especializado no sistema digestivo.','DIFICIL'),(1709,'neuropediatria','SUBSTANTIVO','PT',0,1,NULL,2,2,'Especialidade médica voltada ao sistema nervoso infantil.','DIFICIL'),(1710,'neurocirurgião','NOME','PT',1,1,NULL,0,0,'Médico especializado em cirurgias do sistema nervoso.','DIFICIL'),(1711,'cardiomiopatia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Doença que afeta o músculo do coração.','DIFICIL'),(1712,'hepatotoxicidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Capacidade de causar danos ao fígado.','DIFICIL'),(1713,'imunohistoquímica','SUBSTANTIVO','PT',1,1,NULL,0,0,'Técnica laboratorial usada para identificar proteínas em tecidos.','DIFICIL'),(1714,'desoxirribonucleico','SUBSTANTIVO','PT',0,1,NULL,0,0,'Relacionado ao DNA.','DIFICIL'),(1715,'ribossômico','ADJETIVO','PT',1,1,NULL,0,0,'Relacionado aos ribossomos das células.','DIFICIL'),(1716,'transcriptase','SUBSTANTIVO','PT',0,1,NULL,0,0,'Enzima envolvida na produção de material genético.','DIFICIL'),(1717,'mitocondrial','ADJETIVO','PT',0,1,NULL,0,0,'Relacionado às mitocôndrias das células.','DIFICIL'),(1718,'microbiologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ciência que estuda os microrganismos.','DIFICIL'),(1719,'nanotecnologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Tecnologia aplicada em escala extremamente pequena.','DIFICIL'),(1720,'eletromagnetismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Fenômeno que envolve eletricidade e magnetismo.','DIFICIL'),(1721,'termodinâmica','SUBSTANTIVO','PT',1,1,NULL,0,0,'Área da Física que estuda calor e energia.','DIFICIL'),(1722,'astrofísica','SUBSTANTIVO','PT',1,1,NULL,1,1,'Estudo dos corpos celestes por meio da Física.','DIFICIL'),(1723,'cosmologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ciência que estuda a origem e evolução do universo.','DIFICIL'),(1724,'paralelepípedo','SUBSTANTIVO','PT',1,1,NULL,0,0,'Sólido geométrico com faces retangulares.','DIFICIL'),(1725,'incompreensibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que é difícil de entender.','DIFICIL'),(1726,'inexplicavelmente','ADVERBIO','PT',0,1,NULL,0,0,'De maneira que não pode ser explicada.','DIFICIL'),(1727,'extraordinariamente','ADVERBIO','PT',0,1,NULL,0,0,'De forma muito acima do comum.','DIFICIL'),(1728,'constitucionalista','NOME','PT',0,1,NULL,0,0,'Especialista em Direito Constitucional.','DIFICIL'),(1729,'eletrodoméstico','SUBSTANTIVO','PT',1,1,NULL,3,1,'Aparelho utilizado em tarefas domésticas.','DIFICIL'),(1730,'microorganismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ser vivo microscópico.','DIFICIL'),(1731,'hipersensibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Resposta exagerada do organismo a um estímulo.','DIFICIL'),(1732,'fotossíntese','SUBSTANTIVO','PT',1,1,NULL,0,0,'Processo pelo qual as plantas produzem seu alimento.','DIFICIL'),(1733,'respiratório','ADJETIVO','PT',1,1,NULL,0,0,'Relacionado à respiração.','DIFICIL'),(1734,'circulatório','ADJETIVO','PT',1,1,NULL,0,0,'Relacionado ao transporte de sangue pelo corpo.','DIFICIL'),(1735,'endócrino','ADJETIVO','PT',1,1,NULL,2,2,'Relacionado às glândulas produtoras de hormônios.','DIFICIL'),(1736,'neurológico','ADJETIVO','PT',1,1,NULL,0,0,'Relacionado ao sistema nervoso.','DIFICIL'),(1737,'hematologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Área da medicina que estuda o sangue.','DIFICIL'),(1738,'psicopatologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estudo dos transtornos mentais.','DIFICIL'),(1739,'fenomenologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Corrente filosófica que estuda a experiência consciente.','DIFICIL'),(1740,'existencialismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Corrente filosófica focada na existência humana.','DIFICIL'),(1741,'epistemologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ramo da filosofia que estuda o conhecimento.','DIFICIL'),(1742,'hermenêutica','SUBSTANTIVO','PT',1,1,NULL,2,0,'Arte ou técnica de interpretar textos.','DIFICIL'),(1743,'antropocentrismo','SUBSTANTIVO','PT',0,1,NULL,3,2,'Visão que coloca o ser humano como centro.','DIFICIL'),(1744,'evolucionismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Teoria sobre a evolução das espécies.','DIFICIL'),(1745,'biotecnologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Aplicação da biologia para desenvolver tecnologias.','DIFICIL'),(1746,'criptografia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Técnica de proteger informações por codificação.','DIFICIL'),(1747,'interoperabilidade','SUBSTANTIVO','PT',0,1,NULL,2,2,'Capacidade de diferentes sistemas trabalharem juntos.','DIFICIL'),(1748,'telecomunicações','SUBSTANTIVO','PT',1,1,NULL,0,0,'Transmissão de informações à distância.','DIFICIL'),(1749,'hiperrealismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estilo artístico com riqueza extrema de detalhes.','DIFICIL'),(1750,'desenvolvimentista','ADJETIVO','PT',0,1,NULL,0,0,'Relacionado ao incentivo do desenvolvimento econômico.','DIFICIL'),(1751,'constitucionalmente','ADVERBIO','PT',0,1,NULL,0,0,'De acordo com a Constituição.','DIFICIL'),(1752,'irreversibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que não pode ser desfeito.','DIFICIL'),(1753,'inconstitucional','ADJETIVO','PT',0,1,NULL,0,0,'Contrário ao que determina a Constituição.','DIFICIL'),(1754,'antropomorfismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Atribuição de características humanas a animais ou objetos.','DIFICIL'),(1755,'pneumoultramicroscopicossilicovulcanoconiótico','ADJETIVO','PT',0,1,NULL,0,0,'Palavra famosa por ser uma das maiores da língua portuguesa.','DIFICIL'),(1756,'ultramicroscópico','ADJETIVO','PT',1,1,NULL,3,2,'Extremamente pequeno, quase invisível.','DIFICIL'),(1757,'micropaleontologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estudo dos fósseis microscópicos.','DIFICIL'),(1758,'paleoclimatologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estudo dos climas da Terra no passado.','DIFICIL'),(1759,'geocronologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ciência que determina a idade das rochas.','DIFICIL'),(1760,'radioastronomia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Estudo do universo por meio de ondas de rádio.','DIFICIL'),(1761,'astrobiologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Ciência que investiga a possibilidade de vida fora da Terra.','DIFICIL'),(1762,'biogeoquímica','SUBSTANTIVO','PT',1,1,NULL,0,0,'Estudo das relações entre seres vivos, solo e elementos químicos.','DIFICIL'),(1763,'eletroquímica','SUBSTANTIVO','PT',1,1,NULL,0,0,'Área que relaciona eletricidade e reações químicas.','DIFICIL'),(1764,'nanobiotecnologia','SUBSTANTIVO','PT',0,1,NULL,0,0,'Aplicação da nanotecnologia na biologia.','DIFICIL'),(1765,'quimiossíntese','SUBSTANTIVO','PT',1,1,NULL,0,0,'Produção de energia por bactérias sem uso da luz solar.','DIFICIL'),(1766,'fotoperiodismo','SUBSTANTIVO','PT',0,1,NULL,0,0,'Resposta dos organismos à duração da luz do dia.','DIFICIL'),(1767,'heterogeneidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Característica do que possui partes diferentes.','DIFICIL'),(1768,'homogeneização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Processo de tornar algo uniforme.','DIFICIL'),(1769,'sustentabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Uso consciente dos recursos naturais.','DIFICIL'),(1770,'descarbonização','SUBSTANTIVO','PT',1,1,NULL,2,0,'Processo de redução das emissões de carbono.','DIFICIL'),(1771,'industrialização','SUBSTANTIVO','PT',1,1,NULL,2,2,'Desenvolvimento das atividades industriais.','DIFICIL'),(1772,'urbanização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Crescimento das áreas urbanas.','DIFICIL'),(1773,'globalização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Integração econômica, cultural e tecnológica entre países.','DIFICIL'),(1774,'informatização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Implantação de recursos de informática.','DIFICIL'),(1775,'automatização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Uso de máquinas para executar tarefas automaticamente.','DIFICIL'),(1776,'descentralização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Distribuição de poder ou responsabilidades.','DIFICIL'),(1777,'centralização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Concentração de decisões em um único ponto.','DIFICIL'),(1778,'responsabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Dever de responder pelos próprios atos.','DIFICIL'),(1779,'incompatibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Falta de compatibilidade entre elementos.','DIFICIL'),(1780,'indisponibilidade','SUBSTANTIVO','PT',0,1,NULL,2,2,'Estado do que não está disponível.','DIFICIL'),(1781,'inconstitucionalidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Condição do que viola a Constituição.','DIFICIL'),(1782,'irreparabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Característica do que não pode ser reparado.','DIFICIL'),(1783,'ininteligibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que é difícil de compreender.','DIFICIL'),(1784,'desproporcionalidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Falta de equilíbrio entre duas partes.','DIFICIL'),(1785,'inquestionabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que não pode ser contestado.','DIFICIL'),(1786,'indispensabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Característica do que é essencial.','DIFICIL'),(1787,'invariabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que permanece sem mudanças.','DIFICIL'),(1788,'inexequibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Condição do que não pode ser executado.','DIFICIL'),(1789,'intraduzibilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Característica do que não pode ser traduzido com exatidão.','DIFICIL'),(1790,'inabalabilidade','SUBSTANTIVO','PT',0,1,NULL,2,1,'Qualidade do que não pode ser abalado.','DIFICIL'),(1791,'intransigência','SUBSTANTIVO','PT',1,1,NULL,0,0,'Rigidez ao não aceitar opiniões contrárias.','DIFICIL'),(1792,'intransparência','SUBSTANTIVO','PT',1,1,NULL,0,0,'Falta de clareza nas informações.','DIFICIL'),(1793,'incontestabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Qualidade do que não admite contestação.','DIFICIL'),(1794,'incomunicabilidade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Dificuldade ou impossibilidade de comunicação.','DIFICIL'),(1795,'interdisciplinaridade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Integração entre diferentes áreas do conhecimento.','DIFICIL'),(1796,'transdisciplinaridade','SUBSTANTIVO','PT',0,1,NULL,0,0,'Integração que ultrapassa os limites das disciplinas.','DIFICIL'),(1797,'intergovernamental','ADJETIVO','PT',0,1,NULL,0,0,'Relacionado à cooperação entre governos.','DIFICIL'),(1798,'constitucionalização','SUBSTANTIVO','PT',1,1,NULL,2,0,'Processo de incorporar normas à Constituição.','DIFICIL'),(1799,'desindustrialização','SUBSTANTIVO','PT',1,1,NULL,0,0,'Redução da participação da indústria na economia.','DIFICIL'),(1801,'abacate',NULL,'PT',0,0,NULL,0,0,NULL,'FACIL'),(1802,'topeira',NULL,'PT',0,0,1505,0,0,NULL,'FACIL'),(1803,'login',NULL,'PT',0,0,1505,0,0,NULL,'MEDIO'),(1804,'logout',NULL,'PT',0,0,1505,0,0,NULL,'MEDIO'),(1805,'epaminondas',NULL,'PT',0,0,NULL,0,0,NULL,'FACIL');
/*!40000 ALTER TABLE `palavra` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `palavra_do_dia_tentativa`
--

DROP TABLE IF EXISTS `palavra_do_dia_tentativa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `palavra_do_dia_tentativa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data` date NOT NULL,
  `login` varchar(100) DEFAULT NULL,
  `acertou` tinyint NOT NULL,
  `palavra_id` bigint NOT NULL,
  `criado_em` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_pdd_tentativa_data_login` (`data`,`login`),
  KEY `fk_pdd_tentativa_palavra` (`palavra_id`),
  KEY `ix_pdd_tentativa_data` (`data`),
  CONSTRAINT `fk_pdd_tentativa_palavra` FOREIGN KEY (`palavra_id`) REFERENCES `palavra` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `palavra_do_dia_tentativa`
--

LOCK TABLES `palavra_do_dia_tentativa` WRITE;
/*!40000 ALTER TABLE `palavra_do_dia_tentativa` DISABLE KEYS */;
/*!40000 ALTER TABLE `palavra_do_dia_tentativa` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ranking`
--

DROP TABLE IF EXISTS `ranking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ranking` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `posicao` int DEFAULT NULL,
  `pontuacao_total` int DEFAULT NULL,
  `ultima_atualizacao` datetime(6) DEFAULT NULL,
  `aluno_id` bigint DEFAULT NULL,
  `sala_codigo` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ranking__aluno_id` (`aluno_id`),
  KEY `fk_ranking__sala_codigo` (`sala_codigo`),
  CONSTRAINT `fk_ranking__aluno_id` FOREIGN KEY (`aluno_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `fk_ranking__sala_codigo` FOREIGN KEY (`sala_codigo`) REFERENCES `sala` (`codigo`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ranking`
--

LOCK TABLES `ranking` WRITE;
/*!40000 ALTER TABLE `ranking` DISABLE KEYS */;
/*!40000 ALTER TABLE `ranking` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rel_lista_palavras__palavras`
--

DROP TABLE IF EXISTS `rel_lista_palavras__palavras`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rel_lista_palavras__palavras` (
  `palavras_id` bigint NOT NULL,
  `lista_palavras_id` bigint NOT NULL,
  PRIMARY KEY (`lista_palavras_id`,`palavras_id`),
  KEY `fk_rel_lista_palavras__palavras__palavras_id` (`palavras_id`),
  CONSTRAINT `fk_rel_lista_palavras__palavras__lista_palavras_id` FOREIGN KEY (`lista_palavras_id`) REFERENCES `lista_palavras` (`id`),
  CONSTRAINT `fk_rel_lista_palavras__palavras__palavras_id` FOREIGN KEY (`palavras_id`) REFERENCES `palavra` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rel_lista_palavras__palavras`
--

LOCK TABLES `rel_lista_palavras__palavras` WRITE;
/*!40000 ALTER TABLE `rel_lista_palavras__palavras` DISABLE KEYS */;
/*!40000 ALTER TABLE `rel_lista_palavras__palavras` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rel_usuario__salas_aluno`
--

DROP TABLE IF EXISTS `rel_usuario__salas_aluno`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rel_usuario__salas_aluno` (
  `usuario_id` bigint NOT NULL,
  `salas_aluno_codigo` varchar(255) NOT NULL,
  PRIMARY KEY (`usuario_id`,`salas_aluno_codigo`),
  KEY `fk_rel_usuario__salas_aluno__salas_aluno_codigo` (`salas_aluno_codigo`),
  CONSTRAINT `fk_rel_usuario__salas_aluno__salas_aluno_codigo` FOREIGN KEY (`salas_aluno_codigo`) REFERENCES `sala` (`codigo`),
  CONSTRAINT `fk_rel_usuario__salas_aluno__usuario_id` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rel_usuario__salas_aluno`
--

LOCK TABLES `rel_usuario__salas_aluno` WRITE;
/*!40000 ALTER TABLE `rel_usuario__salas_aluno` DISABLE KEYS */;
/*!40000 ALTER TABLE `rel_usuario__salas_aluno` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `resposta`
--

DROP TABLE IF EXISTS `resposta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resposta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resposta_digitada` varchar(255) DEFAULT NULL,
  `correta` tinyint DEFAULT NULL,
  `tempo_resposta` int DEFAULT NULL,
  `pontuacao` int DEFAULT NULL,
  `data_resposta` datetime(6) DEFAULT NULL,
  `atividade_id` bigint DEFAULT NULL,
  `aluno_id` bigint DEFAULT NULL,
  `palavra_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_resposta__atividade_id` (`atividade_id`),
  KEY `fk_resposta__aluno_id` (`aluno_id`),
  KEY `fk_resposta__palavra_id` (`palavra_id`),
  CONSTRAINT `fk_resposta__aluno_id` FOREIGN KEY (`aluno_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `fk_resposta__atividade_id` FOREIGN KEY (`atividade_id`) REFERENCES `atividade` (`id`),
  CONSTRAINT `fk_resposta__palavra_id` FOREIGN KEY (`palavra_id`) REFERENCES `palavra` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `resposta`
--

LOCK TABLES `resposta` WRITE;
/*!40000 ALTER TABLE `resposta` DISABLE KEYS */;
/*!40000 ALTER TABLE `resposta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sala`
--

DROP TABLE IF EXISTS `sala`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sala` (
  `nome` varchar(255) NOT NULL,
  `codigo` varchar(255) NOT NULL,
  `descricao` json DEFAULT NULL,
  `ativo` tinyint DEFAULT NULL,
  `professor_id` bigint DEFAULT NULL,
  `data_criacao` datetime(6) DEFAULT NULL,
  `tipo` varchar(20) NOT NULL DEFAULT 'TURMA',
  `privada` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`codigo`),
  UNIQUE KEY `ux_sala__codigo` (`codigo`),
  KEY `fk_sala__professor_id` (`professor_id`),
  CONSTRAINT `fk_sala__professor_id` FOREIGN KEY (`professor_id`) REFERENCES `usuario` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sala`
--

LOCK TABLES `sala` WRITE;
/*!40000 ALTER TABLE `sala` DISABLE KEYS */;
/*!40000 ALTER TABLE `sala` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuario`
--

DROP TABLE IF EXISTS `usuario`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuario` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nome` varchar(255) NOT NULL,
  `sobrenome` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `senha` varchar(255) NOT NULL,
  `tipo_usuario` varchar(255) NOT NULL,
  `ativo` tinyint DEFAULT NULL,
  `xp` bigint NOT NULL DEFAULT '0',
  `apelido` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_usuario__email` (`email`),
  KEY `ix_usuario_xp` (`xp`)
) ENGINE=InnoDB AUTO_INCREMENT=1508 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuario`
--

LOCK TABLES `usuario` WRITE;
/*!40000 ALTER TABLE `usuario` DISABLE KEYS */;
/*!40000 ALTER TABLE `usuario` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuario_conquista`
--

DROP TABLE IF EXISTS `usuario_conquista`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuario_conquista` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_conquista` datetime(6) DEFAULT NULL,
  `progresso` int DEFAULT NULL,
  `concluida` tinyint DEFAULT NULL,
  `aluno_id` bigint DEFAULT NULL,
  `conquista_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_usuario_conquista__aluno_id` (`aluno_id`),
  KEY `fk_usuario_conquista__conquista_id` (`conquista_id`),
  CONSTRAINT `fk_usuario_conquista__aluno_id` FOREIGN KEY (`aluno_id`) REFERENCES `usuario` (`id`),
  CONSTRAINT `fk_usuario_conquista__conquista_id` FOREIGN KEY (`conquista_id`) REFERENCES `conquista` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1532 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuario_conquista`
--

LOCK TABLES `usuario_conquista` WRITE;
/*!40000 ALTER TABLE `usuario_conquista` DISABLE KEYS */;
/*!40000 ALTER TABLE `usuario_conquista` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'digitado'
--

--
-- Dumping routines for database 'digitado'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-14 20:47:25
