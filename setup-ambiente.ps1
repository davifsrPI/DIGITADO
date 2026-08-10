<#
    Prepara o ambiente de desenvolvimento do DIGITADO em qualquer maquina Windows.

    O que faz:
      1. Instala e configura o JDK 17 (o enforcer do pom.xml aceita apenas 17, 21 ou 24)
      2. Sobe um MySQL em localhost:3306 com o banco DIGITADO e as credenciais
         esperadas por src/main/resources/config/application-dev.yml (root / 31415)
      3. Libera as portas 8080 e 9000 no firewall (perfil Private), para que
         outras maquinas da rede local consigam acessar a aplicacao
      4. Compila o backend com o Maven Wrapper (baixa Node e npm automaticamente)
      5. Instala as dependencias do frontend
      6. Mostra um resumo dizendo se tudo funcionou ou o que falhou

    Node, npm e Maven nao precisam estar instalados: o projeto ja traz o
    Maven Wrapper e o frontend-maven-plugin baixa o Node dentro da pasta do projeto.

    Uso (de preferencia em um PowerShell como administrador):
        powershell -ExecutionPolicy Bypass -File .\setup-ambiente.ps1

    Parametros:
        -PularBuild    nao compila nem instala o frontend, so configura Java e banco
        -Simular       mostra o que seria feito sem alterar nada na maquina
                       (nao grava variaveis, nao instala, nao sobe container, nao compila)
#>

param(
    [switch]$PularBuild,
    [switch]$Simular
)

# Sem 'Stop' global: o script tenta todas as etapas e resume as falhas no fim.
$ErrorActionPreference = 'Continue'

# Versoes aceitas pelo maven-enforcer-plugin no pom.xml, em ordem de preferencia.
$VERSOES_ACEITAS = @('17', '21', '24')

$SENHA_MYSQL = '31415'
$NOME_BANCO = 'DIGITADO'
$COMPOSE_MYSQL = 'src/main/docker/mysql.yml'

# Portas liberadas no firewall para que outras maquinas da rede local alcancem
# a aplicacao: 8080 e o back-end (Spring Boot) e 9000 e o front-end de
# desenvolvimento. O MySQL nao entra na lista de proposito: o compose publica a
# porta 3306 apenas em 127.0.0.1, entao o banco nao fica exposto na rede.
$PORTAS_FIREWALL = @(
    @{ Porta = 8080; Descricao = 'back-end (API Spring Boot)' }
    @{ Porta = 9000; Descricao = 'front-end (servidor de desenvolvimento)' }
)

$etapas = [System.Collections.Generic.List[object]]::new()

function Escrever($texto, $cor = 'White') { Write-Host $texto -ForegroundColor $cor }

function Simulado($descricao) { Escrever "  [SIMULACAO] $descricao" DarkGray }

function Registrar($nome, $ok, $detalhe = '') {
    $etapas.Add([pscustomobject]@{ Etapa = $nome; Ok = $ok; Detalhe = $detalhe })
    if ($ok) {
        Escrever "  [OK] $nome" Green
        if ($detalhe) { Escrever "       $detalhe" DarkGray }
    } else {
        Escrever "  [FALHOU] $nome" Red
        if ($detalhe) { Escrever "         $detalhe" DarkYellow }
    }
}

function Testar-Administrador {
    $identidade = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identidade)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Existe-Comando($nome) {
    return [bool](Get-Command $nome -ErrorAction SilentlyContinue)
}

function Porta-Ocupada($porta) {
    $cliente = New-Object Net.Sockets.TcpClient
    try {
        $conexao = $cliente.BeginConnect('127.0.0.1', $porta, $null, $null)
        $ok = $conexao.AsyncWaitHandle.WaitOne(1000)
        if ($ok) { $cliente.EndConnect($conexao) }
        return $ok
    } catch {
        return $false
    } finally {
        $cliente.Close()
    }
}

