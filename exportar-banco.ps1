<#
    Exporta o banco DIGITADO desta maquina para um arquivo .sql, que viaja junto
    com o projeto e e restaurado pelo setup-ambiente.ps1 na outra maquina.

    O arquivo contem estrutura e dados, inclusive as tabelas de controle do
    Liquibase (databasechangelog), entao a outra maquina reconhece os changelogs
    ja aplicados e nao tenta recria-los.

    Uso:
        powershell -ExecutionPolicy Bypass -File .\exportar-banco.ps1
        acrescente -Arquivo dados\meu-backup.sql para escolher outro destino
#>

param(
    [string]$Arquivo = 'dados\banco-digitado.sql',

    # Ligado pelo copiar-para-pendrive.ps1: omite a dica do proximo passo,
    # que naquele fluxo ja esta sendo executado.
    [switch]$Encadeado
)

# 'Continue', e nao 'Stop': no Windows PowerShell 5.1 qualquer texto que um
# programa externo escreva na saida de erro viraria excecao fatal com 'Stop'.
# O mysqldump e o docker fazem isso em situacoes normais (aviso de senha na
# linha de comando, daemon parado). O sucesso e conferido por $LASTEXITCODE.
$ErrorActionPreference = 'Continue'

$SENHA_MYSQL = '31415'
$NOME_BANCO = 'digitado'
$COMPOSE_MYSQL = 'src/main/docker/mysql.yml'

function Escrever($texto, $cor = 'White') { Write-Host $texto -ForegroundColor $cor }

# Procura o mysqldump de uma instalacao local do MySQL.
function Procurar-MysqlDump {
    if (Get-Command mysqldump -ErrorAction SilentlyContinue) {
        return (Get-Command mysqldump).Source
    }
    foreach ($base in @("$env:ProgramFiles\MySQL", "${env:ProgramFiles(x86)}\MySQL")) {
        if (-not (Test-Path $base)) { continue }
        $exe = Get-ChildItem $base -Recurse -Filter 'mysqldump.exe' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($exe) { return $exe.FullName }
    }
    return $null
}

Escrever ""
Escrever "==================================================" Cyan
Escrever "   Exportacao do banco DIGITADO" Cyan
Escrever "==================================================" Cyan

Push-Location $PSScriptRoot
try {
    $destino = Join-Path $PSScriptRoot $Arquivo
    $pasta = Split-Path $destino -Parent
    if (-not (Test-Path $pasta)) { New-Item -ItemType Directory -Path $pasta -Force | Out-Null }

    # Argumentos comuns as duas formas de exportar.
    # --databases inclui o CREATE DATABASE, para a restauracao recriar o banco.
    # --single-transaction evita travar as tabelas durante a leitura.
    $opcoes = @(
        '--databases', $NOME_BANCO
        '--add-drop-database'
        '--single-transaction'
        '--routines'
        '--events'
        '--default-character-set=utf8mb4'
    )

    $dump = Procurar-MysqlDump
    $idContainer = $null

    # O Docker so e consultado quando nao ha mysqldump local. O filtro descarta
    # as mensagens de erro que o docker imprime quando o daemon esta parado,
    # deixando passar apenas um id de container.
    if (-not $dump -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        $idContainer = (docker compose -f $COMPOSE_MYSQL ps -q mysql 2>&1 |
            Where-Object { $_ -is [string] -and $_ -match '^[0-9a-f]{12,}$' } |
            Select-Object -First 1)
    }

    if ($dump) {
        Escrever "`n  Usando o mysqldump local:"
        Escrever "  $dump" DarkGray
        & $dump -u root "-p$SENHA_MYSQL" @opcoes --result-file="$destino" 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' } | Out-Host
    } elseif ($idContainer) {
        Escrever "`n  Usando o mysqldump de dentro do container Docker."
        docker exec $idContainer mysqldump -u root "-p$SENHA_MYSQL" @opcoes 2>$null |
            Set-Content -Path $destino -Encoding UTF8
    } else {
        Escrever "`nERRO: nao encontrei o mysqldump nem um container do projeto." Red
        Escrever "Instale o MySQL Server (que traz o mysqldump) ou suba o banco com:" Red
        Escrever "  docker compose -f $COMPOSE_MYSQL up -d" DarkYellow
        exit 1
    }

    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $destino)) {
        Escrever "`n  FALHOU - o mysqldump retornou codigo $LASTEXITCODE" Red
        Escrever "  Confira se o banco '$NOME_BANCO' existe e se a senha do root e '$SENHA_MYSQL'." DarkYellow
        exit 1
    }

    $tamanho = (Get-Item $destino).Length
    if ($tamanho -lt 1KB) {
        Escrever "`n  FALHOU - o arquivo gerado esta vazio ($tamanho bytes)." Red
        exit 1
    }

    # Confirmacao simples de que o dump tem estrutura e dados.
    $conteudo = Get-Content $destino -TotalCount 400 | Out-String
    $temCreate = $conteudo -match 'CREATE TABLE'
    # -Quiet sozinho: combinado com -List ele devolve a linha, nao um booleano.
    $temInsert = [bool](Select-String -Path $destino -Pattern 'INSERT INTO' -Quiet)

    Escrever "`n  EXPORTACAO CONCLUIDA" Green
    Escrever ("  {0}  ({1:N1} MB)" -f $destino, ($tamanho / 1MB))
    Escrever "  Contem CREATE TABLE: $temCreate"
    Escrever "  Contem INSERT INTO:  $temInsert"
    if (-not $Encadeado) {
        Escrever ""
        Escrever "  Agora copie o projeto para o pendrive:"
        Escrever "    powershell -ExecutionPolicy Bypass -File .\copiar-para-pendrive.ps1 -Destino E:\" Cyan
    }
    Escrever ""
} finally {
    Pop-Location
}

exit 0
