# Лабораторна робота №9 — Interservice communication

Сервіс A — **booking-service**, сервіс B — **tour-service**.
Три дії планування бронювання виконуються через окремі порти.
Кожен порт має HTTP і gRPC реалізації; бізнес-код не імпортує транспортні API.

| Бізнес-дія в A | Порт | REST у B | gRPC у B |
| --- | --- | --- | --- |
| Оцінити вартість для групи | TourPriceLookup | GET /api/catalog/tours/{id}/price | GetPrice |
| Перевірити наявність місць | TourAvailabilityLookup | GET /api/catalog/tours/{id}/availability | GetAvailability |
| Переглянути маршрут і дати поїздки | TourItineraryLookup | GET /api/catalog/tours/{id}/itinerary | GetItinerary |

Запити в A:

```http
GET http://localhost:8082/api/bookings/planning/1/estimate?passengers=2
GET http://localhost:8082/api/bookings/planning/1/availability?passengers=2
GET http://localhost:8082/api/bookings/planning/1/itinerary
```

Оцінка вартості — ціна туру × кількість пасажирів, без додаткових послуг і зборів.
Перевірка місць показує поточний стан, але **не резервує місця**.
Ці дії не змінюють бронювання; транзакції та outbox лабораторної №8 зберігають попередню поведінку.

## Архітектура

```text
BookingPlanningController
  -> BookingPlanningService
      -> TourPriceLookup / TourAvailabilityLookup / TourItineraryLookup
          -> HttpTourCatalog -> TourHttpClient (@HttpExchange) -> REST
          або
          -> GrpcTourCatalog -> generated blocking stub -> gRPC
                                                           |
                                            TourCatalogService -> TourService -> DB
```

HTTP-клієнт декларативний: інтерфейс Spring HTTP Service з @HttpExchange/@GetExchange,
проксі HttpServiceProxyFactory і RestClient. Додатковий Feign стартер не потрібний.
TourCatalog — об'єднання трьох портів для конфігурації адаптерів.
Сервіс планування залежить лише від трьох окремих портів.

Спільні REST DTO і **один** контракт common/src/main/proto/tour_catalog.proto містяться
у модулі common, який використовують обидва сервіси.
protobuf-maven-plugin запускає protoc і grpc-java під час Maven збірки.
Згенеровані класи знаходяться тільки у common/target/generated-sources та не комітяться.
Грошові суми передаються в protobuf десятковим рядком і перетворюються на BigDecimal;
дати — ISO-8601.

## Конфігурація

| Властивість | Змінна оточення | Локальне значення |
| --- | --- | --- |
| travel.tour-client.protocol | TOUR_CLIENT_PROTOCOL | http |
| travel.tour-client.rest-url | TOUR_REST_URL | http://localhost:8080 |
| travel.tour-client.grpc-host | TOUR_GRPC_HOST | localhost |
| travel.tour-client.grpc-port | TOUR_GRPC_PORT | 9090 |
| travel.tour-client.grpc-plaintext | TOUR_GRPC_PLAINTEXT | true |
| travel.tour-client.connect-timeout | TOUR_HTTP_CONNECT_TIMEOUT | 500ms |
| travel.tour-client.read-timeout | TOUR_HTTP_READ_TIMEOUT | 1s |
| travel.tour-client.deadline | TOUR_GRPC_DEADLINE | 1s |
| travel.catalog.grpc.enabled (B) | TOUR_GRPC_ENABLED | false |
| travel.catalog.grpc.port (B) | TOUR_GRPC_PORT | 9090 |

protocol — enum HTTP/GRPC; значення http/grpc також приймаються.
Невідоме чи порожнє значення спричиняє помилку конфігурації під час запуску.
Створюється тільки обраний адаптер; у режимі HTTP gRPC channel не створюється.
Адреси, порти й додатні тайм-аути перевіряються під час запуску.
Тайм-аути задаються як Spring Duration (наприклад 500ms, 2s), від 1ms до 2147483647ms.
Deadline встановлюється заново для **кожного** RPC.
Channel належить Spring і закривається разом із контекстом.

B запускає gRPC лише за enabled=true. SmartLifecycle відкриває порт під час запуску
контексту та закриває його під час зупинки. Після 5 секунд очікування активні виклики
примусово скасовуються. Зайнятий порт спричиняє помилку запуску.
Порт 0 дозволено для ізольованих тестів із випадковим вільним портом.

