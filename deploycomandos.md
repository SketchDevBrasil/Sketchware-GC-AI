1. Compilação Padrão (Gera o APK)
Este comando compila o projeto inteiro e gera o APK na pasta
./gradlew assembleDebug

Se você estiver com um dispositivo ou emulador conectado, este comando compila e já instala o app para você:

powershell
./gradlew installDebug

Caso queira garantir que não haja sobras de compilações antigas:

powershell
./gradlew clean assembleDebug


Se quiser apenas verificar se o código está correto sem gerar o APK (é mais rápido):

powershell
./gradlew :app:compileDebugJavaWithJavac


O arquivo APK gerado normalmente fica em: app\build\outputs\apk\debug\app-debug.apk