<#
    Sobe o DIGITADO inteiro em containers: a aplicacao (back-end e front-end
    ja compilados) e o MySQL com os dados de dados\banco-digitado.sql.

    E a alternativa ao setup-ambiente.ps1. A diferenca e o que a maquina
    precisa ter instalado:

        setup-ambiente.ps1    Docker + JDK 17 + Node (instalados na maquina)
        iniciar-container.ps1 Docker, e mais nada

    Nao pede administrador, nao mexe no Path, nao instala Java e nao cria
    regra de firewall - o Docker Desktop ja publica a porta 8080 sozinho.

    Uso:
        powershell -ExecutionPolicy Bypass -File .\iniciar-container.ps1

    Parametros:
        -Reconstruir  recompila a imagem da aplicacao a partir do codigo atual
                      (use depois de trazer uma versao nova do projeto);
                      os dados do banco sao preservados
        -Parar        desliga os dois containers, sem apagar nada
        -Limpar       apaga tambem o banco do container, para a proxima subida
                      recomecar do dump dados\banco-digitado.sql
#>

param(
    [switch]$Reconstruir,
    [switch]$Parar,
    [switch]$Limpar
)

# 'Continue', e nao 'Stop': o docker escreve avisos normais na saida de erro
# (build cache, credenciais) e com 'Stop' isso viraria excecao fatal no
# Windows PowerShell 5.1. O sucesso e conferido por $LASTEXITCODE.
$ErrorActionPreference = 'Continue'

$COMPOSE = 'docker-compose.yml'
$ARQUIVO_ENV = '.env'
$ARQUIVO_DUMP = 'dados\banco-digitado.sql'

# Semente que vem no repositorio: palavras, listas, conquistas e as contas
# padrao, sem nenhum dado de usuario. Serve de reserva para quem clonou do
# GitHub e por isso nao tem o dump do pendrive.
$ARQUIVO_SEMENTE = 'dados\seed-digitado.sql'
$ENDERECO = 'http://localhost:8080'

# Quanto esperar a aplicacao responder depois que os containers sobem. A
# primeira subida inclui a importacao do dump e a migracao do Liquibase.
$SEGUNDOS_ESPERA = 300

function Escrever($texto, $cor = 'White') { Write-Host $texto -ForegroundColor $cor }

# Chave aleatoria em Base64, do gerador criptografico do Windows (Get-Random
# nao serve: e previsivel e nao vale para segredo).
function Gerar-Segredo($bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($buffer)
}

Escrever ""
Escrever "==================================================" Cyan
Escrever "   DIGITADO em container" Cyan
Escrever "==================================================" Cyan

