# assinador-verificador

Modulo Java inicial para o `assinador-verificador.jar`.

## Escopo deste incremento

- comando `sign` em modo `one-time`
- comando `validate` em modo `one-time`
- comando `server` com `start`, `status` e `stop`
- validacao forte de flags e arquivos
- resposta JSON padronizada
- simulacao deterministica de assinatura via digest SHA-256

O modo `server` agora e suportado de forma simulada para controlar o ciclo de vida do servico.

## Contrato atual

### Sign

```powershell
java -jar .\build\dist\assinador-verificador.jar `
  sign `
  --pathin .\examples\fhir-bundle.json `
  --pathout .\build\saida\assinado.json `
  --mode one-time `
  --alias demo-signer `
  --pkcs11-lib token.dll `
  --pkcs11-slot 0
```

### Validate

```powershell
java -jar .\build\dist\assinador-verificador.jar `
  validate `
  --pathin .\build\saida\assinado.json `
  --pathout .\build\saida\resultado-validacao.json `
  --mode one-time
```

### Server

```powershell
java -jar .\build\dist\assinador-verificador.jar `
  server start `
  --port 8080

java -jar .\build\dist\assinador-verificador.jar `
  server status `
  --port 8080

java -jar .\build\dist\assinador-verificador.jar `
  server stop `
  --port 8080
```

## Premissas desta primeira versao

- `sign` exige um JSON de entrada nao vazio contendo o campo `"resourceType"`.
- `validate` espera como entrada um JSON previamente gerado pelo comando `sign`.
- `--mode` aceita apenas `one-time` neste momento.
- `--alias` e obrigatorio em `sign`.
- `--pkcs11-lib` e `--pkcs11-slot` sao opcionais e, por enquanto, usados apenas para validacao e rastreabilidade da simulacao.

## Scripts locais

- `build.ps1`: compila as classes e empacota o JAR.
- `test.ps1`: compila o modulo e executa os testes sem dependencias externas.

