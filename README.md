# Runner

Projeto em construcao para orquestrar CLIs multiplataforma e JARs Java do Sistema Runner.

## Direcao atual

- Os CLIs serao implementados em Go.
- Neste incremento, o repositorio contem:
  - `assinador-verificador/`: modulo Java ja iniciado
  - `assinador-cli/`: primeiro CLI Go para orquestrar o JAR atual
- O foco atual do JAR cobre `sign`, `validate` e o ciclo de vida do `server` simulado.

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

O primeiro incremento do `assinador-cli` assume:

- Java disponivel localmente via `java` ou `--java-bin`
- `assinador-verificador.jar` disponivel localmente via `--jar`

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
assinador-cli servidor parar --porta 8080"
```