# Procura um JDK compativel (com javac) nos diretorios usuais de instalacao.
# O enforcer do pom.xml aceita [17,18),[21,22),[24,25) - ou seja, so as LTS.
# Um JDK 20, por exemplo, e recusado mesmo estando entre 17 e 24.
function Procurar-Jdk {
    $bases = @(
        "$env:ProgramFiles\Eclipse Adoptium"
        "$env:ProgramFiles\Java"
        "$env:ProgramFiles\Microsoft"
        "$env:ProgramFiles\Zulu"
        "$env:ProgramFiles\Amazon Corretto"
        "$env:ProgramFiles\BellSoft"
    )

    $encontrados = @{}
    foreach ($base in $bases) {
        if (-not (Test-Path $base)) { continue }
        foreach ($pasta in Get-ChildItem $base -Directory -ErrorAction SilentlyContinue) {
            $exe = Join-Path $pasta.FullName 'bin\java.exe'
            if (-not (Test-Path $exe)) { continue }
            # Um JRE nao serve: precisamos do compilador.
            if (-not (Test-Path (Join-Path $pasta.FullName 'bin\javac.exe'))) { continue }
            $saida = & $exe -version 2>&1 | Out-String
            foreach ($v in $VERSOES_ACEITAS) {
                if ($saida -match "`"$v\." -and -not $encontrados.ContainsKey($v)) {
                    $encontrados[$v] = $pasta.FullName
                }
            }
        }
    }

    # Preferencia pela ordem de $VERSOES_ACEITAS (17 primeiro, que e o alvo da compilacao).
    foreach ($v in $VERSOES_ACEITAS) {
        if ($encontrados.ContainsKey($v)) {
            return [pscustomobject]@{ Caminho = $encontrados[$v]; Versao = $v }
        }
    }
    return $null
}

# Le e grava o Path direto no registro, sem expandir variaveis, para nao
# destruir entradas como %SystemRoot% ao gravar de volta.
function Caminho-Registro($escopo) {
    if ($escopo -eq 'Machine') {
        return 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment'
    }
    return 'HKCU:\Environment'
}

function Ler-PathBruto($escopo) {
    return (Get-Item (Caminho-Registro $escopo)).GetValue('Path', '', 'DoNotExpandEnvironmentNames')
}

function Gravar-PathBruto($escopo, $valor) {
    Set-ItemProperty -Path (Caminho-Registro $escopo) -Name Path -Value $valor -Type ExpandString
}

