<#
    Gera a semente publica do banco a partir de um dump pessoal.

    O dados\banco-digitado.sql que o exportar-banco.ps1 produz tem e-mails,
    hashes de senha, partidas e respostas de gente real, entao fica de fora do
    repositorio. Este script le esse dump e escreve dados\seed-digitado.sql
    com o que pode ser publico:

        fica          palavras, listas, conquistas, papeis (jhi_authority),
                      o controle do Liquibase e as contas padrao admin/user
        sai           usuarios reais, salas, atividades, respostas, erros,
                      ranking, conquistas ganhas e tentativas da palavra do dia

    O trabalho todo acontece dentro de um container MySQL descartavel, criado
    e destruido aqui mesmo. O banco de desenvolvimento desta maquina e o
    container do docker-compose.yml nao sao tocados em momento algum.

    Uso:
        powershell -ExecutionPolicy Bypass -File .\sanitizar-banco.ps1

    Parametros:
        -Origem   dump de entrada  (padrao: dados\banco-digitado.sql)
        -Destino  semente de saida (padrao: dados\seed-digitado.sql)
#>

param(
    [string]$Origem = 'dados\banco-digitado.sql',
    [string]$Destino = 'dados\seed-digitado.sql'
)

# 'Continue', e nao 'Stop': o docker e o mysql escrevem avisos normais na saida
# de erro, e com 'Stop' isso viraria excecao fatal no Windows PowerShell 5.1.
$ErrorActionPreference = 'Continue'

$IMAGEM = 'mysql:9.2.0'
$NOME_CONTAINER = 'digitado-sanitizador'
$SENHA = 'sanitizar'
$BANCO = 'digitado'

# Contas padrao do JHipster, criadas pelo Liquibase em config/liquibase/data/user.csv.
# Precisam sobreviver: o changelog que as carrega ja consta como aplicado no
# dump, entao o Liquibase nao vai recria-las em uma instalacao nova.
$IDS_PADRAO = '1,2'

function Escrever($texto, $cor = 'White') { Write-Host $texto -ForegroundColor $cor }

