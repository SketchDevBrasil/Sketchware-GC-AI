# Política de Segurança

## Versões suportadas

Apenas a versão mais recente publicada em
[Releases](https://github.com/SketchDevBrasil/Sketchware-sdbcodflow/releases) recebe correções.

## Reportando uma vulnerabilidade

**Não abra uma issue pública** para falhas de segurança.

Use o canal privado do GitHub: aba **Security → Report a vulnerability** neste repositório
([link direto](https://github.com/SketchDevBrasil/Sketchware-sdbcodflow/security/advisories/new)).
Só o mantenedor enxerga o relato.

Inclua no relato:

- o que a falha permite fazer;
- como reproduzir, passo a passo;
- a versão do app e o Android onde você testou.

Responderemos assim que possível. Se a falha for confirmada, você será creditado na correção,
a menos que prefira o contrário.

## Escopo

O que **é** relevante reportar:

- vazamento das chaves de API que o usuário configura no agente de IA;
- execução de código a partir de um projeto `.swb` importado;
- qualquer caminho em que um projeto de terceiros consiga ler ou alterar dados fora dele.

O que **não** é vulnerabilidade deste projeto:

- o app pedir permissões do Android que ele de fato usa;
- crashes sem impacto de segurança — esses vão como
  [bug normal](https://github.com/SketchDevBrasil/Sketchware-sdbcodflow/issues/new?template=bug_report.yml);
- falhas do próprio Sketchware original, cujo código vem compilado nos `.jar` de `app/libs/`.

## Sobre as chaves de API do agente de IA

As chaves (Claude, entre outras) são digitadas pelo usuário e ficam armazenadas **no
dispositivo dele**. Não há chave embutida no código nem servidor intermediário do projeto.
Se você encontrar qualquer chave commitada no repositório, reporte pelo canal privado acima.
