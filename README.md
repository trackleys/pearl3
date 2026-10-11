# Pearl Trajectory (Fabric 1.21.11)

Клиентский мод: показывает белую линию траектории полёта эндер-жемчуга
(аналогично функции из CheatsUtils, но только траектория).

## Как работает
- Линия рисуется, только пока жемчуг в основной или второй руке.
- Линия белая и видна сквозь блоки.
- Линия заканчивается в точке первого касания блока (в ванилле жемчуг
  от стен может отскакивать — реальный приземлится чуть иначе).
- Симуляция учитывает гравитацию (0.03), сопротивление воздуха (0.99)
  и скорость игрока в момент броска. Разброс (random) жемчуга не
  учитывается, поэтому возможно отклонение примерно до 0.2 блока.

## Сборка
Требуется JDK 21.

1. Если у вас не установлен Gradle — создайте обёртку:
   `gradle wrapper --gradle-version 8.14` (или скопируйте папку
   `gradle/` + `gradlew` из шаблона fabric-example-mod).
2. Соберите: `./gradlew build` (Linux/macOS) или `gradlew.bat build` (Windows)
3. Готовый jar будет в `build/libs/pearltrajectory-1.0.0.jar`
4. Киньте его в папку `mods` вместе с Fabric Loader 0.18.x
   и Fabric API 0.141.x для 1.21.11.

## Структура
- `PearlTrajectoryClient.java` — весь код мода (фаза извлечения
  данных + фаза отрисовки через StagedVertexBuffer, новое API
  Fabric World Render Events для 1.21.10+).

## Возможные мелкие правки под вашу сборку
- Если `RenderPipelines.LINES_SNIPPET` не найдётся — посмотрите в
  `net.minecraft.client.renderer.RenderPipelines` доступные сниппеты
  линий для вашей версии (например `DEBUG_LINE_STRIP`).
- Если `drawIndexed(...)` не скомпилируется, поменяйте порядок
  аргументов на тот, что требует `RenderPass` в вашей версии.
- Толщина линии задаётся пайплайном (по умолчанию 1px). Другой цвет:
  меняйте `setColor(...)` в `renderTrajectory`.
- Чтобы линия НЕ была видна сквозь стены — уберите
  `.withDepthStencilState(Optional.empty())`.


## Самый простой способ получить готовый jar (без установки Java/Gradle)

1. Создай репозиторий на github.com (можно приватный).
2. Загрузи туда **содержимое** папки pearltrajectory (через веб-интерфейс:
   "Add file" -> "Upload files", можно перетащить папку целиком).
   Файл `.github/workflows/build.yml` тоже загрузится — он нужен.
3. Зайди во вкладку **Actions** -> выбери workflow "Build mod jar" ->
   "Run workflow". Сборка займёт 3–5 минут.
4. Открой завершённый запуск, внизу будет раздел **Artifacts** ->
   скачай `pearltrajectory-jar`. Внутри архива лежит готовый
   `.jar` — его и кидай в папку `mods`.

## Если хочешь собрать у себя на компьютере

1. Установи JDK 21 (например, Temurin: adoptium.net).
2. Скачай шаблон fabric-example-mod (кнопка Code -> Download ZIP на GitHub)
   и распакуй — в нём есть готовый gradlew.
3. Замени в нём `src` и `gradle.properties` на файлы из этого проекта
   (пакет `ru.example.pearltrajectory` менять не нужно).
4. Выполни `./gradlew build` (Linux/macOS) или `gradlew.bat build` (Windows).
5. Готовый jar будет в `build/libs/pearltrajectory-1.0.0.jar`.
