# Лабораторна робота 5: асинхронна обробка

## Предметна область і три повільні операції

До Travel Agency додано підтримку бронювання:

1. Підготовка підтвердження бронювання для відправлення електронною поштою.
2. Отримання ціни трансферу: 25.00 на пасажира.
3. Отримання ціни страхування: 15.00 на пасажира.

У початковому CRUD цих інтеграцій не було. `TravelPartnerClient` моделює три
синхронні блокувальні операції із затримкою 1000 мс кожна. Реальних SMTP,
HTTP-провайдерів, платежів чи запису бронювання немає. Для лабораторної таку
операцію перенесено у власний пул через три методи `@Async` у `BookingAsyncTasks`.
У тестах замість секундних очікувань клієнт замінюється керованими моками.

## Структура й призначення коду

Усі наведені Java-шляхи починаються з `src/main/java/com/example/travelagency/`.

| Файл | За що відповідає |
| --- | --- |
| `config/AsyncConfig.java` | `@EnableAsync`, власний executor і підключення обробника помилок `void`. |
| `booking/AsyncFailureHandler.java` | `AsyncUncaughtExceptionHandler`: записує помилку, метод і назву потоку в лог. |
| `booking/TravelPartnerClient.java` | Три повільні синхронні операції та імітація збою провайдера. |
| `booking/BookingAsyncTasks.java` | Три `@Async("travelExecutor")` методи: один `void` і два `CompletableFuture<BigDecimal>`. |
| `booking/BookingSupportService.java` | Перевіряє існування туру й місця, запускає дві незалежні задачі, об'єднує їх і обробляє тайм-аут/збій. |
| `booking/ConfirmationRequest.java` | DTO з перевіркою електронної адреси та демонстраційним прапорцем збою. |
| `booking/ExtrasQuote.java` | DTO з двома цінами, загальною сумою, ознакою доступності та поясненням. |
| `web/BookingSupportController.java` | REST endpoints, які використовують асинхронні операції. |
| `config/RestExceptionHandler.java` | Додано обробку `TaskRejectedException`: HTTP 503 та `Retry-After: 1`. |

Наявний `TourService` використовується для синхронного читання DTO туру. JPA-сутності
й поточна транзакція не передаються між потоками. Методи `@Async` викликаються
з іншого Spring-біна, тому виклик проходить через проксі. Self-invocation немає.

## Налаштування

У `src/main/resources/application.yml`:

```yaml
spring:
  mvc:
    async:
      request-timeout: 5s

travel:
  async:
    core-pool-size: 2
    max-pool-size: 2
    queue-capacity: 2
    partner-delay-ms: 1000
    timeout-ms: 2500
```

Executor має назву `travelExecutor`, префікс потоків `travel-async-` і політику
`AbortPolicy`. За стандартних налаштувань одночасно працюють максимум дві
фонові операції, ще дві можуть чекати в черзі. Кожний розрахунок додаткових
послуг надсилає дві окремі задачі, тому при навантаженні можливий HTTP 503.

Залежності не додавалися: Spring Context, MVC, AOP та тестові бібліотеки вже є
в `pom.xml`. Достатньо Java 17 і поточних залежностей проєкту.

## Поведінка API

Усі endpoints нижче потребують наявної автентифікації через Spring Security.

### Підтвердження бронювання

`POST /api/tours/{id}/booking-support/confirmation`

```json
{"email":"traveler@example.com","simulateFailure":false}
```

Повертає HTTP 202 і `status: QUEUED`, коли задачу прийнято до виконання.
Після приблизно секунди в консолі з'являється демонстраційне підтвердження.
Параметр `simulateFailure: true` імітує збій відправлення: HTTP 202 залишається
відповіддю про прийняття задачі, а пізнішу помилку реєструє `AsyncFailureHandler`.
Це не підтвердження фактичної доставки листа. Пул і черга працюють у пам'яті
процесу; це не надійна черга повідомлень і не механізм повторної доставки.

### Паралельний розрахунок

`GET /api/tours/{id}/booking-support/extras?passengers=2`

Обидва методи `@Async` запускаються перед об'єднанням:

```java
CompletableFuture<BigDecimal> transfer = tasks.transferPrice(tourId, passengers);
CompletableFuture<BigDecimal> insurance = tasks.insurancePrice(tourId, passengers, false);
// ... thenCombine(...).orTimeout(...).exceptionally(...)
```

Для двох пасажирів відповідь містить `transferPrice: 50.00`, `insurancePrice: 30.00`,
`total: 80.00`, `available: true`. Це ціна додаткових послуг, а не вартість самого туру.
Обидва повільні виклики виконуються паралельно, тому без черги результат
очікується приблизно за одну секунду. HTTP-контролер повертає CompletableFuture;
Servlet-потік не блокується викликом `get()` або `join()`.

### Помилка та тайм-аут

Додайте `simulateInsuranceFailure=true`, щоб змоделювати збій страхового провайдера.
`exceptionally` перетворює виняток на HTTP 200 з такими даними:

```json
{
  "tourId": 1,
  "passengers": 2,
  "transferPrice": null,
  "insurancePrice": null,
  "total": null,
  "available": false,
  "message": "Additional services temporarily unavailable. Please try again later."
}
```

Такий самий fallback використовується при перевищенні `timeout-ms` через
`orTimeout`. Значення `null` не створює враження, що недоступна послуга безкоштовна.
Причина записується в лог із винятком, зокрема `TimeoutException` для тайм-ауту.

