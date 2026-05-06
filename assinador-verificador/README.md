# assinador-verificador

Modulo Java inicial para o `assinador-verificador.jar`.

## Escopo deste incremento

- comando `sign` em modo `one-time`
- comando `validate` em modo `one-time`
- comando `server` com `start`, `status` e `stop`
- validacao forte de flags e arquivos
- resposta JSON padronizada em stdout e no arquivo de saida
- simulacao deterministica de assinatura via digest SHA-256

## Contrato de uso

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

Flags obrigatorias: `--pathin`, `--pathout`, `--mode`, `--alias`.
Flags opcionais: `--pkcs11-lib`, `--pkcs11-slot`.

Resposta JSON de sucesso:

```json
{
  "status": "SUCCESS",
  "operation": "sign",
  "mode": "one-time",
  "message": "Assinatura simulada gerada com sucesso.",
  "inputDigestSha256": "<sha256-hex-do-arquivo-de-entrada>",
  "signature": "SIMULATED-SIGNATURE-<sha256-hex>",
  "signedBy": "<alias>",
  "pkcs11Library": "<lib-ou-not-informed>",
  "pkcs11Slot": "<slot-ou-not-informed>",
  "generatedAt": "<timestamp-iso-8601>"
}
```

### Validate

```powershell
java -jar .\build\dist\assinador-verificador.jar `
  validate `
  --pathin .\build\saida\assinado.json `
  --pathout .\build\saida\resultado-validacao.json `
  --mode one-time
```

Flags obrigatorias: `--pathin`, `--pathout`, `--mode`.
O arquivo de entrada deve ser o JSON gerado pelo comando `sign` — nao o arquivo original.
As flags `--alias`, `--pkcs11-lib` e `--pkcs11-slot` nao sao aceitas por este comando.

Resposta JSON de sucesso:

```json
{
  "status": "SUCCESS",
  "operation": "validate",
  "mode": "one-time",
  "message": "Validacao simulada concluida.",
  "valid": true,
  "reason": "Assinatura simulada reconhecida e consistente com o digest.",
  "inputDigestSha256": "<sha256-hex-extraido-do-arquivo>",
  "signature": "<assinatura-extraida-do-arquivo>",
  "signedBy": "<alias-extraido-ou-unknown>",
  "checkedAt": "<timestamp-iso-8601>"
}
```

O campo `valid` sera `false` quando a assinatura presente no arquivo nao corresponder ao digest.

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

A flag `--port` e opcional em `status` e `stop`. Em `start`, se omitida, o valor padrao e `8080`.

Respostas JSON:

**start:**
```json
{
  "status": "SUCCESS",
  "operation": "server-start",
  "port": 8080,
  "message": "Servidor simulado iniciado.",
  "startedAt": "<timestamp-iso-8601>"
}
```

**status:**
```json
{
  "status": "SUCCESS",
  "operation": "server-status",
  "port": 8080,
  "running": true,
  "checkedAt": "<timestamp-iso-8601>"
}
```

**stop:**
```json
{
  "status": "SUCCESS",
  "operation": "server-stop",
  "port": 8080,
  "message": "Servidor simulado encerrado.",
  "stoppedAt": "<timestamp-iso-8601>"
}
```

## Exit codes

| Codigo | Constante        | Significado                                                  |
|--------|------------------|--------------------------------------------------------------|
| `0`    | `SUCCESS`        | Operacao concluida com sucesso.                              |
| `1`    | `RUNTIME_ERROR`  | Erro inesperado em tempo de execucao.                        |
| `2`    | `VALIDATION_ERROR` | Argumento invalido, arquivo ausente ou conteudo incorreto. |
| `3`    | `SERVER_RUNNING` | Servidor iniciado. O processo permanece ativo intencionalmente. |

O codigo `3` e retornado exclusivamente por `server start`. Neste caso o processo nao termina — ele mantém o servidor em execucao em background ate que `server stop` seja invocado.

## Premissas desta versao

- `sign` exige um JSON de entrada nao vazio contendo o campo `"resourceType"`.
- `validate` espera como entrada um JSON gerado pelo comando `sign`, nao o arquivo original. O arquivo deve conter os campos `"signature"` e `"inputDigestSha256"`.
- `--mode` aceita apenas `one-time` neste momento.
- `--alias` e obrigatorio somente em `sign`.
- `--pkcs11-lib` e `--pkcs11-slot` sao opcionais em `sign` e nao aceitos em `validate`.
- Tanto `--pathin` quanto `--pathout` devem apontar para arquivos com extensao `.json`.

## Resposta de erro

Qualquer erro retorna JSON no stderr com a seguinte estrutura:

```json
{
  "status": "ERROR",
  "type": "VALIDATION_ERROR",
  "message": "<descricao-do-problema>"
}
```

O campo `type` pode ser `VALIDATION_ERROR` (exit 2) ou `RUNTIME_ERROR` (exit 1).

## Scripts locais

- `build.ps1`: compila as classes e empacota o JAR.
- `test.ps1`: compila o modulo e executa os testes sem dependencias externas.
