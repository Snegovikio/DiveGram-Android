# DiveGram

Неофициальный Android-клиент на базе исходников Telegram. Это форк официального
[Telegram for Android](https://github.com/DrKLO/Telegram) с большим набором
собственных функций: обход DPI, приватность, кастомизация внешнего вида, NFC-«дроп»
контактов, ИИ-анализ чатов, погода в списке чатов, локальные NFT-подарки и другое.

| | |
|---|---|
| Пакет | `org.minegram.messenger` |
| Версия | `12.10.1` (`versionCode 7038`) |
| База | `DrKLO/Telegram`, upstream-коммит `45ab8f43` |
| Изменено файлов | 382 относительно upstream |
| Лицензия | GPLv2 (см. `LICENSE`), + правила Telegram API |

> **Это неофициальный клиент.** Он никак не связан с и не одобрен Telegram FZ-LLC.
> Разработчики не несут ответственности за ваши данные. Перед публикацией своей
> сборки прочитайте [«Что нужно заполнить»](#что-нужно-заполнить-перед-сборкой) —
> в этом репозитории **намеренно удалены** чужие `api_id`/`api_hash` и ключи.

---

## Содержание

- [Быстрый старт](#быстрый-старт)
- [Требования](#требования)
- [Что нужно заполнить перед сборкой](#что-нужно-заполнить-перед-сборкой)
- [Сборка](#сборка)
- [Возможности](#возможности)
- [Пасхалки](#пасхалки)
- [Где хранятся настройки](#где-хранятся-настройки)
- [Ограничения и честные заметки](#ограничения-и-честные-заметки)
- [Отличия от upstream](#отличия-от-upstream)
- [Структура проекта](#структура-проекта)
- [Лицензия](#лицензия)

---

## Быстрый старт

```bash
# 1. Клонировать ВМЕСТЕ с сабмодулями (иначе сборка упадёт на NDK)
git clone --recursive --shallow-submodules <url-репозитория> DiveGram
cd DiveGram

# 2. Указать путь к SDK
echo "sdk.dir=/путь/к/Android/Sdk" > local.properties

# 3. Вписать свои api_id / api_hash
nano TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java

# 4. Проверить, что Java компилируется (быстро, ~1 мин)
./gradlew :TMessagesProj:compileDebugJavaWithJavac

# 5. Собрать APK
./gradlew :TMessagesProj_App:assembleAfatRelease
```

Готовый APK: `TMessagesProj_App/build/outputs/apk/afatRelease/app.apk`
(имя файла задано `outputFileName = "app.apk"` в `TMessagesProj_App/build.gradle`).

**Важно про сабмодули.** `TMessagesProj/jni/third_party/` содержит три git-сабмодуля
(`libvpx`, `dav1d`, `ffmpeg`) — без них NDK-сборка не запустится. Забыли `--recursive`?

```bash
git submodule init && git submodule update --init --recursive --depth=1
```

---

## Требования

| Компонент | Версия | Откуда |
|---|---|---|
| JDK | **17** | `Dockerfile`: `gradle:8.7.0-jdk17` |
| Android SDK | 35 | `compileSdkVersion 35` |
| Build Tools | 35.0.0 | `buildToolsVersion '35.0.0'` |
| Android NDK | **27.2.12479018** | `ndkVersion` в `TMessagesProj_App/build.gradle` |
| Gradle | 8.7 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.6.1 | `build.gradle` |
| Kotlin | 1.9.20 | `build.gradle` |
| CMake / NDK-build | из NDK | для нативной части |

> ⚠️ **Расхождение в репозитории:** в `Dockerfile` указан NDK `21.4.7075529`,
> а в `TMessagesProj_App/build.gradle` — `27.2.12479018`. Это наследие upstream.
> Верьте `build.gradle` и ставьте **27.2.12479018**, иначе нативная сборка падает.

**Железо.** Собираются сразу 4 ABI (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`),
поэтому нужно ~16 ГБ RAM (в `gradle.properties` стоит `-Xmx6144M`) и ~30 ГБ
свободного места. Первая сборка нативной части занимает 20–40 минут, последующие
инкрементальные — несколько минут. Ограничить список ABI, чтобы ускорить сборку,
можно в `TMessagesProj_App/build.gradle` → `productFlavors` → `abiFilters`.

Проверить, что Gradle подхватил нужный JDK:

```bash
./gradlew -version
```

Если JDK определился неверно, раскомментируйте в `gradle.properties`:

```properties
org.gradle.java.home=/путь/к/jdk-17
```

---

## Что нужно заполнить перед сборкой

Из репозитория **намеренно вырезаны** личные ключи автора. Пока они пустые,
приложение собирается, но **не подключается к серверам Telegram**.

### 1. `api_id` / `api_hash` — обязательно

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`:

```java
// сейчас в репозитории:
public static int APP_ID = 0;
public static String APP_HASH = "";
```

Получите свои на <https://my.telegram.org/apps> и впишите. По правилам Telegram
использовать чужие `api_id` нельзя, а имя «Telegram» и официальный логотип
использовать нельзя — этот форк называется DiveGram и использует свою иконку.

### 2. Подпись (`release.keystore`)

Лежит в `TMessagesProj/config/release.keystore`. Это **фиктивный keystore из
upstream** (`CN=Android Developer, OU=Telegram`, 2019 г.), пароли — `android`.
Он в комплекте с открытым исходником, и им подписан сам Telegram.

Для публикации своей сборки сделайте свой:

```bash
keytool -genkeypair -v -keystore my.keystore -alias mykey \
        -keyalg RSA -keysize 2048 -validity 10000
```

и пропишите в `gradle.properties`:

```properties
RELEASE_KEY_PASSWORD=...
RELEASE_KEY_ALIAS=mykey
RELEASE_STORE_PASSWORD=...
```

> Обратите внимание: **все** buildType (`debug`, `standalone`, `release`) в этом
> проекте подписываются одним и тем же `config/release.keystore`
> (`TMessagesProj_App/build.gradle` → `signingConfigs`). Пока вы не замените файл,
> все ваши сборки будут подписаны ключом, который лежит в открытом репозитории.

### 3. Firebase (`google-services.json`)

`TMessagesProj/google-services.json` содержит проект **Telegram** (`tmessages2`)
и регистрации пакетов `org.minegram.messenger*`. Секретов в нём нет, для сборки
можно оставить как есть. Для собственного релиза заведите свой проект на
<https://console.firebase.google.com/> и замените файл.

### 4. Ключ погоды (опционально)

`TMessagesProj/src/main/java/org/telegram/messenger/DiveGramWeather.java`:

```java
private static final String OPENWEATHERMAP_API_KEY = "";  // пусто — погода не работает
```

Бесплатный ключ: <https://openweathermap.org/api>.

### 5. Адреса конфигов бейджей и страйков (опционально)

`DiveGramBadge.java` и `DiveGramStreak.java`:

```java
public static final String CONFIG_URL = "";  // пусто — работают только вшитые дефолты
```

При пустом значении загрузка сети пропускается, приложение не падает. Впишите
свой URL, если хотите раздавать бейджи/страйки динамически.

---

## Сборка

### Варианты (flavor × buildType)

Flavor'ы (`TMessagesProj_App/build.gradle`):

| Flavor | `abiVersionCode` | `BUNDLE` | minSdk | Назначение |
|---|---|---|---|---|
| `afat` | 9 | false | 23 | основной, обычный APK |
| `bundleAfat` | 1 | true | 21 | AAB для Google Play |
| `bundleAfat_SDK23` | 2 | true | 23 | AAB с манифестом SDK23 |

BuildType:

| BuildType | `applicationIdSuffix` | debuggable | minify | Назначение |
|---|---|---|---|---|
| `debug` | `.beta` | да | нет | отладка, ставится рядом с релизом |
| `standalone` | `.web` | нет | да | веб-версия (как Telegram Web) |
| `release` | — | нет | да | обычный релиз |

Итоговый `applicationId`: `org.minegram.messenger` (release),
`….beta` (debug), `….web` (standalone).

### Команды

```bash
# Проверка Java-части (быстро)
./gradlew :TMessagesProj:compileDebugJavaWithJavac

# Проверка нативной части (долго, 4 ABI)
./gradlew :TMessagesProj:externalNativeBuildDebug

# Релизный APK — то, что нужно обычному пользователю
./gradlew :TMessagesProj_App:assembleAfatRelease

# AAB для Google Play
./gradlew :TMessagesProj_App:bundleBundleAfatRelease
./gradlew :TMessagesProj_App:bundleBundleAfat_SDK23Release

# Веб-вариант и Huawei
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
./gradlew :TMessagesProj_AppHuawei:assembleAfatRelease
```

Оффлайн-сборка (когда зависимости уже в кэше) — добавьте `--offline`.

### Сборка в Docker

`Dockerfile` собирает сразу все пять артефактов. Требуется установленный Docker:

```bash
docker build -t divegram .
docker run --rm -v "$PWD:/home/source" divegram
```

Результаты появятся в `TMessagesProj/build/outputs/apk/` и
`TMessagesProj/build/outputs/bundle/`.

### Вспомогательные скрипты в корне

| Файл | Что делает |
|---|---|
| `apkfrombundle.py` | достаёт `.apk` из AAB |
| `apkdiff.py` | сравнивает два APK по содержимому |
| `Dockerfile` | контейнерная сборка всех вариантов |

---

## Возможности

Всё настраивается в **Настройки → DiveGram → «Настройки» (Все фичи)**.
Экран состоит из четырёх вкладок.

### Обход

| Фича | Описание |
|---|---|
| **TG WS Proxy (обход DPI)** | Локальный WebSocket-прокси (`127.0.0.1:1443`) для обхода блокировок и DPI. Включается вручную, для работы нужен секрет от автора |

### Приватность

| Фича | Описание |
|---|---|
| Защита номера | Скрывает ваш номер в чатах |
| Сохранять удалённые сообщения | Удалённые у собеседника сообщения остаются видны вам |
| Прозрачность удалённых | Насколько приглушены такие сообщения |
| Режим призрака: «в сети» | Не отправлять статус «в сети» |
| Режим призрака: «прочитано» | Не отправлять отметки о прочтении |
| Режим призрака: «печатает…» | Не отправлять статус набора текста |
| Показывать, где печатает собеседник | Видно, печатает человек или нет |
| Убрать камеру из выбора медиа | В галерее остаётся только выбор из файлов |

### Внешний вид

| Фича | Описание |
|---|---|
| Надпись в списке чатов | Своё имя (и/или кастомное) вместо «Telegram» |
| Плашки чатов | Цветные плашки счётчиков непрочитанного |
| Цвет и прозрачность плашек | Настройка вида плашек |
| Обои чата как фон списка | Фон списка чатов из обоев профиля |
| Загрузить обои на фон списка | Своё изображение + редактор (масштаб, сдвиг, блюр) |
| Прозрачность плашки меню | Скругление и прозрачность нижней панели |
| Надпись посередине | Центрирование заголовка |
| Блюр фона карточки музыки | Размытие подложки в музыкальной карточке |
| Текст песни в плеере | Отображение лирики в аудиоплеере |
| Скрывать кнопки в лирике | Убирает контролы поверх лирики |
| Редактор альбомной лирики | Своё размещение обложки и текста |
| Скрыть папку «Все чаты» | Убирает дефолтную вкладку из списка чатов |
| Скрыть «Контакты» внизу | Прячет нижнюю кнопку контактов |
| Кружок на заднюю камеру | Обрезка превью в круг |
| Боковое меню | Альтернативное боковое меню вместо нижнего. Содержимое опущено на 72 dp, чтобы не наезжать на системный статус-бар |

### Функции

| Фича | Описание |
|---|---|
| Автоответчик | Автоответ, если вас долго нет в сети; настраиваются текст и пауза |
| Локальный Telegram Premium | Эмуляция Premium **только на вашем устройстве**. Не даёт серверных привилегий и не обходит проверку на стороне Telegram |
| Быстрые ответы | Свои шаблоны для быстрой вставки |
| Упомянуть всех (`@all`) | Отметки всех участников |
| Отправлять отметки частями | Не упрётся в лимит Telegram |
| Перевод лирики | Перевод текста песни на другой язык |
| Погода | Виджет погоды в списке чатов, город задаётся вручную |
| ИИ анализ чата | Отправка переписки в ИИ через OpenRouter. **Нужен свой API-ключ** |
| Добавить НФТ-подарок локально | Свои NFT-подарки, хранятся только на устройстве |
| Очистить список чатов | Массовая очистка |
| Username Drope | Обмен контактами через NFC, см. ниже |
| Сила рипл-анимации | Настройка анимации в Username Drope. **Только Android 13+** |
| Звуковой кодек | По умолчанию / AAC / LDAC — см. [ограничения](#ограничения-и-честные-заметки) |
| Качество записи голосовых | Максимум / 64 / 32 / 16 кбит/с (Opus) |
| Показать битрейт аудиофайла | Битрейт трека в обоих плеерах: внизу рядом с длительностью, вверху — левее кнопки скорости (например `256 kbps`) |

### Username Drope

Отдельная фича: приложение работает как **NFC HostApduService**
(`xml/drope_apduservice.xml`, AID `F00144726F7065`). Два телефона рядом
и касаются друг друга — контакты обмениваются автоматически.

Вход: **Профиль → «⋯» → Username Drope** (пункт есть только в своём профиле).
Разрешение NFC выдаётся при первом запуске. На экране показывается прогресс
(«слушает» / «нашёл собеседника» / «обменивается» / «добавляет в контакты»),
после успеха — суммаризация переданных контактов.

---

## Пасхалки

| Где | Как | Что |
|---|---|---|
| Иконка приложения (DiveGram) | 5 тапов | Случайная картинка (`photo_6.jpg` … `photo_10.jpg`) вместо иконки, тап ещё раз — назад |
| Заголовок списка чатов | 12 тапов | Картинка `photo_1.png` … `photo_5.png` выезжает снизу-слева с анимацией |
| Текст сообщения | Отправить сообщение со словом `kitagawa` или `marin` | Оверлей `MarinOverlayView` с картинкой и звуком на 7,5 секунды |
| Заголовок «Ссылки» в настройках | 3 тапа | BottomSheet «Создано при моральной поддержке…» |

---

## Где хранятся настройки

Всё лежит в общем файле настроек `mainconfig` (стандартные `SharedPreferences`
Telegram) плюс отдельный `easter_egg` для состояния пасхалок.
Ключи форка вынесены в блок примерно в `SharedConfig.java` и читаются
по константам. Основные группы и префиксы:

| Префикс / ключ | Что хранит |
|---|---|
| `dgDeletedMessagesOpacity` | прозрачность удалённых сообщений |
| `dgHideAllChats`, `dgHideContactsBar` | скрытие вкладок и кнопок |
| `dgDialogsWallpaper*` | свои обои списка чатов (цвет, сдвиг, масштаб, блюр) |
| `dgMainTabsOpacity` | прозрачность нижней панели |
| `dgPlayerLyrics*` | лирика в плеере, перевод, координаты обложки |
| `dgWeather*` | погода: включена, город, текст, координаты, время кэша |
| `dgBadges*`, `dgStreaks*` | бейджи и страйки |
| `dgQuickReplies*`, `dgMentionAll*` | быстрые ответы и `@all` |
| `dgAiChat`, `dgGeminiKey`, `dgAiModel` | ИИ-анализ чата и ключ OpenRouter |
| `dgAudioCodec`, `dgVoiceRecordBitrate` | звуковой кодек и битрейт голосовых |
| `dgDropeRippleStrength` | сила рипл-анимации |
| `dgAccKey()`-суффиксы `_a<index>` | per-аккаунтные ключи (эмодзи, коллекционные знаки, цвета профиля) |
| `tgwsProxy*` | настройки локального прокси |

Кэш фич лежит на диске: `files/divegram_badges/`, `files/divegram_streaks/`,
`files/nft_local_gifts_a<account>.dat`.

Полный список ключей с номерами строк — в `SharedConfig.java`.

---

## Ограничения и честные заметки

Здесь то, о чём стоит знать до того, как выкладывать сборку.

**Звуковой кодек не форсирует LDAC.** Настройка «Звуковой кодек» лишь выставляет
`AudioAttributes.contentType = CONTENT_TYPE_MUSIC` и подсказывает системе, что
это музыка. Выбор кодека Bluetooth делает операционная система, и публичного API
для принудительного выбора нет. На проверенном устройстве (Android 16) система
выбирала LDAC и **до** включения этой настройки. Реально измеримый параметр здесь
один — «Качество записи голосовых», он работает и меняет битрейт Opus.
Источник качества музыки при этом остаётся исходным файлом: клиент не может
добавить детализацию, которой нет в файле.

**Локализация почти не сделана.** Строки новых функций захардкожены по-русски
прямо в Java-коде, поэтому интерфейс не переключается на английский.
Локализованы только название приложения и строки Username Drope
(`values/strings.xml`, `values-ru/strings.xml`).

**Часть фич не заработает «из коробки»:** погода (нужен OpenWeather-ключ),
ИИ-анализ (нужен ключ OpenRouter), обход DPI (нужен секрет прокси),
динамические бейджи и страйки (нужен `CONFIG_URL`).

**«Локальный Telegram Premium»** — только визуальные и локальные эффекты
на устройстве. Серверные привилегии и проверка подписки на стороне Telegram
не обходятся. Не выдавайте его за настоящий Premium.

**Обход DPI** требует отдельной настройки и может нарушать правила
использования Telegram API. Ответственность на пользователе.

**В корне репозитория лежит файл `divegram` — 133 МБ.** Это готовый APK,
закоммиченный по недосмотру, а не исходник. Стоит удалить, если не нужен.

---

## Отличия от upstream

365 изменённых файлов относительно `DrKLO/Telegram` @ `45ab8f43`.
Основные по числу правок:

- `SharedConfig.java` — все настройки форка (около 60 ключей)
- `ChatActivity.java`, `DialogsActivity.java`, `MainTabsActivity.java` — UI
- `TLRPC.java`, `TL_update.java`, `TL_bots.java`, `TL_iv.java` — схема API
- `VideoPlayer.java`, `MediaController.java`, `jni/audio.c` — плеер и запись
- `strings.xml` и 9 локализаций, `styles.xml` (splash)
- `LauncherIconController.java` + набор иконок — свои иконки приложения

Новые классы: `DiveGramFeaturesActivity`, `MineGramSettingsActivity`,
`DiveGramBadge`, `DiveGramStreak`, `DiveGramWeather`, `DiveGramNfts`,
`DiveGramLocalGiftsActivity`, `AudioCodecs`, `LocalNftGiftsStore`, `GeminiApi`,
`GoogleTranslate`, `TgWsProxy` (+ пакет `tgwsproxy/proxy/*`), `EmuDetector`,
`BatteryOptimizationHelper`, `MarinOverlayView`, `LyricsAlbumEditorActivity`,
`EphemeralMessagesHelper`, пакет `ui/UsernameDrope/*`.

Полный список своих правок:

```bash
git diff --stat 45ab8f43
```

Оригинальный README Telegram сохранён в
[`README.telegram-upstream.md`](README.telegram-upstream.md).

---

## Структура проекта

| Модуль | Назначение |
|---|---|
| `TMessagesProj` | основной модуль, вся логика клиента и `jni/` |
| `TMessagesProj_App` | сборка Google Play (applicationId `org.minegram.messenger`) |
| `TMessagesProj_AppStandalone` | веб-вариант (`.web`) |
| `TMessagesProj_AppHuawei` | сборка под AppGallery с HMS |
| `TMessagesProj_AppHockeyApp` | сборка для HockeyApp |
| `TMessagesProj_AppTests` | тесты |
| `buildSrc` | кастомные gradle-задачи |
| `third_party/` | `libvpx`, `dav1d`, `ffmpeg` — git-сабмодули |

Ключевые файлы форка:

| Файл | Что делает |
|---|---|
| `ui/DiveGramFeaturesActivity.java` | экран всех настроек, 4 вкладки |
| `ui/MineGramSettingsActivity.java` | корневой экран DiveGram |
| `messenger/SharedConfig.java` | хранение и чтение всех настроек |
| `messenger/AudioCodecs.java` | звуковой кодек и битрейт записи |
| `messenger/DiveGramBadge.java` | бейджи у аватарок |
| `messenger/DiveGramStreak.java` | streak-декорации |
| `messenger/DiveGramWeather.java` | погода (OpenWeatherMap) |
| `messenger/GeminiApi.java` | ИИ-анализ чата (OpenRouter) |
| `messenger/TgWsProxy.java` | локальный прокси для обхода DPI |
| `ui/Components/FragmentContextView.java` | битрейд в верхней панели плеера |
| `ui/UsernameDrope/*` | обмен контактами через NFC |
| `messenger/BuildVars.java` | `api_id`, `api_hash`, ссылки на приложение |

---

## Лицензия

Код распространяется под **GPL v2** — см. [`LICENSE`](LICENSE).

Полный список сторонних компонентов — в
[`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES).

Приложение использует исходный код Telegram, и при публикации своей сборки вы
обязаны соблюдать [правила Telegram API](https://core.telegram.org/api/terms):

1. получить собственный `api_id`;
2. не использовать имя «Telegram»;
3. не использовать официальный логотип;
4. публиковать исходный код своей сборки.

Форк не связан с Telegram FZ-LLC и не одобрен ими. Официальный клиент —
<https://telegram.org>, документация API — <https://core.telegram.org/api>.