Тайм-аут припиняє очікування клієнта, але не гарантує зупинку вже запущеної
блокувальної операції. У реальній інтеграції потрібні також тайм-аути самого
HTTP/SMTP-клієнта. Демонстраційний клієнт завершує роботу після своєї затримки.

### Переповнення пулу

`TaskRejectedException` може виникнути безпосередньо під час надсилання задачі,
до отримання Future. Він обробляється окремо від помилок виконання: повертається
HTTP 503, `Retry-After: 1` і повідомлення `Background workers are busy. Please try again later.`
Задача не виконується в Servlet-потоці й не відкидається мовчки.
Якщо відхилено другий із двох розрахунків, Future першого скасовується
за принципом best effort; це також не гарантує переривання його блокувального виклику.

## Відповідність усім шести сценаріям PDF

Тести розташовані в `src/test/java/com/example/travelagency/booking/`.

| Сценарій | Код і доказ |
| --- | --- |
| Повільний метод не блокує викликаючий код | `BookingAsyncExecutionTest.confirmation_returnsWhileWorkerIsStillBlockedOnTheCustomExecutor`: виклик повернувся, але операція ще чекає на latch; завершується лише після його звільнення. |
| Власний executor з обмеженнями | `AsyncConfig` та явний `@Async("travelExecutor")`. Тест перевіряє розміри 2/2, чергу на 2, префікс потоків і відмінність від потоку виклику. |
| Два виклики справді одночасні | `extras_bothWorkersEnterBeforeEitherIsReleased_andCombineTheirPrices`: обидва методи повинні зайти у блокувальну частину до звільнення будь-якого; перевіряються різні потоки й сума 80.00. |
| Обробка помилок Future та void | `extras_failedAsyncProvider_returnsFallbackInsteadOfAFailedFuture`, HTTP-тест `extras_failedFuture_stillReturns200WithAnExplicitUnavailableResult` і `confirmation_voidFailure_reachesTheRealUncaughtExceptionHandler`. Останній перевіряє виклик справжнього логувального handler-а через spy. |
| Обмежене очікування | `orTimeout` у сервісі; `extras_neverCompletingPartners_returnsFallbackAfterTimeout` і HTTP-тест `extras_blockedProvider_returnsFallbackWithoutWaitingForWorkerCompletion`. У тестах усі очікування latch/Future теж мають тайм-аут. |
| Цілеспрямоване відхилення задач | HTTP-тест `saturatedExecutor_oneRunningAndTwoQueued_fourthRequestReturns503WithRetryAdvice`: окреме тестове налаштування 1 потік + 2 місця в черзі; четвертий запит гарантовано відхиляється з 503 та Retry-After. |

`CountDownLatch` забезпечує доказ порядку подій і одночасності. Тести не
доводять паралельність лише порівнянням приблизного часу виконання.
Секундна затримка партнера у звичайних тестах замінена моками;
у `TravelPartnerClientTest` реальний адаптер запускається з нульовою затримкою.
Реальна БД і Keycloak для тестів не потрібні. HTTP-тести запускають локальний
сервер на випадковому порту та тестовий Basic Auth тільки всередині `src/test`.

## Запуск

```bash
cd /Users/dmytro/Desktop/Java2/travel-agency
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn verify
```

Перевірено на JDK 17: `mvn verify` успішний. Усього 122 тести, з них 20 нових
сценаріїв лабораторної №5; failures/errors/skipped: 0. Покриття JaCoCo:
410 із 440 рядків, тобто 93,18%. Поріг 60% з попередньої лабораторної збережено.

Тільки тести лабораторної №5:

```bash
mvn -Dtest=BookingAsyncExecutionTest,BookingSupportServiceTest,TravelPartnerClientTest,BookingAsyncHttpTest test
```

Для ручної демонстрації:

```bash
docker compose up -d
mvn spring-boot:run
```

Адреса застосунку: `http://localhost:8080`. У файлі
[http/lab5-async.http](http/lab5-async.http) отримайте токени та створіть окремий
демонстраційний тур. Використовується його реальний generated ID, а не припущення про id=1.
Для тайм-ауту перезапустіть застосунок так:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--travel.async.timeout-ms=200"
```

Після повторного запуску виконайте запит `/extras`: повернеться fallback до
завершення секундного розрахунку. Для повернення до звичайного режиму запустіть
без додаткового аргументу. Дати створюваного туру мають бути майбутніми.

## Що показати викладачу

1. `AsyncConfig`: `@EnableAsync`, розміри пулу, чергу, префікс, AbortPolicy і handler.
2. `BookingAsyncTasks`: рівно три методи з явним іменем executor; поясніть, чому виклик із іншого біна проходить через проксі.
3. POST підтвердження: відповідь 202 перед завершенням роботи; у логах потік `travel-async-*`.
4. GET `/extras`: `thenCombine`, дві незалежні задачі, сума 80.00; потім latch-тест паралельності.
5. Прапорці збою: Future повертає fallback, а помилку `void` записує handler.
6. `orTimeout`, демонстраційний запуск з 200 мс і тест блокувального провайдера.
7. HTTP-тест маленького executor-а: одна задача виконується, дві чекають, четверта отримує 503.
8. Результат `mvn verify` і `target/site/jacoco/index.html`.

## Джерело механізмів Spring

Використані `@Async`, кваліфікація executor-а та `AsyncUncaughtExceptionHandler`
описані в [офіційній документації Spring](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
