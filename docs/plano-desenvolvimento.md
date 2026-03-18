# Plano de Desenvolvimento — Sistema Runner

## 1. Objetivo do plano

Definir uma estratégia incremental para implementar o **Sistema Runner** com foco em:
- CLI multiplataforma para assinatura e gestão do simulador;
- integração com `assinador.jar` em modo direto e HTTP;
- validação robusta de parâmetros e mensagens de erro úteis;
- automação de provisionamento de JDK e download de artefatos.

## 2. Base de referência

Este plano foi elaborado a partir dos documentos:
- `especificacao.md` (escopo, histórias de usuário, critérios de aceitação e entregáveis);
- `design.md` (contexto C4 e contêineres).

## 3. Premissas de implementação

1. A solução será orientada a **CLI** (sem GUI).
2. `assinador.jar` e `simulador.jar` são tratados como dependências executáveis externas.
3. O Runner deve operar em **Windows, Linux e macOS**.
4. A assinatura e validação digital são **simuladas**, mas com validação rigorosa de parâmetros.
5. Distribuição e versionamento seguem SemVer, com publicação em releases.

## 4. Arquitetura de alto nível (implementação)

### 4.1 Módulos principais
- **CLI Core**: parser de comandos, help e roteamento.
- **Assinador Client**: invocação local (processo Java) e remota (HTTP).
- **Simulador Manager**: start/stop/status, verificação de portas e versionamento local.
- **Runtime Manager**: descoberta/provisionamento de JDK e execução Java.
- **Artifact Manager**: checagem de versão e download de jars (cache local).
- **Validation + Error Layer**: validação de entrada e padronização de erros.
- **Observability Layer**: logs estruturados e códigos de saída.

### 4.2 Princípios
- Separar regras de negócio de integração com sistema operacional.
- Garantir idempotência em operações de setup (download/provisionamento).
- Expor mensagens claras para usuário final e logs detalhados para diagnóstico.

## 5. Roadmap por fases

## Fase 0 — Fundação do projeto
**Meta:** estabelecer base técnica e estrutura modular.

**Entregas:**
- Estrutura de diretórios e módulos.
- Convenções de logging e tratamento de erro.
- Contrato de comandos CLI (esqueleto de comandos e help).

**Critério de pronto:**
- CLI inicial executando com `--help`, `--version` e códigos de saída padronizados.

## Fase 1 — US-01 (invocar assinador via CLI)
**Meta:** permitir criação/validação via `assinador.jar`.

**Entregas:**
- Comandos de assinatura e validação.
- Adaptador de invocação local (execução Java com argumentos).
- Adaptador HTTP (cliente com timeout/retry controlado).
- Formatação legível do retorno para terminal.

**Critério de pronto:**
- Mesma operação disponível em dois modos (local e HTTP) com comportamento equivalente.

## Fase 2 — US-02 (validação e simulação)
**Meta:** robustez de parâmetros e contrato funcional do assinador.

**Entregas:**
- Regras de validação alinhadas às exigências da especificação.
- Catálogo de erros de entrada (com códigos e mensagens).
- Simulação de resultado de assinatura e validação.
- Tratamento de exceções entre camadas.

**Critério de pronto:**
- Entradas inválidas retornam mensagens claras e estruturadas; entradas válidas retornam simulação prevista.

## Fase 3 — US-03 (ciclo de vida do simulador)
**Meta:** controlar `simulador.jar` via CLI.

**Entregas:**
- Comandos `start`, `stop` e `status`.
- Verificação de portas antes de iniciar.
- Download da última versão do simulador quando necessário.
- Reuso da versão local quando já atualizada.

**Critério de pronto:**
- Operações de ciclo de vida funcionando sem intervenção manual no Java.

## Fase 4 — US-04 (provisionamento automático de JDK)
**Meta:** reduzir dependência de configuração manual do ambiente.

**Entregas:**
- Descoberta de JDK compatível no host.
- Download e instalação de JDK quando ausente/incompatível.
- Seleção de runtime por plataforma e arquitetura.

**Critério de pronto:**
- Usuário consegue executar comandos sem instalar Java manualmente.

## Fase 5 — Qualidade, release e segurança
**Meta:** elevar confiabilidade e preparar entrega final.

**Entregas:**
- Testes unitários, integração e aceitação por história.
- Pipeline CI para build/test/package.
- Empacotamento multiplataforma.
- Assinatura de artefatos com Cosign (`.sig` e `.pem`).

**Critério de pronto:**
- Release reproduzível com artefatos assinados e documentação completa.

## 6. Estratégia de testes

### 6.1 Testes unitários
- Parser de CLI e validação de parâmetros.
- Mapeamento de erros e códigos de saída.
- Regras de seleção de runtime e versão.

### 6.2 Testes de integração
- Execução real de comandos contra `assinador.jar` (local/HTTP).
- Gestão de processo do `simulador.jar`.
- Fluxo de download/versionamento de artefatos.

### 6.3 Testes de aceitação
- Cenários derivados diretamente dos critérios das US-01 a US-04.
- Casos de erro: parâmetros inválidos, porta em uso, jar indisponível, timeout.

## 7. Gestão de riscos

| Risco | Impacto | Mitigação |
|------|---------|-----------|
| Diferenças entre SOs na execução de processos | Alto | Camada de abstração por plataforma + testes cruzados |
| Falhas de rede para download de jar/JDK | Médio | Retry com backoff e cache local |
| Inconsistência entre modo local e HTTP | Alto | Testes de contrato com suíte comum |
| Mensagens de erro pouco acionáveis | Médio | Padrão único de erro com causa e ação sugerida |

## 8. Critérios de sucesso

- Cobrir integralmente os critérios de aceitação das histórias de usuário.
- Operação estável em Windows/Linux/macOS.
- Fluxo de uso com baixa fricção para usuário final (sem setup manual complexo).
- Entrega final com documentação, testes e artefatos assinados.

## 9. Backlog técnico sugerido (priorizado)

1. Esqueleto CLI e contrato de comandos.
2. Invocação local do assinador.
3. Invocação HTTP do assinador.
4. Camada de validação de parâmetros e erros.
5. Gestão de simulador (start/stop/status).
6. Download/versionamento de `simulador.jar`.
7. Provisionamento automático de JDK.
8. Pipeline CI/CD e assinatura de artefatos.
