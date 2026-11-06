# Лабораторна №8: Message brokers

Реалізовано в `/Users/dmytro/Desktop/Java2/travel-agency`.

## Три бізнес-дії

| Дія в booking-service | HTTP | Подія | Результат у tour-service |
| --- | --- | --- | --- |
| Створення бронювання | `POST :8082/api/bookings` | `CREATED` | Запис журналу зі статусом `NEW` |
| Підтвердження | `POST :8082/api/bookings/{id}/confirm` | `CONFIRMED` | Запис журналу зі статусом `CONFIRMED` |
| Скасування | `POST :8082/api/bookings/{id}/cancel` | `CANCELLED` | Запис журналу зі статусом `CANCELLED` |

Результат споживання видно через `GET http://localhost:8080/api/booking-events`.
Цей ендпоінт захищений наявною OIDC/JWT-автентифікацією: у браузері ввійдіть
через Keycloak або передайте Bearer JWT з `http/lab8-message-brokers.http`.
`GET http://localhost:8082/api/bookings` показує бронювання відправника.
Кореневий URL `http://localhost:8082/` не має HTML-сторінки і повертає 404.

## Як проходить подія

```text
HTTP -> BookingService @Transactional
          |-> bookings (business row)
          |-> booking_outbox (event with a unique eventId)
        COMMIT
          -> OutboxPublisher -> RabbitMQ -> BookingEventListener
                                             -> BookingEventInbox @Transactional
                                             -> booking_event_receipts COMMIT
                                             -> RabbitMQ ACK
```

Бронювання й outbox зберігаються в одній H2-транзакції. Відкат видаляє обидва
записи. Окремий scheduled-потік читає лише committed-рядки та надсилає подію.
HTTP-запит не чекає на RabbitMQ або tour-service. Опитування outbox кожну секунду
означає, що журнал оновлюється з невеликою затримкою.

Producer позначає подію відправленою тільки після publisher confirm та перевірки,
що повідомлення не було повернуте як unroutable. Якщо брокер недоступний, запис
залишається у файловій БД для наступної спроби, навіть після перезапуску producer.

Доставка має семантику at-least-once: збій між RabbitMQ confirm і оновленням outbox
може спричинити повторне надсилання того самого eventId. Consumer перевіряє eventId
у БД; додатково eventId є PRIMARY KEY. Повтор не створює другого запису й не змінює
час першого отримання. Дедуплікація переживає перезапуск consumer.

Топологію оголошують обидва сервіси: черга існує, навіть якщо consumer ще ніколи
не запускався. Durable exchange/queue, persistent messages і volume RabbitMQ
зберігають події, доки consumer зупинений. Підтвердження consumer відбувається
після успішного повернення listener, коли транзакція вже закомічена.
Помилки обробки повторюються тричі; після цього повідомлення зберігається в
`travel.tour.booking-events.failed` для аналізу.

`common` містить тільки DTO `BookingEvent` і його enum-и. Сервіси не мають Maven-
залежностей один на одного та не викликають один одного через HTTP у цьому процесі.

## Перевірка перед здачею

У гілці lab8 доступна повна база лабораторних 1–7 та реалізація №8.
Команда `./scripts/mvn17.sh clean install` автоматично вибирає встановлену Java 17
на macOS, перебудовує всі модулі, запускає тести та встановлює common у локальний
Maven repository. Це також дозволяє запускати модулі через Maven окремо.

## Запуск усіх компонентів у Docker

