<p align="center">
  <img src="assets/Sketchware-Pro.png" style="width: 30%;" />
</p>

# Sketchware GC AI

[![Android CI](https://github.com/SketchDevBrasil/Sketchware-GC-AI/actions/workflows/android.yml/badge.svg)](https://github.com/SketchDevBrasil/Sketchware-GC-AI/actions/workflows/android.yml)
[![GitHub contributors](https://img.shields.io/github/contributors/SketchDevBrasil/Sketchware-GC-AI)](https://github.com/SketchDevBrasil/Sketchware-GC-AI/graphs/contributors)
[![GitHub last commit](https://img.shields.io/github/last-commit/SketchDevBrasil/Sketchware-GC-AI)](https://github.com/SketchDevBrasil/Sketchware-GC-AI/commits/)
[![Total downloads](https://img.shields.io/github/downloads/SketchDevBrasil/Sketchware-GC-AI/total)](https://github.com/SketchDevBrasil/Sketchware-GC-AI/releases)
[![Repository Size](https://img.shields.io/github/repo-size/SketchDevBrasil/Sketchware-GC-AI)](https://github.com/SketchDevBrasil/Sketchware-GC-AI)

**Sketchware GC AI** é um mod do [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro)
com um agente de IA integrado, que entende o projeto aberto e edita telas, blocos e código
a partir de conversa. Este repositório é o código-fonte dele.

*Sketchware GC AI is a Sketchware Pro mod with a built-in AI agent that reads your open project and
edits screens, blocks and code through conversation. Issues and PRs in English are welcome.*

## Como contribuir

Leia o **[CONTRIBUTING.md](CONTRIBUTING.md)** — ele cobre como compilar, o mapa do código,
o padrão de commits e como investigar crashes que caem dentro dos `.jar` fechados do
Sketchware original.

- 🐞 [Reportar um bug](https://github.com/SketchDevBrasil/Sketchware-GC-AI/issues/new?template=bug_report.yml)
- 💡 [Sugerir uma funcionalidade](https://github.com/SketchDevBrasil/Sketchware-GC-AI/issues/new?template=feature_request.yml)
- 🤝 [Código de Conduta](CODE_OF_CONDUCT.md)
- 🔒 [Política de Segurança](SECURITY.md)

Boa parte do que está abaixo vem do Sketchware Pro original e continua valendo aqui.

## Building the App
To build the app, you must use Gradle. It's highly recommended to use Android Studio for the best experience.

### Source Code Map

| Class           | Role                                        |
| --------------- | ------------------------------------------- |
| `a.a.a.ProjectBuilder`      | Helper for compiling an entire project       |
| `a.a.a.Ix`      | Responsible for generating AndroidManifest.xml |
| `a.a.a.Jx`      | Generates source code of activities          |
| `a.a.a.Lx`      | Generates source code of components, such as listeners, etc. |
| `a.a.a.Ox`      | Responsible for generating XML files of layouts |
| `a.a.a.qq`      | Registry of built-in libraries' dependencies |
| `a.a.a.tq`      | Responsible for the compiling dialog's quizzes |
| `a.a.a.yq`      | Organizes Sketchware projects' file paths    |

> [!TIP]
> You can also check the `mod` package, which contains the majority of contributors' changes.

## Contributing

O guia completo está no [CONTRIBUTING.md](CONTRIBUTING.md). O resumo:

1. Faça um fork deste repositório.
2. Crie uma branch para a sua mudança.
3. **Teste no aparelho ou emulador** — compilar não é testar.
4. Abra um pull request descrevendo o que mudou e como você testou.

Contribuições de qualquer tamanho são bem-vindas, de correção de crash a funcionalidade nova.
Todas passam por revisão.

### Commit Message

When you make changes to one or more files, you need to commit those changes with a commit message. Here are some guidelines:

- Keep the commit message short and detailed.
- Use one of these commit types as a prefix:
  - `feat:` for a feature, possibly improving something already existing.
  - `fix:` for a fix, such as a bug fix.
  - `style:` for features and updates related to styling.
  - `refactor:` for refactoring a specific section of the codebase.
  - `test:` for everything related to testing.
  - `docs:` for everything related to documentation.
  - `chore:` for code maintenance (you can also use emojis to represent commit types).

Examples:
- `feat: Speed up compiling with new technique`
- `fix: Fix crash during launch on certain phones`
- `refactor: Reformat code in File.java`

> [!IMPORTANT]
> If you want to add new features that don't require editing other packages other than `pro.sketchware`, make your changes in `pro.sketchware` package, and respect the directories and files structure and names. Also, even though the project compiles just fine with Kotlin classes that you might add, try to make your changes or additions in Java, not Kotlin unless it is more than necessary.

## Thanks for Contributing

Thank you for contributing to Sketchware Pro! Your contributions help keep Sketchware Pro alive. Each accepted contribution will be noted down in the "About Team" activity. We'll use your GitHub name and profile picture initially, but they can be changed, of course.

## Discord

Want to chat with us, discuss changes, or just hang out? We have a Discord server just for that.

[![Join our Discord server!](https://invidget.switchblade.xyz/kq39yhT4rX)](http://discord.gg/kq39yhT4rX)

## Disclaimer

This mod was not created for any harmful purposes, such as harming Sketchware; quite the opposite, actually. It was made to keep Sketchware alive by the community for the community. Please use it at your own discretion and consider becoming a Patreon backer to support the developers. Unfortunately, other ways to support them are not working anymore, so Patreon is the only available option currently. You can find their Patreon page [here](https://www.patreon.com/sketchware).

We do NOT permit publishing Sketchware Pro as it is, or with modifications, on Play Store or on any other app store. Keep in mind that this project is still a mod. Unauthorized modding of apps is considered illegal and we discourage such behavior.

We love Sketchware very much and are grateful to Sketchware's developers for creating such an amazing app. However, we haven't received updates for a long time. That's why we decided to keep Sketchware alive by creating this mod, and it's completely free. We don't demand any money :)
