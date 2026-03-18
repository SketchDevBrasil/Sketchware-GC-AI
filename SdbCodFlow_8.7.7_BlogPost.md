# 🤖 SdbCodFlow 8.7.7 — O Agente de IA que edita seu projeto no Sketchware Pro

> update em 17 de março de 2026

---

Essa versão marca um ponto de virada: o **SdbCodFlow** agora é um **Agente de IA completo**, integrado diretamente no Sketchware Pro. Sem copiar e colar. Sem sair do app. Você conversa, ele edita o projeto.

---

## O que ele faz?

O Agente abre como um **chat dentro do app** e tem acesso real ao seu projeto. Ele pode:

### 🧠 Escrever e injetar código Java
Você descreve o que quer, ele escreve o código e injeta direto no evento correto da tela. Cria o evento automaticamente se não existir. Adiciona imports sem quebrar os que já tem.

### 🧱 Criar e editar MoreBlocks
```
Criar: add_moreblock
Editar spec e código: update_moreblock
Deletar: delete_moreblock
```
Tudo pelo chat, sem abrir o editor de MoreBlocks.

### 🎨 Criar e deletar Drawables
Shapes, gradientes, bordas arredondadas, seletores — o Agente escreve o XML e salva no projeto. Também pode deletar drawables existentes.

### 📦 Gerenciar a Paleta de Blocos Customizados
```
Criar bloco: add_custom_block
Editar bloco: update_custom_block
Deletar bloco: delete_custom_block
Deletar paleta inteira: delete_palette
```
Cria a paleta automaticamente se não existir. Ao deletar uma paleta, corrige os IDs de todas as paletas restantes automaticamente.

### 📐 Editar o Layout das Telas
Adicionar, editar e remover widgets pelo chat. Ou passar o XML completo de uma tela e ele aplica diretamente, preservando o histórico de undo.

### 📸 Enviar screenshot e aplicar o design
Manda uma foto de como quer que a tela fique. O Agente analisa e aplica o design.

---

## Onde o chat aparece?

| Local | Como abrir |
|---|---|
| **Logic Editor** | Botão FAB — contexto automático: tela + evento selecionado |
| **XML Editor** | Botão FAB — contexto automático: arquivo XML da tela |
| **Design da tela** | Botão FAB — abre o XML Editor com chat já aberto |
| **Tela inicial** | Botão flutuante — contexto global do projeto |

---

## Novidades de interface

- **Bottom bar** na tela inicial com links rápidos: Store, Telegram, YouTube, Updates — fundo sólido, sem misturar com a barra de navegação do Android
- **Changelog em inglês** por padrão, com botão **"PT BR"** para traduzir — ao clicar, muda para **"EN"** para voltar ao inglês
- **Chat bilíngue PT/EN** — todo o chat muda de idioma (botões, mensagens, labels)
- **Histórico salvo por projeto** — o Agente lembra das conversas anteriores

---

## Bugs corrigidos

- `update_moreblock` não atualizava nada — corrigido com handler dedicado
- Botão "Salvar Mudanças" dava erro de reflexão (`private save()`) — corrigido com `getDeclaredMethod`
- Botão "Layout Preview" aparecia no Logic Editor — corrigido para aparecer só no XML Editor
- Bottom bar estava transparente e misturada com a nav bar do Android — corrigido aplicando insets no root view
- ANR ao abrir MoreBlock no Logic Editor — corrigido movendo I/O para thread de background

---

## Links

- 🌐 **Site e atualizações:** https://sketch-dev-brasil.web.app/sdbcodflow
- 💬 **Telegram:** https://t.me/sketchdevbrasil
- ▶️ **YouTube:** https://youtube.com/@sketchdevbrasil
- 🛒 **Store:** https://sketch-dev-brasil.web.app/home

---

*Desenvolvido por Marcos Santos — SdbCodFlow 8.7.7*
