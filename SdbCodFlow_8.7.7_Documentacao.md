# SdbCodFlow 8.7.7 — Documentação Completa

> Versão especial do Sketchware Pro com Agente de IA integrado desenvolvida por **Marcos Santos (SdbCodFlow)**.
> Data de lançamento: **17 de março de 2026**

---

## Índice

1. [O que é o SdbCodFlow?](#o-que-é-o-sdbcodflow)
2. [Visão Geral da Arquitetura](#visão-geral-da-arquitetura)
3. [Funcionalidades Implementadas](#funcionalidades-implementadas)
4. [Operações do Agente (JSON API)](#operações-do-agente-json-api)
5. [Contextos de Uso](#contextos-de-uso)
6. [Interface e UX](#interface-e-ux)
7. [Release Notes Técnicas](#release-notes-técnicas)
8. [Links](#links)

---

## O que é o SdbCodFlow?

O **SdbCodFlow** é uma modificação do Sketchware Pro que integra um **Agente de IA nativo** diretamente no ambiente de desenvolvimento. Em vez de copiar código de um chat externo e colar manualmente, o Agente lê, cria e edita seu projeto em tempo real — diretamente pelo chat dentro do app.

O Agente entende o contexto do seu projeto (qual tela, qual evento, qual arquivo XML) e aplica as mudanças automaticamente usando uma API de operações JSON própria.

---

## Visão Geral da Arquitetura

### Arquivos principais

| Arquivo | Responsabilidade |
|---|---|
| `SdbAgenteChatSheet.java` | BottomSheet do chat — UI, envio de mensagens, histórico, instruções ao modelo |
| `SdbEditEngine.java` | Motor de edição — recebe JSON do modelo e aplica no projeto |
| `SdbAgenteSk.java` | Helpers estáticos — linguagem, preferências, contexto |
| `SdbAgenteActivity.java` | Tela standalone do agente (usada em contextos legados) |
| `SdbProjectContext.java` | Coleta contexto do projeto para montar o prompt do sistema |
| `SdbSnapshotManager.java` | Gerencia histórico de conversas por projeto |

### Fluxo de funcionamento

```
Usuário digita mensagem
        ↓
SdbAgenteChatSheet monta o prompt (contexto + histórico + instruções)
        ↓
Chamada à API Claude (Anthropic)
        ↓
Resposta JSON com lista de "operations"
        ↓
SdbEditEngine.applyEdits() despacha cada operação
        ↓
Projeto modificado em tempo real (sem reiniciar a activity)
```

### Estrutura JSON de resposta do modelo

```json
{
  "operations": [
    {
      "op": "inject_code",
      "data": {
        "java_name": "MainActivity",
        "event_name": "onCreate",
        "code": "Toast.makeText(this, \"Hello!\", Toast.LENGTH_SHORT).show();"
      }
    }
  ]
}
```

---

## Funcionalidades Implementadas

### 1. Chat de IA em todos os contextos

O chat abre como um **BottomSheet** e está disponível em:

- **Logic Editor** (botão FAB no editor de blocos) — contexto automático: tela + evento selecionado
- **XML Editor / View Code Editor** (botão FAB) — contexto automático: arquivo XML da tela atual
- **Design Activity** (botão FAB) — abre o XML Editor com o chat já aberto
- **Tela inicial de projetos** (botão flutuante) — contexto global do projeto

### 2. Injeção de código Java

O Agente injeta código Java diretamente nos eventos do projeto usando o bloco nativo `addSourceDirectly`. Suporta:

- Qualquer evento existente (onCreate, onClick, etc.)
- Criação automática de eventos novos se ainda não existirem
- Adição de imports sem sobrescrever os existentes
- Anti-duplicação: detecta se já existe um bloco de IA no evento e atualiza em vez de duplicar

### 3. MoreBlocks via chat

| Operação | Descrição |
|---|---|
| `add_moreblock` | Cria um novo MoreBlock com spec e corpo |
| `update_moreblock` | Atualiza spec e/ou código de um MoreBlock existente |
| `delete_moreblock` | Remove o MoreBlock completamente |

O Agente entende automaticamente a qual tela o MoreBlock pertence pelo contexto do Logic Editor.

### 4. Drawables via chat

| Operação | Descrição |
|---|---|
| `add_drawable` | Cria ou substitui um drawable XML (shapes, gradientes, seletores) |
| `delete_drawable` | Remove um drawable do projeto |

Os drawables são salvos diretamente em `/.sketchware/data/{scId}/files/resource/drawable/`.

### 5. Paleta de blocos customizados via chat

| Operação | Descrição |
|---|---|
| `add_custom_block` | Cria bloco na paleta (cria a paleta se não existir) |
| `update_custom_block` | Atualiza definição de um bloco existente (por nome ou opCode) |
| `delete_custom_block` | Remove um bloco individual da paleta |
| `delete_palette` | Remove uma paleta inteira e todos os seus blocos (com correção automática de IDs) |

### 6. Design / Widgets via chat

| Operação | Descrição |
|---|---|
| `add_widget` | Adiciona um widget ao layout (com parent, index, atributos) |
| `update_widget` | Edita atributos de um widget existente |
| `remove_widget` | Remove um widget do layout |
| `edit_layout_xml` | Substitui o XML completo de uma tela (modo avançado) |

### 7. Suporte a imagens (screenshot → design)

O usuário pode enviar um screenshot no chat. O Agente analisa a imagem e aplica o design usando as operações de widget/drawable acima.

### 8. Histórico de conversas por projeto

- Cada projeto tem seu próprio histórico salvo automaticamente
- O histórico é mantido entre sessões (persistência em disco via `SdbSnapshotManager`)
- O Agente lembra do contexto de conversas anteriores do mesmo projeto

### 9. Interface bilíngue PT/EN

- Botão de troca de idioma no chat (PT 🇧🇷 / EN 🇺🇸)
- **Todo o texto do chat** se adapta ao idioma selecionado: botões, labels, instruções, toasts
- Preferência salva em SharedPreferences
- Helper `s(pt, en)` usado em todos os métodos do `SdbAgenteChatSheet`

---

## Operações do Agente (JSON API)

Referência completa de todas as operações disponíveis para o modelo usar:

### Código Java

```json
{ "op": "inject_code", "data": { "java_name": "MainActivity", "event_name": "onCreate", "code": "// seu código Java aqui" } }
{ "op": "add_import", "data": { "java_name": "MainActivity", "code": "import java.util.List;" } }
```

### MoreBlocks

```json
{ "op": "add_moreblock", "data": { "name": "meuMbr", "spec": "meuMbr %s.s", "code": "// java" } }
{ "op": "update_moreblock", "data": { "name": "meuMbr", "spec": "novo spec", "code": "// novo código" } }
{ "op": "delete_moreblock", "data": { "name": "meuMbr" } }
```

### Drawables

```json
{ "op": "add_drawable", "data": { "drawable_name": "bg_card", "xml_content": "<shape>...</shape>" } }
{ "op": "delete_drawable", "data": { "drawable_name": "bg_card" } }
```

### Paleta de Blocos Customizados

```json
{ "op": "add_custom_block", "data": { "palette_name": "Minha Paleta", "palette_color": "#FF0000", "blocks": [{ "name": "meuBloco", "spec": "meu bloco %s", "type": " ", "opCode": "m_op" }] } }
{ "op": "update_custom_block", "data": { "name": "meuBloco", "blocks": [{ "name": "meuBloco", "spec": "novo spec", "type": " ", "opCode": "m_op" }] } }
{ "op": "delete_custom_block", "data": { "name": "meuBloco" } }
{ "op": "delete_palette", "data": { "palette_name": "Minha Paleta" } }
```

### Widgets / Design

```json
{ "op": "add_widget", "xmlName": "main", "data": { "widget_id": "btn1", "widget_type": 3, "parent_id": "root", "attributes": { "android:text": "Clique aqui" } } }
{ "op": "update_widget", "xmlName": "main", "data": { "widget_id": "btn1", "attributes": { "android:text": "Novo texto", "android:background": "@drawable/bg_card" } } }
{ "op": "remove_widget", "xmlName": "main", "data": { "widget_id": "btn1" } }
{ "op": "edit_layout_xml", "xmlName": "main", "data": { "xml_content": "<LinearLayout>...</LinearLayout>" } }
```

**Tipos de widget:** `0`=Linear, `1`=Relative, `3`=Button, `4`=TextView, `8`=ImageView, `6`=EditText, `7`=ImageButton, `2`=ScrollView

---

## Contextos de Uso

### Logic Editor
- Contexto: nome da tela Java + evento selecionado
- Operações disponíveis: `inject_code`, `add_moreblock`, `update_moreblock`, `delete_moreblock`, `add_import`
- Operações bloqueadas: `add_custom_block` (global, não por projeto)

### XML Editor (View Code Editor)
- Contexto: arquivo XML da tela atual
- Botão "Layout Preview" disponível para visualizar o resultado
- Botão "Salvar Mudanças" aplica o código gerado pelo Agente diretamente no editor
- Todas as operações de widget/drawable disponíveis

### Design Activity
- Ao clicar no FAB do Agente, abre o XML Editor com o chat já aberto
- Permite editar o design da tela via linguagem natural

### Tela Principal de Projetos
- Contexto global (sem tela específica)
- Útil para criar estruturas, drawables globais, blocos de paleta

---

## Interface e UX

### Tela inicial de projetos

- **Bottom bar** com 4 links rápidos: Store, Telegram, YouTube, Updates
- Fundo sólido (`colorSurface`) com elevação — sem transparência nem mistura com a navigation bar do Android
- Insets do sistema aplicados corretamente via `ViewCompat.setOnApplyWindowInsetsListener` no root view
- FAB "New project" com margem dinâmica calculada em função da altura da barra + inset da navigation bar
- Botão Store sem efeito de seleção (ripple apenas nos outros)

### Changelog

- Exibido em **inglês por padrão**
- Botão flutuante **"PT BR"** no canto inferior direito para traduzir para português
- Ao traduzir, o botão muda para **"EN"** para indicar que pode voltar ao inglês
- Ícone de tradução (`ic_translate`) + label dinâmico via `ExtendedFloatingActionButton`

### Chat do Agente

- Balões de mensagem diferenciados para usuário e IA
- Ícone SdbCodFlow Sparkle consistente em todos os FABs do app
- Botão de imagem para enviar screenshot ao chat
- Indicador de digitação enquanto aguarda resposta
- Histórico rolável com persistência entre sessões

### Sobre o App (About)

- Seção "official AI sdb" com badge
- Versão **SdbCodFlow 8.7.7** com data de lançamento
- Links clicáveis para site, Telegram e YouTube
- Avatar personalizado do desenvolvedor na seção Team

---

## Release Notes Técnicas

### Bugs corrigidos nesta versão

| Bug | Causa | Solução |
|---|---|---|
| `update_moreblock` não atualizava | Combinado com `add_moreblock` em OR — `addMoreBlockAndInjectCode` pulava existentes | Handler separado chamando `updateMoreBlockAndCode()` |
| `save()` privado lançava exceção | `getMethod()` não acessa métodos privados | Mudado para `getDeclaredMethod()` + `setAccessible(true)` |
| Botão "Layout Preview" aparecia no Logic Editor | Condição usava `contextXmlName != null` (verdadeiro em ambos os contextos) | Condição mudada para `isCodeEditorMode` |
| Bottom bar transparente (misturada com nav bar) | `CoordinatorLayout` consumia insets antes de chegar na barra | Listener movido para `binding.getRoot()` (DrawerLayout) |
| `defaultContextName.contains("_")` sobrescrevia eventName | Condição em `applyOperation` usava nome de classe como eventName | Branch `else if` removido completamente |
| ANR ao abrir MoreBlock no Logic Editor | Operação de I/O na thread principal | Movido para thread de background com Handler |

### Novas classes e métodos

- `SdbEditEngine.updateMoreBlockAndCode()` — atualiza spec e corpo de MoreBlock existente
- `SdbEditEngine` handlers: `update_custom_block`, `delete_custom_block`, `delete_palette`
- `SdbAgenteSk.getLanguage(Context)` — helper estático de idioma
- `SdbAgenteChatSheet.s(pt, en)` — helper de string bilíngue

### Estrutura de dados das operações

```java
public static class OperationData {
    public String view_id, widget_id, id;
    public String parent_id, parent;
    public int widget_type, type, index;
    public Map<String, String> attributes;
    public Map<String, Object> params;
    public String drawable_name, xml_content;
    public String palette_name, palette_color;
    public List<Map<String, Object>> blocks;
    public String op_code;          // identificar bloco por opCode
    public String icon_name, style, color;
    public String target_view_id, target_xml_name;
    public String name, spec, code;
    public String java_name, event_name;
}
```

---

## Links

- **Site / Updates:** https://sketch-dev-brasil.web.app/sdbcodflow
- **Telegram:** https://t.me/sketchdevbrasil
- **YouTube:** https://youtube.com/@sketchdevbrasil
- **Store:** https://sketch-dev-brasil.web.app/home

---

*Desenvolvido por Marcos Santos — SdbCodFlow 8.7.7 — 2026*