# Devolve o Path com o bin do JDK na primeira posicao, sem duplicar entradas.
function Reordenar-Path($pathBruto, $binJdk) {
    $entradas = $pathBruto -split ';' | Where-Object { $_ -ne '' }
    $entradas = $entradas | Where-Object { $_.TrimEnd('\') -ne $binJdk.TrimEnd('\') }
    return (@($binJdk) + $entradas) -join ';'
}

Escrever ""
Escrever "==================================================" Cyan
Escrever "   Preparacao do ambiente DIGITADO" Cyan
Escrever "==================================================" Cyan

if ($Simular) {
    Escrever "`nMODO SIMULACAO: nada sera instalado nem alterado nesta maquina." Magenta
}

$admin = Testar-Administrador
$escopo = if ($admin) { 'Machine' } else { 'User' }
if (-not $admin) {
    Escrever "`nAviso: rodando sem privilegios de administrador." Yellow
    Escrever "As variaveis serao gravadas apenas para o seu usuario." Yellow
}

Push-Location $PSScriptRoot

# =====================================================================  JAVA
Escrever "`n[1/5] Java (aceitos: $($VERSOES_ACEITAS -join ', '))" Cyan

$achado = Procurar-Jdk
$jdk = if ($achado) { $achado.Caminho } else { $null }

if ($achado) {
    Escrever "  JDK $($achado.Versao) ja instalado - nada a baixar." Green
} else {
    if (Existe-Comando 'winget') {
        if ($Simular) {
            Simulado 'winget install EclipseAdoptium.Temurin.17.JDK'
        } else {
            Escrever "  Nenhum JDK compativel encontrado. Instalando Eclipse Temurin 17..." Yellow
            winget install --id EclipseAdoptium.Temurin.17.JDK --exact `
                --accept-source-agreements --accept-package-agreements
            $achado = Procurar-Jdk
            $jdk = if ($achado) { $achado.Caminho } else { $null }
        }
    } else {
        Escrever "  Nenhum JDK compativel e winget indisponivel nesta maquina." Yellow
    }
}

if ($jdk) {
    # O instalador da Oracle coloca ...\Common Files\Oracle\Java\javapath no inicio
    # do Path do sistema, o que faz aquele java vencer o JDK 17.
    $binJdk = Join-Path $jdk 'bin'
    $entradas = (Ler-PathBruto $escopo) -split ';' | Where-Object { $_ -ne '' }

    if ($Simular) {
        Simulado "JAVA_HOME ($escopo) passaria a ser $jdk"
        Simulado "Path ($escopo) passaria a comecar com $binJdk"
    } else {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, $escopo)
        $env:JAVA_HOME = $jdk
        Gravar-PathBruto $escopo (Reordenar-Path (Ler-PathBruto $escopo) $binJdk)
        $env:Path = "$binJdk;$env:Path"
    }

    Registrar "JDK $($achado.Versao) configurado" $true $jdk

    $javapath = $entradas | Where-Object { $_ -like '*Oracle\Java\javapath*' }
    if ($javapath -and -not $admin) {
        Escrever "  Aviso: existe um javapath da Oracle no Path do sistema, que e lido" Yellow
        Escrever "  antes do Path do usuario. Rode como administrador para corrigir." Yellow
    }
} else {
    Registrar 'JDK configurado' $false `
        'Instale o JDK 17 em https://adoptium.net/temurin/releases/?version=17 e rode o script de novo'
}

# =====================================================================  BANCO
Escrever "`n[2/5] Banco de dados MySQL" Cyan

$bancoOk = $false

if (Porta-Ocupada 3306) {
    # Ja existe algo na porta. Se for o container do projeto, esta tudo certo.
    $nosso = $false
    if (Existe-Comando 'docker') {
        $rodando = docker compose -f $COMPOSE_MYSQL ps -q mysql 2>$null
        $nosso = [bool]$rodando
    }
    if ($nosso) {
        Registrar 'MySQL disponivel em localhost:3306' $true 'container do projeto ja em execucao'
    } else {
        Registrar 'MySQL disponivel em localhost:3306' $true `
            'ja havia um servico na porta 3306 - confira se o usuario root com senha 31415 e o banco DIGITADO existem'
    }
    $bancoOk = $true
} elseif (Existe-Comando 'docker') {
    docker info 2>&1 | Out-Null
    if ($Simular) {
        if ($LASTEXITCODE -eq 0) {
            Simulado "docker compose -f $COMPOSE_MYSQL up -d"
            Simulado "banco $NOME_BANCO em 127.0.0.1:3306 com root / $SENHA_MYSQL"
        } else {
            Registrar 'MySQL via Docker' $false `
                'o Docker esta instalado mas nao esta em execucao - abra o Docker Desktop e rode o script de novo'
        }
    } elseif ($LASTEXITCODE -ne 0) {
        Registrar 'MySQL via Docker' $false `
            'o Docker esta instalado mas nao esta em execucao - abra o Docker Desktop e rode o script de novo'
    } else {
        Escrever "  Subindo o MySQL (pode demorar no primeiro uso)..." Yellow
        docker compose -f $COMPOSE_MYSQL up -d 2>&1 | Out-Null

        if ($LASTEXITCODE -ne 0) {
            Registrar 'MySQL via Docker' $false "nao foi possivel subir o container - tente: docker compose -f $COMPOSE_MYSQL up"
        } else {
            $id = (docker compose -f $COMPOSE_MYSQL ps -q mysql 2>$null | Select-Object -First 1)
            Escrever "  Aguardando o MySQL aceitar conexoes..." Yellow
            $pronto = $false
            foreach ($tentativa in 1..60) {
                docker exec $id mysqladmin ping -h 127.0.0.1 -u root "-p$SENHA_MYSQL" 2>&1 | Out-Null
                if ($LASTEXITCODE -eq 0) { $pronto = $true; break }
                Start-Sleep -Seconds 2
            }
            if ($pronto) {
                Registrar 'MySQL via Docker' $true "banco $NOME_BANCO pronto em localhost:3306 (root / $SENHA_MYSQL)"
                $bancoOk = $true
            } else {
                Registrar 'MySQL via Docker' $false `
                    "o container subiu mas nao respondeu em 2 minutos - veja: docker compose -f $COMPOSE_MYSQL logs"
            }
        }
    }
} else {
    Registrar 'MySQL' $false `
        'nem Docker nem MySQL encontrados - instale o Docker Desktop (winget install Docker.DockerDesktop) ou um MySQL local com usuario root, senha 31415 e banco DIGITADO'
}

# =================================================================  FIREWALL
Escrever "`n[3/5] Firewall" Cyan

foreach ($item in $PORTAS_FIREWALL) {
    $porta = $item.Porta
    $nomeRegra = "DIGITADO - porta $porta"
    $etapa = "Porta $porta liberada no firewall"

    # Perfil Private apenas: libera a rede local (casa, escola) sem expor a
    # aplicacao em redes publicas, como wi-fi de aeroporto ou cafe.
    if ($Simular) {
        Simulado "regra de entrada TCP $porta (perfil Private) - $($item.Descricao)"
        continue
    }

    $existente = Get-NetFirewallRule -DisplayName $nomeRegra -ErrorAction SilentlyContinue
    if ($existente) {
        Registrar $etapa $true 'regra ja existia'
        continue
    }

    if (-not $admin) {
        Registrar $etapa $false `
            'criar regra de firewall exige administrador - reabra o PowerShell como administrador'
        continue
    }

    try {
        New-NetFirewallRule -DisplayName $nomeRegra `
            -Description "DIGITADO: $($item.Descricao)" `
            -Direction Inbound -Action Allow -Protocol TCP `
            -LocalPort $porta -Profile Private -ErrorAction Stop | Out-Null
        Registrar $etapa $true 'entrada TCP permitida no perfil Private (rede local)'
    } catch {
        Registrar $etapa $false $_.Exception.Message
    }
}

# ==================================================================  BACKEND
Escrever "`n[4/5] Backend (Maven)" Cyan

if ($Simular) {
    Simulado '.\mvnw.cmd -ntp clean package -DskipTests'
} elseif ($PularBuild) {
    Escrever "  Ignorado por causa do parametro -PularBuild." Yellow
} elseif (-not $jdk) {
    Registrar 'Compilacao do backend' $false 'depende de um JDK compativel, que nao foi configurado'
} elseif (-not (Test-Path '.\mvnw.cmd')) {
    Registrar 'Compilacao do backend' $false 'mvnw.cmd nao encontrado - rode o script de dentro da pasta do projeto'
} else {
    $logMaven = Join-Path $PSScriptRoot 'setup-maven.log'
    Escrever "  Compilando (a primeira vez baixa as dependencias e pode demorar)..." Yellow
    & .\mvnw.cmd -ntp clean package -DskipTests *>&1 | Tee-Object -FilePath $logMaven | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Registrar 'Compilacao do backend' $true
    } else {
        $erro = Select-String -Path $logMaven -Pattern '^\[ERROR\]' |
            Select-Object -First 3 | ForEach-Object { $_.Line.Trim() }
        Registrar 'Compilacao do backend' $false "$($erro -join ' | ') (log completo: setup-maven.log)"
    }
}

# =================================================================  FRONTEND
Escrever "`n[5/5] Frontend (npm)" Cyan

if ($Simular) {
    Simulado '.\npmw.cmd install'
} elseif ($PularBuild) {
    Escrever "  Ignorado por causa do parametro -PularBuild." Yellow
} elseif (-not (Test-Path '.\npmw.cmd')) {
    Registrar 'Dependencias do frontend' $false 'npmw.cmd nao encontrado'
} else {
    $logNpm = Join-Path $PSScriptRoot 'setup-npm.log'
    Escrever "  Instalando pacotes do package.json..." Yellow
    & .\npmw.cmd install *>&1 | Tee-Object -FilePath $logNpm | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Registrar 'Dependencias do frontend' $true
    } else {
        Registrar 'Dependencias do frontend' $false 'veja o log completo em setup-npm.log'
    }
}

