<#
    Copia o projeto DIGITADO para um pendrive (ou qualquer pasta de destino),
    deixando de fora o que se regenera sozinho na outra maquina.

    Sao ignorados:
      node_modules  - 374 MB e 68 mil arquivos; o 'npmw install' recria
      target        - resultado de build do Maven
      *.log         - logs locais

    O codigo-fonte tem cerca de 3 MB, entao a copia e rapida.

    Uso:
        powershell -ExecutionPolicy Bypass -File .\copiar-para-pendrive.ps1 -Destino E:\
        acrescente -Simular para so listar, sem copiar
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$Destino,

    [switch]$Simular
)

# 'Continue', e nao 'Stop': no Windows PowerShell 5.1 o texto que o robocopy
# escreve na saida de erro (acesso negado, midia protegida) viraria excecao
# fatal, escondendo a mensagem util. O resultado e conferido pelo codigo de saida.
$ErrorActionPreference = 'Continue'

$origem = $PSScriptRoot

function Escrever($texto, $cor = 'White') { Write-Host $texto -ForegroundColor $cor }

Escrever ""
Escrever "==================================================" Cyan
Escrever "   Copia do projeto DIGITADO" Cyan
Escrever "==================================================" Cyan

# A verificacao vem antes de qualquer uso do caminho: com uma letra inexistente,
# ate o Join-Path falha, e o erro cru do PowerShell nao ajuda em nada.
if (-not (Test-Path $Destino)) {
    Escrever "`nERRO: destino '$Destino' nao encontrado." Red
    Escrever "Confira a letra do pendrive em 'Este Computador'." Red
    exit 1
}

$pasta = Join-Path $Destino 'DIGITADO'

# Sem o dump, a outra maquina comeca com o banco vazio - vale avisar a tempo.
if (-not (Test-Path (Join-Path $origem 'dados\banco-digitado.sql'))) {
    Escrever "`n  AVISO: nao existe dados\banco-digitado.sql." Yellow
    Escrever "  Sem ele a outra maquina comeca com o banco vazio (sem palavras," Yellow
    Escrever "  listas nem contas). Para levar os dados, cancele com Ctrl+C e rode:" Yellow
    Escrever "      powershell -ExecutionPolicy Bypass -File .\exportar-banco.ps1" Cyan
    Start-Sleep -Seconds 4
}

Escrever "`n  Origem:  $origem"
Escrever "  Destino: $pasta"

# Espaco livre no destino, quando for possivel descobrir.
$letra = ($Destino -replace ':.*', '')
$volume = Get-Volume -DriveLetter $letra -ErrorAction SilentlyContinue
if ($volume) {
    $livreMb = [math]::Round($volume.SizeRemaining / 1MB)
    Escrever "  Espaco livre: $livreMb MB ($($volume.FileSystem))"
    if ($livreMb -lt 50) {
        Escrever "`nERRO: espaco insuficiente no destino (menos de 50 MB livres)." Red
        exit 1
    }
}

# /E copia subpastas inclusive vazias; /XD e /XF excluem por nome em qualquer nivel;
# /L apenas lista, sem gravar nada.
$argumentos = @(
    $origem, $pasta, '/E'
    '/XD', 'node_modules', 'target', '.idea', '.vscode'
    '/XF', '*.log'
    '/NFL', '/NDL', '/NJH', '/NP', '/R:2', '/W:2'
)
if ($Simular) {
    $argumentos += '/L'
    Escrever "`n  MODO SIMULACAO: nada sera gravado." Magenta
}

Escrever "`n  Copiando..." Yellow
robocopy @argumentos | Out-Host

# O robocopy usa codigos de saida acumulativos: abaixo de 8 e sucesso.
$codigo = $LASTEXITCODE
if ($codigo -ge 8) {
    Escrever "`n  FALHOU - o robocopy retornou codigo $codigo" Red
    Escrever "  Copie manualmente a pasta, ignorando node_modules e target." DarkYellow
    exit 1
}

if ($Simular) {
    Escrever "`n  SIMULACAO CONCLUIDA - nada foi copiado." Magenta
    Escrever "  Rode sem -Simular para copiar de verdade.`n" Magenta
    exit 0
}

$copiado = Get-ChildItem $pasta -Recurse -Force -ErrorAction SilentlyContinue |
    Measure-Object Length -Sum
Escrever "`n  COPIA CONCLUIDA" Green
Escrever ("  {0:N0} arquivos, {1:N1} MB em {2}" -f $copiado.Count, ($copiado.Sum / 1MB), $pasta)
Escrever ""
Escrever "  Na outra maquina: copie a pasta DIGITADO do pendrive para o disco"
Escrever "  (por exemplo C:\Projetos\DIGITADO) e rode la dentro, como administrador:"
Escrever ""
Escrever "    powershell -ExecutionPolicy Bypass -File .\setup-ambiente.ps1" Cyan
Escrever ""

# Obrigatorio: sem isto o script herda o codigo do robocopy, que devolve 1
# quando copia arquivos com sucesso - e 1 seria lido como falha.
exit 0
