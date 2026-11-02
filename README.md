# snip — локальный менеджер снипетов на Picocli

CLI-утилита для хранения кусков кода с тегами и поиском. Данные — в `~/.snip/snippets.db` (простой текстовый формат, одна строка на снипет, тело в base64).

## Команды

```
snip add    -t "merge PRs" -l sh -g git -g bash -f ./script.sh
snip add    -t "sieve" -l py -g algo --body "def sieve(n): ..."
echo "SELECT 1" | snip add -t "hello pg" -l sql -g db
snip list
snip list -g algo
snip show   a1b2c3
snip search "sieve" --content
snip tag    a1b2c3 -a hot -r algo
snip rm     a1b2c3
```

Короткий префикс id работает везде, если он однозначен.

## Сборка — обычный JAR

Требуется JDK 17+ и Maven.

```bash
mvn -q package
java -jar target/snip-all.jar list
```

## Сборка — GraalVM native-image

Нужен GraalVM 21+ с установленным `native-image` (`gu install native-image` на старых дистрибутивах, либо GraalVM CE/Oracle GraalVM 21+ где он идёт сразу).

```bash
mvn -Pnative -DskipTests package
./target/snip --help          # Linux/macOS
.\target\snip.exe --help      # Windows
```

На выходе — один исполняемый файл ~10–20 МБ, стартует мгновенно (нет прогрева JVM).

### Что сделано для native-image

- Picocli подхвачен через аннотационный процессор `picocli-codegen`, он сам генерирует `reflect-config.json` в `META-INF/native-image/<groupId>/<artifactId>/` при `mvn package` — отдельно руками ничего писать не надо.
- Хранилище написано без Jackson/GSON, чтобы не тащить рефлексию: свой line-based формат c base64 для тела.
- `native-image.properties` фиксирует инициализацию пакета `snip` на этапе сборки.

## Структура

```
src/main/java/snip/
  SnipCli.java   — корневая @Command + подкоманды
  Snippet.java   — модель
  Store.java     — атомарная запись в ~/.snip/snippets.db
```