Pop-Location

# ==================================================================  RESUMO
Escrever ""
Escrever "==================================================" Cyan
Escrever "   Resumo" Cyan
Escrever "==================================================" Cyan

# @(...) e obrigatorio: com uma unica falha o resultado seria um objeto escalar,
# e .Count em um PSCustomObject devolve $null em vez de 1.
$falhas = @($etapas | Where-Object { -not $_.Ok })

foreach ($etapa in $etapas) {
    $marca = if ($etapa.Ok) { 'OK     ' } else { 'FALHOU ' }
    $cor = if ($etapa.Ok) { 'Green' } else { 'Red' }
    Escrever "  $marca $($etapa.Etapa)" $cor
}

if ($Simular) {
    Escrever ""
    if ($falhas.Count -eq 0) {
        Escrever "  SIMULACAO CONCLUIDA - nada foi alterado na maquina." Magenta
        Escrever "  Rode sem -Simular para aplicar de verdade." Magenta
        Escrever ""
        exit 0
    }
    Escrever "  SIMULACAO CONCLUIDA - $($falhas.Count) etapa(s) falhariam:" Magenta
    foreach ($falha in $falhas) {
        Escrever "  * $($falha.Etapa)" Red
        if ($falha.Detalhe) { Escrever "    $($falha.Detalhe)" DarkYellow }
    }
    Escrever ""
    exit 1
}

