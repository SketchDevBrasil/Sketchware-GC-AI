1. Compilação Padrão (Gera o APK)
Este comando compila o projeto inteiro e gera o APK na pasta
./gradlew assembleDebug

./gradlew assembleRelease

Se você estiver com um dispositivo ou emulador conectado, este comando compila e já instala o app para você:

powershell
./gradlew installDebug

Caso queira garantir que não haja sobras de compilações antigas:

powershell
./gradlew clean assembleDebug


# 1. Adicionar todos os arquivos modificados
git add .

# 2. Criar o commit com uma descrição clara
git commit -m "feat: melhoria na robustez de injeção de IA, diferenciação de MoreBlocks e integração Unity Ads"

# 3. Enviar para o repositório remoto (GitHub)
git push origin main


Se quiser apenas verificar se o código está correto sem gerar o APK (é mais rápido):

powershell
./gradlew :app:compileDebugJavaWithJavac


O arquivo APK gerado normalmente fica em: app\build\outputs\apk\debug\app-debug.apk