Escrever ""
Escrever "==================================================" Cyan
Escrever "   Semente publica do banco DIGITADO" Cyan
Escrever "==================================================" Cyan

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path $Origem)) {
        Escrever "`nERRO: nao encontrei $Origem." Red
        Escrever "Gere o dump antes, com:" DarkYellow
        Escrever "  powershell -ExecutionPolicy Bypass -File .\exportar-banco.ps1" Cyan
        Escrever ""
        exit 1
    }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Escrever "`nERRO: o Docker nao esta instalado nesta maquina.`n" Red
        exit 1
    }
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Escrever "`nERRO: o Docker esta instalado, mas nao esta em execucao." Red
        Escrever "Abra o Docker Desktop, espere ficar running e rode de novo.`n" DarkYellow
        exit 1
    }

    # Sobra de uma execucao interrompida no meio.
    docker rm -f $NOME_CONTAINER 2>&1 | Out-Null

    Escrever "`n  Subindo um MySQL descartavel..." Yellow
    docker run -d --name $NOME_CONTAINER `
        -e "MYSQL_ROOT_PASSWORD=$SENHA" `
        --health-cmd="mysqladmin ping -h 127.0.0.1 -u root -p$SENHA --silent" `
        --health-interval=5s --health-retries=30 `
        $IMAGEM mysqld --lower_case_table_names=1 --skip-mysqlx `
        --character_set_server=utf8mb4 --explicit_defaults_for_timestamp 2>&1 | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Escrever "`n  FALHOU - nao consegui criar o container temporario.`n" Red
        exit 1
    }

    try {
        Escrever "  Esperando o MySQL aceitar conexoes..." Yellow
        $pronto = $false
        foreach ($tentativa in 1..60) {
            if ((docker inspect -f '{{.State.Health.Status}}' $NOME_CONTAINER 2>$null) -eq 'healthy') {
                $pronto = $true; break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $pronto) {
            Escrever "`n  FALHOU - o MySQL temporario nao respondeu em 2 minutos.`n" Red
            exit 1
        }

        Escrever "  Importando $Origem..." Yellow
        docker cp $Origem "${NOME_CONTAINER}:/tmp/entrada.sql" 2>&1 | Out-Null
        docker exec $NOME_CONTAINER sh -c "mysql -u root -p$SENHA < /tmp/entrada.sql" 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' } | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Escrever "`n  FALHOU - o mysql recusou o dump de entrada.`n" Red
            exit 1
        }

        # FOREIGN_KEY_CHECKS=0 durante a limpeza: as tabelas se referenciam em
        # cadeia (resposta -> atividade -> sala -> usuario) e apagar na ordem
        # certa seria fragil se o modelo mudar.
        Escrever "  Removendo os dados de usuario..." Yellow
        $limpeza = @"
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM $BANCO.erro_ortografico;
DELETE FROM $BANCO.resposta;
DELETE FROM $BANCO.atividade;
DELETE FROM $BANCO.palavra_do_dia_tentativa;
DELETE FROM $BANCO.ranking;
DELETE FROM $BANCO.usuario_conquista;
DELETE FROM $BANCO.rel_usuario__salas_aluno;
DELETE FROM $BANCO.sala;
DELETE FROM $BANCO.usuario;
DELETE FROM $BANCO.jhi_user_authority WHERE user_id NOT IN ($IDS_PADRAO);
DELETE FROM $BANCO.jhi_user WHERE id NOT IN ($IDS_PADRAO);
UPDATE $BANCO.jhi_user SET reset_key=NULL, activation_key=NULL, reset_date=NULL;
SET FOREIGN_KEY_CHECKS=1;
"@
        docker exec -i $NOME_CONTAINER mysql -u root "-p$SENHA" -e $limpeza 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' } | Out-Host

        Escrever "  Exportando a semente..." Yellow
        # Mesmas opcoes do exportar-banco.ps1, para os dois arquivos ficarem
        # com o mesmo formato. A copia sai por 'docker cp' em vez de
        # redirecionamento para nao passar pela codificacao do PowerShell.
        docker exec $NOME_CONTAINER sh -c "mysqldump -u root -p$SENHA --databases $BANCO --add-drop-database --single-transaction --routines --events --default-character-set=utf8mb4 > /tmp/saida.sql" 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' } | Out-Null

        $pasta = Split-Path $Destino -Parent
        if ($pasta -and -not (Test-Path $pasta)) { New-Item -ItemType Directory -Path $pasta -Force | Out-Null }
        docker cp "${NOME_CONTAINER}:/tmp/saida.sql" $Destino 2>&1 | Out-Null

        if (-not (Test-Path $Destino) -or (Get-Item $Destino).Length -lt 1KB) {
            Escrever "`n  FALHOU - a semente saiu vazia.`n" Red
            exit 1
        }

        $contagem = docker exec $NOME_CONTAINER mysql -u root "-p$SENHA" -N -B -e `
            "SELECT CONCAT(COUNT(*),' palavras') FROM $BANCO.palavra UNION ALL SELECT CONCAT(COUNT(*),' listas') FROM $BANCO.lista_palavras UNION ALL SELECT CONCAT(COUNT(*),' conquistas') FROM $BANCO.conquista UNION ALL SELECT CONCAT(COUNT(*),' contas') FROM $BANCO.jhi_user;" 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' }
    } finally {
        # Sai mesmo se algo acima falhar: nada de container orfao na maquina.
        docker rm -f $NOME_CONTAINER 2>&1 | Out-Null
    }

    # Rede de seguranca: se sobrou qualquer e-mail que nao seja das contas
    # padrao, alguma tabela nova entrou no modelo e nao esta sendo limpa.
    $conteudo = [IO.File]::ReadAllText((Resolve-Path $Destino), [Text.Encoding]::UTF8)
    $vazados = [regex]::Matches($conteudo, '@(?!localhost)[A-Za-z0-9.-]+\.[A-Za-z]{2,}').Count

    Escrever ""
    if ($vazados -gt 0) {
        Escrever "  ATENCAO - encontrei $vazados e-mail(s) na semente." Red
        Escrever "  Nao publique este arquivo: confira quais tabelas novas guardam" Red
        Escrever "  dados de usuario e acrescente-as a limpeza deste script." DarkYellow
        Escrever ""
        exit 1
    }

    Escrever "  SEMENTE GERADA" Green
    Escrever ("  {0}  ({1:N0} KB)" -f $Destino, ((Get-Item $Destino).Length / 1KB))
    foreach ($linha in $contagem) { Escrever "  $linha" DarkGray }
    Escrever "  Nenhum e-mail de usuario real." DarkGray
    Escrever ""
    exit 0
} finally {
    Pop-Location
}