Публічні GET /api/catalog/tours/** повертають тільки дані каталогу:
ціни, кількість місць, назву, напрямок і дати. Це дозволяє клієнту A читати каталог без
Keycloak токена. Захист інших API не змінено. gRPC у цій лабораторній працює без
автентифікації та TLS; порт слід використовувати в довіреному локальному середовищі.
grpc-plaintext=false в A вмикає TLS клієнта, але сервер B у цій роботі TLS не налаштовує.

## Запуск локально

Потрібна Java 17. Скрипт scripts/mvn17.sh обирає JDK 17 на macOS.

```bash
bash scripts/mvn17.sh clean verify
docker compose up -d rabbitmq keycloak
```

В окремих терміналах із кореня проєкту:

```bash
# B: REST + gRPC
TOUR_GRPC_ENABLED=true java -jar tour-service/target/tour-service-0.0.1-SNAPSHOT.jar

# A: HTTP (типово)
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

Для переходу на gRPC зупинити A і запустити його з іншою властивістю:

```bash
TOUR_CLIENT_PROTOCOL=grpc java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

Контролер A та URL запитів залишаються однаковими.
Для HTTP B можна запускати без TOUR_GRPC_ENABLED: gRPC тоді вимкнений.

## Запуск Docker

Dockerfile використовує JAR після Maven збірки, тому спочатку виконати clean verify.

```bash
# HTTP; gRPC сервер вимкнений
docker compose --profile apps up -d --build

# gRPC; Compose передає DNS-ім'я tour-service замість localhost
TOUR_CLIENT_PROTOCOL=grpc TOUR_GRPC_ENABLED=true docker compose --profile apps up -d --build
```

У Compose REST адреса — http://tour-service:8080, gRPC — tour-service:9090.
Для відновлення HTTP виконати першу команду знову без відповідних змінних оточення.
Compose не перевіряє готовність HTTP/gRPC сервісу B: до його готовності A повертає 503.

## Обробка помилок

| Ситуація | Власний виняток A | Відповідь A |
| --- | --- | --- |
| HTTP 404 / gRPC NOT_FOUND | TourLookupNotFoundException | 404 ProblemDetail |
| HTTP 5xx та інші HTTP помилки, крім 404 | TourLookupUnavailableException | 503 ProblemDetail |
| Відмова з'єднання / HTTP connect або read timeout | TourLookupUnavailableException | 503 ProblemDetail |
| gRPC deadline або будь-який error status, крім NOT_FOUND | TourLookupUnavailableException | 503 ProblemDetail |

Транспортні винятки залишаються причиною власного винятку всередині адаптера;
сервіси й контролери обробляють тільки власні типи.
TourLookupExceptionHandler не розкриває технічних подробиць у відповіді 503.
Відсутній тур у B перетворюється на REST 404 через наявний RestExceptionHandler
та на gRPC NOT_FOUND через TourCatalogGrpcService.
Внутрішня помилка B у gRPC повертає INTERNAL.
Неправильні tourId або passengers (<1) в API планування дають 400.

## Перевірка

```bash
bash scripts/mvn17.sh clean verify
```

Тести не потребують Docker, Keycloak або зовнішнього сервісу B:
HTTP та gRPC сервери для клієнтських перевірок слухають випадкові локальні порти.

- HttpTourCatalogTest: три реальні HTTP виклики, адреси й декодування, 404, 4xx/5xx,
  повільна відповідь, відмова з'єднання, некоректне та порожнє тіло.
- GrpcTourCatalogTest: три реальні RPC, передавання ID, значення, deadline кожного RPC,
  усі error statuses, завислий сервер та зупинений сервер.
- TourClientConfigTest: HTTP за замовчуванням, GRPC за властивістю, єдиний адаптер
  для трьох портів, закриття channel, відмова запуску за неправильної конфігурації.
- BookingPlanningTest: розрахунок вартості, достатньо/недостатньо місць, маршрут,
  перетворення власних помилок на 404/503 для всіх трьох дій.
- BookingHttpTest: перевірка параметрів планування через HTTP у Boot контексті.
- TourCatalogTransportTest: REST та реальний gRPC сервер B, три методи, NOT_FOUND,
  INTERNAL, gRPC вимкнений за замовчуванням, запуск і звільнення порту із контекстом.

Додаткова наскрізна перевірка зібраних JAR:

```bash
python3 scripts/check-lab9.py
```

Скрипт сам запускає й зупиняє обидва сервіси на випадкових локальних портах,
використовує тимчасові in-memory БД та вимикає RabbitMQ і запуск Quartz jobs.
Він порівнює відповіді всіх трьох бізнес-дій через HTTP та gRPC,
перевіряє 400/404 і 503 після зупинки B. Docker і Keycloak не потрібні;
наявні процеси й дані проєкту не використовуються.

Демонстраційні запити: http/lab9-interservice.http.
Для перевірки 503 зупинити B та повторити будь-який запит планування до A.
Для перевірки 404 використати ID відсутнього туру.
Для перевірки fail-fast запустити A з TOUR_CLIENT_PROTOCOL=unknown — запуск завершиться помилкою.