if ($falhas.Count -eq 0) {
    Escrever ""
    Escrever "  TUDO FUNCIONOU" Green
    Escrever ""
    Escrever "  Feche este terminal e abra outro (as variaveis de ambiente so sao"
    Escrever "  lidas quando o processo e criado). Depois, na pasta do projeto:"
    Escrever ""
    Escrever "    .\mvnw.cmd          # inicia o backend em http://localhost:8080" Cyan
    Escrever "    .\npmw.cmd start    # inicia o frontend em http://localhost:9000" Cyan
    Escrever ""
    # O IP e lido da interface da rota padrao. Pegar o primeiro adaptador da lista
    # traria enderecos de adaptadores virtuais (WSL, Hyper-V), que nao servem.
    $indice = (Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Sort-Object RouteMetric, ifMetric | Select-Object -First 1).InterfaceIndex
    $ip = if ($indice) {
        (Get-NetIPAddress -InterfaceIndex $indice -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Select-Object -First 1).IPAddress
    }
    if ($ip) {
        Escrever "  Na rede local, outras maquinas acessam por http://${ip}:8080"
        Escrever "  (a porta 9000 e do modo de desenvolvimento e pode responder so nesta maquina)."
        Escrever ""
    }
    exit 0
} else {
    Escrever ""
    Escrever "  ALGO NAO DEU CERTO - $($falhas.Count) etapa(s) com problema:" Red
    Escrever ""
    foreach ($falha in $falhas) {
        Escrever "  * $($falha.Etapa)" Red
        if ($falha.Detalhe) { Escrever "    $($falha.Detalhe)" DarkYellow }
    }
    Escrever ""
    exit 1
}
