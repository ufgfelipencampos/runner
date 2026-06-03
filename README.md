# Runner

Projeto em construcao para orquestrar CLIs multiplataforma e JARs Java do Sistema Runner.

## Direcao atual

- Os CLIs serao implementados em Go.
- Neste incremento, o repositorio contem:
  - `assinador-verificador/`: modulo Java ja iniciado
  - `assinador-cli/`: primeiro CLI Go para orquestrar o JAR atual
- O foco atual do JAR cobre `sign`, `validate` e o ciclo de vida do `server` simulado.
- O `assinador-cli` provisiona automaticamente um runtime local do Temurin JRE 17 quando `--java-bin` nao e informado.

## Estrutura

- `assinador-verificador/`: modulo Java inicial do assinador.
- `assinador-cli/`: CLI em Go com comandos em portugues para o assinador.

## Contrato atual de modos

- O JAR Java hoje usa `direct` e `http`.
- O novo CLI Go expoe `direto` e `http`.
- O mapeamento e:
  - `direto` -> `direct`
  - `http` -> `http`

## Como validar o modulo atual

```powershell
powershell -ExecutionPolicy Bypass -File .\assinador-verificador\build.ps1
powershell -ExecutionPolicy Bypass -File .\assinador-verificador\test.ps1
```

## Como exercitar o CLI Go

O `assinador-cli` assume:

- `assinador-verificador.jar` disponivel localmente via `--jar`
- Java provisionado automaticamente em um diretorio local do usuario, ou sobrescrito por `--java-bin`

Exemplos:

```powershell
assinador-cli assinar `
  --entrada .\entrada.json `
  --saida .\assinado.json `
  --modo direto `
  --alias demo-signer

assinador-cli verificar `
  --entrada .\assinado.json `
  --saida .\resultado.json `
  --modo http `
  --porta 8080

assinador-cli servidor iniciar --porta 8080
assinador-cli servidor status --porta 8080
assinador-cli servidor parar --porta 8080
```

## Runtime Java gerenciado

- Runtime padrao: Temurin JRE 17 `jdk-17.0.18+8`
- Diretorio local compartilhado do Runner:
  - Windows: `%APPDATA%\runner`
  - Linux: `$XDG_CONFIG_HOME/runner` ou `~/.config/runner`
  - macOS: `~/Library/Application Support/runner`
- `--java-bin` continua disponivel como override explicito.
- Se o runtime local ficar corrompido, remova a pasta `runner\java\temurin-jre-17` correspondente e execute o comando novamente.