Push-Location $PSScriptRoot
try {
    # ================================================================  DOCKER
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Escrever "`nERRO: o Docker nao esta instalado nesta maquina." Red
        Escrever "Baixe o Docker Desktop em https://www.docker.com/products/docker-desktop/" DarkYellow
        Escrever "e rode este script de novo depois de reiniciar o computador.`n" DarkYellow
        exit 1
    }

    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Escrever "`nERRO: o Docker esta instalado, mas nao esta em execucao." Red
        Escrever "Abra o Docker Desktop, espere o icone da baleia parar de se mexer" DarkYellow
        Escrever "e rode este script de novo.`n" DarkYellow
        exit 1
    }

    if (-not (Test-Path $COMPOSE)) {
        Escrever "`nERRO: $COMPOSE nao encontrado." Red
        Escrever "Rode o script de dentro da pasta do projeto.`n" DarkYellow
        exit 1
    }

    # =================================================================  PARAR
    if ($Parar) {
        Escrever "`n  Desligando os containers..." Yellow
        docker compose -f $COMPOSE down | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Escrever "`n  FALHOU - o docker retornou codigo $LASTEXITCODE`n" Red
            exit 1
        }
        Escrever "`n  DESLIGADO - os dados do banco continuam guardados." Green
        Escrever "  Para subir de novo: .\iniciar-container.ps1`n" DarkGray
        exit 0
    }

    # ================================================================  LIMPAR
    if ($Limpar) {
        Escrever "`n  ATENCAO: isto apaga o banco de dados do container." Red
        Escrever "  Tudo que foi jogado ou cadastrado nesta maquina se perde." Red
        Escrever "  A proxima subida recomeca do dump $ARQUIVO_DUMP." DarkYellow
        Escrever ""
        $resposta = Read-Host "  Digite APAGAR para confirmar (qualquer outra coisa cancela)"
        if ($resposta -cne 'APAGAR') {
            Escrever "`n  Cancelado - nada foi apagado.`n" Green
            exit 0
        }
        docker compose -f $COMPOSE down -v | Out-Host
        Escrever "`n  Banco apagado. Subindo tudo de novo a partir do dump..." Yellow
    }

    # ==================================================================  DUMP
    # O compose monta este arquivo dentro do MySQL. Se ele nao existir, o
    # Docker criaria uma PASTA vazia com esse nome e o banco subiria quebrado,
    # entao deixamos um arquivo vazio no lugar e avisamos.
    if (-not (Test-Path $ARQUIVO_DUMP)) {
        $pasta = Split-Path $ARQUIVO_DUMP -Parent
        if ($pasta -and -not (Test-Path $pasta)) {
            New-Item -ItemType Directory -Path $pasta -Force | Out-Null
        }

        if (Test-Path $ARQUIVO_SEMENTE) {
            Copy-Item $ARQUIVO_SEMENTE $ARQUIVO_DUMP
            Escrever "`n  Sem dump do pendrive: usando a semente $ARQUIVO_SEMENTE." DarkGray
            Escrever "  O banco nasce com as palavras, as listas e as conquistas," DarkGray
            Escrever "  mas sem partidas nem contas alem de admin e user." DarkGray
        } else {
            Escrever "`n  AVISO: nao existe $ARQUIVO_DUMP nem $ARQUIVO_SEMENTE." Yellow
            Escrever "  O banco vai comecar vazio: sem palavras, listas nem contas" Yellow
            Escrever "  alem das que o Liquibase cria (admin e user)." Yellow
            Escrever "  Para trazer os dados, rode exportar-banco.ps1 na maquina de origem." DarkYellow
            Set-Content -Path $ARQUIVO_DUMP -Encoding UTF8 `
                -Value '-- Sem dados para importar. Gere o real com exportar-banco.ps1.'
            Start-Sleep -Seconds 4
        }
    }

    # ===================================================================  ENV
    # As senhas ficam fora do compose e fora do git (.env esta no .gitignore):
    # cada maquina gera as suas na primeira vez e reusa depois.
    if (Test-Path $ARQUIVO_ENV) {
        Escrever "`n  Usando as senhas ja existentes em $ARQUIVO_ENV." DarkGray
    } else {
        Escrever "`n  Gerando as senhas desta maquina em $ARQUIVO_ENV..." Yellow
        $linhas = @(
            '# Gerado por iniciar-container.ps1. Nao commitar, nao compartilhar.'
            '# Apagar este arquivo so faz sentido junto com -Limpar: as senhas'
            '# aqui sao as que o banco do container ja tem gravadas.'
            ''
            "MYSQL_ROOT_PASSWORD=$(Gerar-Segredo 18)"
            "DIGITADO_DB_PASSWORD=$(Gerar-Segredo 18)"
            ''
            '# Chave que assina o login (JWT). Trocar desconecta todo mundo.'
            "JWT_SECRET=$(Gerar-Segredo 64)"
        )
        Set-Content -Path $ARQUIVO_ENV -Value $linhas -Encoding ASCII
    }

    # ==================================================================  SUBIR
    $argumentos = @('compose', '-f', $COMPOSE, 'up', '-d')
    if ($Reconstruir) { $argumentos += '--build' }

    Escrever ""
    if ($Reconstruir) {
        Escrever "  Recompilando a aplicacao e subindo os containers..." Yellow
    } else {
        Escrever "  Subindo os containers..." Yellow
    }
    Escrever "  Na primeira vez isto baixa cerca de 1 GB e compila o projeto:" DarkGray
    Escrever "  de 5 a 20 minutos, conforme a internet. Nas proximas, e rapido." DarkGray
    Escrever ""

    & docker @argumentos | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Escrever "`n  FALHOU - o docker retornou codigo $LASTEXITCODE" Red
        Escrever "  Veja o erro completo com:" DarkYellow
        Escrever "    docker compose -f $COMPOSE up --build" Cyan
        Escrever ""
        exit 1
    }

    # =================================================================  ESPERA
    # 'up -d' devolve o controle assim que os containers iniciam, o que e bem
    # antes de a aplicacao estar de pe. Quem sabe a resposta e o healthcheck.
    Escrever "`n  Esperando a aplicacao responder..." Yellow

    $idApp = (docker compose -f $COMPOSE ps -q app 2>$null | Select-Object -First 1)
    $saudavel = $false
    $morreu = $false
    $limite = (Get-Date).AddSeconds($SEGUNDOS_ESPERA)

    while ((Get-Date) -lt $limite) {
        $estado = (docker inspect -f '{{.State.Status}}' $idApp 2>$null)
        $saude = (docker inspect -f '{{.State.Health.Status}}' $idApp 2>$null)
        if ($saude -eq 'healthy') { $saudavel = $true; break }
        if ($estado -eq 'exited') { $morreu = $true; break }
        Start-Sleep -Seconds 5
    }

    if (-not $saudavel) {
        $motivo = if ($morreu) { 'a aplicacao subiu e parou' } else { "a aplicacao nao respondeu em $SEGUNDOS_ESPERA segundos" }
        Escrever "`n  ALGO NAO DEU CERTO - $motivo." Red
        Escrever "`n  Ultimas linhas do log:" DarkYellow
        docker compose -f $COMPOSE logs --tail 25 app | Out-Host
        Escrever "`n  Log completo: docker compose -f $COMPOSE logs -f app" Cyan
        Escrever ""
        exit 1
    }

    # =================================================================  RESUMO
    Escrever ""
    Escrever "==================================================" Cyan
    Escrever "   TUDO FUNCIONOU" Green
    Escrever "==================================================" Cyan
    Escrever ""
    Escrever "  Abra o DIGITADO em $ENDERECO" Cyan
    Escrever ""
    Escrever "  Nao existe segundo terminal nem 'npmw start': no container o"
    Escrever "  front-end ja vem compilado dentro da aplicacao, tudo na 8080."
    Escrever ""

    # IP da interface da rota padrao. Pegar o primeiro adaptador da lista
    # traria enderecos de adaptadores virtuais (WSL, Hyper-V), que nao servem.
    $indice = (Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Sort-Object RouteMetric, ifMetric | Select-Object -First 1).InterfaceIndex
    $ip = if ($indice) {
        (Get-NetIPAddress -InterfaceIndex $indice -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Select-Object -First 1).IPAddress
    }
    if ($ip) {
        Escrever "  Na rede local, outras maquinas acessam por http://${ip}:8080"
        Escrever ""
    }

    Escrever "  Os containers voltam sozinhos quando o Docker Desktop abrir."
    Escrever "  Para desligar agora:  .\iniciar-container.ps1 -Parar" DarkGray
    Escrever ""
    exit 0
} finally {
    Pop-Location
}
