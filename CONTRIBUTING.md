# Contribuindo com o SDB CodFlow

Obrigado pelo interesse! Este repositório é o código-fonte do **SDB CodFlow**, um mod do
Sketchware Pro com um agente de IA integrado. Contribuições são bem-vindas — de correção de
crash a tradução.

> **English speakers:** this guide is in Portuguese, but issues and pull requests in English
> are perfectly welcome. The essentials: fork, branch, keep changes in Java inside the `mod/`
> or `pro.sketchware` packages, use `feat:`/`fix:`/`refactor:` commit prefixes, and open a PR.

## Antes de tudo: a licença

Este projeto **não é open source** pela definição oficial — ele é *source-available*. Leia o
[LICENSE.md](LICENSE.md) antes de reaproveitar qualquer parte do código em outro projeto.
Ao enviar um PR, você concorda que sua contribuição seja distribuída sob os mesmos termos.

## Compilando

Você precisa de **JDK 17** e do Android SDK. O Android Studio é o caminho mais fácil.

```bash
git clone https://github.com/SketchDevBrasil/Sketchware-sdbcodflow.git
cd Sketchware-sdbcodflow
./gradlew assembleDebug
```

Para checar rapidamente se algo compila sem gerar o APK inteiro:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

O `local.properties` é gerado pelo Android Studio e está no `.gitignore` — nunca commite ele.

## Mapa do código

| Pacote | O que é |
| --- | --- |
| `a.a.a` | Código original do Sketchware, ofuscado. **Boa parte não está aqui** — vem pronta dos `.jar` em `app/libs/`. |
| `com.besome.sketch` | Telas originais do Sketchware (editor de design, lógica, compilação). |
| `mod.*` | Contribuições da comunidade ao Sketchware Pro, organizadas por autor. |
| `mod.sdb.agente` | O agente de IA do SDB CodFlow. É aqui que fica a maior parte do que é exclusivo deste mod. |
| `pro.sketchware` | Código novo e reescrito do Sketchware Pro. |

### Sobre o código que está dentro dos `.jar`

Classes como `a.a.a.eC` (dados do projeto), `a.a.a.hC`, `a.a.a.iC` e `a.a.a.kC` vêm compiladas
de `app/libs/a.a.a-important-classes.jar`. Você **não consegue editá-las** — só chamá-las.

Quando um crash aponta para uma dessas classes, dá para investigar assim:

```bash
cd /tmp && unzip -o caminho/para/a.a.a-important-classes.jar 'a/a/a/eC.class'
javap -p -c -classpath . a.a.a.eC > eC.txt
```

O bytecode é legível o suficiente para achar a linha do problema. A correção, nesses casos,
tem que ser **antes** da chamada: sanear os dados ou proteger o ponto de entrada. Veja
`mod.sdb.agente.SdbProjectIntegrityGuard` como exemplo desse padrão.

## Regras para mudanças

1. **Prefira Java.** O projeto compila Kotlin, mas a base é Java. Use Kotlin só se for
   realmente necessário.
2. **Código novo vai em `pro.sketchware` ou `mod.sdb.agente`.** Evite espalhar mudanças pelas
   classes originais quando der para isolar.
3. **Respeite a estrutura de pastas e a nomenclatura** dos arquivos vizinhos.
4. **Não commite artefatos de build** (`*.apk`, logs, `build/`). Já estão no `.gitignore`.
5. **Nunca commite chaves de API, tokens ou keystores.** As chaves do agente de IA são
   digitadas pelo usuário no app e ficam no dispositivo dele — nada de valor default no código.

## Commits

Use um prefixo que descreva o tipo da mudança:

```
feat: adiciona exportação de projeto para ZIP
fix: corrige crash ao salvar layout sem parent
refactor: reorganiza SdbEditEngine
docs: atualiza README
```

Escreva a mensagem explicando **por que**, não só o que mudou. Se o commit corrige um crash,
cole o stack trace relevante no corpo da mensagem — isso ajuda muito quem for investigar
depois.

## Abrindo um Pull Request

1. Faça um fork do repositório.
2. Crie uma branch: `git checkout -b fix/nome-curto`.
3. Faça a mudança e **teste no aparelho ou emulador** — não basta compilar.
4. Abra o PR descrevendo o que mudou e como você testou.

PRs pequenos e focados são revisados muito mais rápido do que um PR gigante com dez coisas
diferentes.

## Reportando bugs

Use o [template de bug](https://github.com/SketchDevBrasil/Sketchware-sdbcodflow/issues/new?template=bug_report.yml).
Se o app crashou, **cole o relatório de crash inteiro** — incluindo o cabeçalho com versão,
modelo e Android. O stack trace sozinho já resolve metade dos casos.

## Encontrou uma falha de segurança?

Não abra uma issue pública. Veja o [SECURITY.md](SECURITY.md).
