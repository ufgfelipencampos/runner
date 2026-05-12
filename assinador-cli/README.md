# assinador-cli

CLI em Go para orquestrar o `assinador-verificador.jar` com comandos em portugues.

## Escopo desta versao

- cobre `assinar`, `verificar` e `servidor iniciar|status|parar`
- assume Java e JAR ja disponiveis localmente
- traduz o vocabulario do CLI para o contrato atual do JAR

## Contrato de modos

- CLI Go:
  - `--modo direto`
  - `--modo http`
- JAR Java:
  - `--mode direct`
  - `--mode http`

O `assinador-cli` faz esse mapeamento automaticamente.

## Defaults

- `--java-bin`: `java`
- `--jar`: `./assinador-verificador/build/dist/assinador-verificador.jar`
- `--porta`: `8080`

## Exemplos

### Assinar em modo direto

```powershell
assinador-cli assinar `
  --entrada .\examples\entrada.json `
  --saida .\build\saida\assinado.json `
  --modo direto `
  --alias demo-signer
```

### Assinar via HTTP

```powershell
assinador-cli assinar `
  --entrada .\examples\entrada.json `
  --saida .\build\saida\assinado.json `
  --modo http `
  --porta 8080 `
  --alias demo-signer `
  --biblioteca-pkcs11 token.dll `
  --slot-pkcs11 0
```

### Verificar em modo direto

```powershell
assinador-cli verificar `
  --entrada .\build\saida\assinado.json `
  --saida .\build\saida\resultado-validacao.json `
  --modo direto
```

### Iniciar servidor

```powershell
assinador-cli servidor iniciar --porta 8080
```

### Consultar status e parar

```powershell
assinador-cli servidor status --porta 8080
assinador-cli servidor parar --porta 8080
```

## Desenvolvimento

```powershell
go test ./...
go build .
```
