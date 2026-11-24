# AGENTS.md - UnB Pocket

## Stack
- Kotlin 2.0.20, AGP 8.5.2, Gradle 8.7, minSdk 26, compileSdk/targetSdk 34
- Core JVM: Coroutines 1.9, OkHttp 4.12, Jsoup 1.18.1, ZXing 3.5.3, Serialization 1.6.3
- Core Android: Room 2.6.1 (KSP), DataStore 1.1.1, Android Keystore AES-GCM, PdfBox-Android 2.0.27

## Comandos da Maquina (PowerShell)
- `java` no PATH e Zulu 11; mantenha `JAVA_HOME` (Temurin 17) na frente antes de rodar Gradle:
```powershell
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:model:test :core:network:test :core:parser:test testDebugUnitTest
.\gradlew.bat lint assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Mapa de Pastas
- `core/model/`: Modelos imutaveis, `CoreError`, `CoreResult`, `SyncedData` e interfaces do Core (JVM puro, incluindo `AppUpdateRepository`)
- `core/network/`: Cliente OkHttp, `InMemoryCookieJar`, lock JSF `ViewState`, decoder ISO-8859-1, streaming de downloads e guard-rail Read-Only (SIGAA, RU e GitHub Releases somente GET)
- `core/parser/`: Decodificador de horarios SIGAA, calculadora RU, classificador MSE de alergenicos, parsers HTML e parser SemVer de GitHub Releases
- `core/data/`: Room (`UnbPocketDatabase`), DataStore (`RuPreferencesStore`), `KeystoreCredentialVault`, `AppUpdateRepositoryImpl` e `UnbPocketCoreFactory`
- `app/`: Shell Android (`UnbPocketApplication`, `MainActivity`); camada de UI consumira apenas `UnbPocketCore`
- `docs/`: `endpoints.md` (mapeamento SIGAA/RU) e `core-contract.md` (contrato oficial Core <-> UI)

## Regras Inviolaveis
- Nunca enviar POST mutavel no SIGAA (`retiradaMatricula`, `trancamento*`, `novoTopicoForum`, `Confirmar` perfil)
- UI depende apenas de `:core:model` e recebe `UnbPocketCore` via `:app`; nao vazar Room/OkHttp para UI