З кореня проєкту, з Java 17:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn verify
docker compose --profile apps up -d --build
```

`compose.yml` є основною конфігурацією. Старий `docker-compose.yml` замінено нею.
Без `--profile apps` запускаються тільки RabbitMQ і Keycloak, що зручно для IDE.
Перед Docker-збіркою потрібні JAR-и, створені Maven.

| Компонент | Адреса | Демо-доступ |
| --- | --- | --- |
| Tour service | http://localhost:8080 | admin/admin або user/user через Keycloak |
| Booking API | http://localhost:8082/api/bookings | Як у лабораторній №7, без автентифікації |
| Keycloak | http://localhost:8081 | admin/admin у master realm |
| RabbitMQ Management | http://localhost:15672 | travel/travel |
| RabbitMQ AMQP | localhost:5672 | travel/travel |

Compose задає `RABBITMQ_HOST=rabbitmq` обом сервісам. У `application.yml` обох
модулів використано `${RABBITMQ_HOST:localhost}`, а також змінні RABBITMQ_PORT,
RABBITMQ_USERNAME, RABBITMQ_PASSWORD з локальними значеннями за замовчуванням.
Профіль `docker` у tour-service задає внутрішні token/JWK адреси Keycloak, залишаючи
публічну адресу входу для браузера. У демо-профілів Keycloak додано email, потрібний
для видачі password-grant JWT.

Бронювання та outbox: volume `booking-data`. Журнал consumer: `tour-data`.
RabbitMQ: `rabbitmq-data`. `docker compose down` зберігає volumes.
Команда `down -v` видаляє ці навчальні дані.

## Запуск із IDE або локальними JAR

```bash
docker compose up -d
```

Імпортуйте кореневий Maven-проєкт у IDE та запустіть обидва main-класи.
Або після `mvn verify` запустіть у двох терміналах з кореня:

```bash
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
java -jar tour-service/target/tour-service-0.0.1-SNAPSHOT.jar
```

Якщо використовуєте `mvn -pl ... spring-boot:run`, спочатку виконайте `mvn install`,
щоб Maven міг знайти common у локальному repository. Для JAR та IDE це не потрібно.
Працюйте або з Docker-сервісами, або з локальними сервісами на тих самих портах.
Локальні файлові БД зберігаються у `data/` відносно working directory.

## Ручна демонстрація

1. Виконайте отримання JWT у `http/lab8-message-brokers.http`.
2. Створіть бронювання: отримайте 201 та його id. Через секунду прочитайте журнал
   consumer: один запис з CREATED.
3. Підтвердіть і скасуйте те саме бронювання: з'являться ще два записи.
4. Повторно підтвердіть скасоване: 409, нової події немає. Некоректний JSON/DTO
   дає 400 і не створює бронювання або outbox.
5. Виконайте `docker compose --profile apps stop tour-service`, створіть ще два
   бронювання. Обидва запити дають 201, а RabbitMQ показує дві Ready-події в черзі
   `travel.tour.booking-events`.
6. Виконайте `docker compose --profile apps start tour-service`; після запуску
   обидва записи з'являться через GET /api/booking-events.
7. У RabbitMQ Management перевідправте JSON тієї самої події в exchange
   `travel.booking.events`, routing key `booking.created`, з тим самим eventId і
   content_type=application/json. Кількість записів consumer не збільшиться.

## Автоматична перевірка

`mvn verify` не потребує Docker або Keycloak. Він запускає 153 тести: 146 у
tour-service і 7 у booking-service. Покриття tour-service перевіряється JaCoCo
з порогом 60%, успадкованим із попередніх лабораторних.

Після запуску повного Docker-стека:

```bash
python3 scripts/check-lab8.py
```

Скрипт створює демо-бронювання з префіксом lab8-check, тимчасово зупиняє/запускає
consumer, перезапускає RabbitMQ та producer. Перевіряє три типи подій, помилкові
дії, доставку після зупинки consumer, стійкість черги після рестарту брокера,
реальний дублікат і відновлення outbox після рестарту producer без брокера.
Наприкінці відновлює запущений стан контейнерів. Дані демонстрації залишаються
видимими через API, щоб їх можна було показати викладачу.

## Де реалізовано кожен acceptance criterion

| Сценарій | Файли та доказ |
| --- | --- |
| 1. Одна дія -> одна подія -> видимий результат | BookingController, BookingService, OutboxPublisher, RabbitBookingEventPublisher; BookingEventListener -> BookingEventInbox -> GET /api/booking-events. Тест threeActionsProduceExactlyOneEventEach і Docker-перевірка. |
| 2. Лише після commit; без події при rollback | @Transactional у BookingService та booking_outbox у тій самій БД. Тести publisherOnAnotherThreadCannotSeeUncommittedEvent і rollbackRemovesBothBusinessRowAndOutboxRowAndNothingIsPublished. |
| 3. Без HTTP і залежностей на класи іншого сервісу | POM-и залежать лише від common DTO. Передавання виключно через RabbitTemplate та @RabbitListener. |
| 4. Consumer зупинений, два запити успішні, після старту обидва обробляються | Дві BookingMessagingConfig, durable queue/exchange, persistent delivery, AUTO ACK, volume. Відтворюється scripts/check-lab8.py. |
| 5. Повторна подія не змінює результат + unit-тест | BookingEventInbox і PK event_id; BookingEventInboxTest (Mockito, без Spring і RabbitMQ), BookingEventPersistenceTest (реальна H2), повторна доставка в Docker. |
| 6. Адреса брокера з env; Docker використовує ім'я сервісу; IDE має default | application.yml обох сервісів + environment у compose.yml. |

## Основні файли

- `common/src/main/java/com/example/travelagency/contract/BookingEvent.java`: контракт JSON.
- `booking-service/src/main/java/com/example/travelagency/booking/BookingService.java`: три транзакційні бізнес-дії.
- У тому самому модулі `BookingRepository.java`: JDBC-сховище бронювань.
- Пакет `booking.messaging`: outbox, повторне надсилання, підтвердження RabbitMQ і топологія.
- `booking-service/src/main/resources/db/changelog/db.changelog-master.xml`: bookings та booking_outbox.
- `tour-service/.../messaging`: топологія, listener, дедуплікація, журнал і REST-ендпоінт.
- `tour-service/src/main/resources/db/changelog/005-booking-events.xml`: схема журналу consumer.
- `tour-service/.../config/DockerOidcConfig.java`: доступ до Keycloak у Docker.
- `compose.yml`: брокер, два сервіси, Keycloak, volumes, адреси та порти.

Технічні джерела: [Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/template.html),
[RabbitMQ confirms та acknowledgements](https://www.rabbitmq.com/docs/confirms).


## Результат перевірки після відновлення гілки

Гілку lab8 відновлено на базі lab7. Чистий запуск ./scripts/mvn17.sh clean install
завершився BUILD SUCCESS: 153 тести, 0 failures/errors/skipped.
Покриття рядків tour-service: 89,37%.
Повторна перевірка scripts/check-lab8.py із реально запущеним RabbitMQ пройшла
для всіх сценаріїв, включно з перезапуском брокера й відновленням outbox.
