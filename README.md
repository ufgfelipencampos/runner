# Runner

Projeto em construcao para orquestrar CLIs multiplataforma e JARs Java do Sistema Runner.

## Direcao atual

- Os CLIs serao implementados em Go.
- Neste incremento, somente o `assinador-verificador.jar` foi iniciado.
- O foco atual do JAR e o modo `one-time` para `sign` e `validate`.

## Estrutura

- `assinador-verificador/`: modulo Java inicial do assinador.

## Como validar o modulo atual

```powershell
powershell -ExecutionPolicy Bypass -File .\assinador-verificador\build.ps1
powershell -ExecutionPolicy Bypass -File .\assinador-verificador\test.ps1
```

