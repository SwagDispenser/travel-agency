# Лабораторна робота 6: Quartz Scheduler

## Що реалізовано

Це реальні операції над базою даних Travel Agency:

1. `ExpiredFeaturedCleanupJob`: зняття `featured` із завершених турів, дата завершення яких раніше за поточний день мінус `graceDays`.
2. `DailyTourReportJob`: створення щоденного знімка кількості майбутніх турів та доступних місць для країни й горизонту планування.

Тури не видаляються. Звіт зберігається у `daily_tour_reports`; вже створений
знімок для тієї самої дати, країни й горизонту не перезаписується.

## Файли

Java-шляхи відносні до `src/main/java/com/example/travelagency/`.

| Файл | Призначення |
| --- | --- |
| `config/QuartzConfig.java` | Єдине місце оголошення двох JobDetail, трьох Trigger та годинника Europe/Kyiv. |
| `scheduling/ExpiredFeaturedCleanupJob.java` | Читає graceDays та один раз викликає сервіс очищення. |
| `scheduling/DailyTourReportJob.java` | Читає country/horizonDays та один раз викликає сервіс звітів. |
| `scheduling/JobParameters.java` | Перевіряє обов'язкові текстові й числові параметри. |
| `scheduling/TourMaintenanceService.java` | SQL, ідемпотентність і @Transactional на рівні бізнес-операцій. |
| `web/QuartzAdminController.java` | Перегляд jobs, ручне подання jobs до Quartz та читання збережених звітів. |
| `src/main/resources/db/changelog/quartz-h2.sql` (від кореня) | Схема 11 таблиць QRTZ_* для H2. |
| `src/main/resources/db/changelog/003-quartz.xml` (від кореня) | Liquibase changeset для Quartz, автор dmytro. |
| `src/main/resources/db/changelog/004-daily-tour-reports.xml` (від кореня) | Таблиця звітів із складеним первинним ключем, автор dmytro. |

У `pom.xml` додано `spring-boot-starter-quartz`; версією Quartz керує Spring Boot
3.3.5, фактично використовується Quartz 2.3.2. Два нових changelog-файли
підключено до master. Старі changeset-и не змінено.

## JDBC Job Store та Liquibase

Поточна база проєкту: H2 2.x із `MODE=PostgreSQL`, а не окремий PostgreSQL сервер.
Схему взято з `org/quartz/impl/jdbcjobstore/tables_h2.sql` всередині
`quartz-2.3.2.jar`. Двійкові поля `IMAGE` адаптовано до `BYTEA`, сумісного з
поточним режимом H2. У SQL немає DROP TABLE або власного COMMIT: виконанням
і повторним застосуванням керує Liquibase. Міграція Quartz позначена `dbms="h2"`.
Якщо змінити справжню СУБД, для неї потрібна відповідна окрема схема Quartz.

У `application.yml`:

```yaml
spring:
  quartz:
    job-store-type: jdbc
    scheduler-name: travelAgencyScheduler
    overwrite-existing-jobs: false
    wait-for-jobs-to-complete-on-shutdown: true
    jdbc:
      initialize-schema: never
    properties:
      org.quartz.threadPool.threadCount: 2
```

Spring Boot підключає DataSource проєкту до `LocalDataSourceJobStore`.
Таблиці створює Liquibase до використання scheduler-а. Сам Boot не створює і
не очищує таблиці Quartz. Стабільні імена й `overwrite-existing-jobs: false`
зберігають наявні визначення jobs, їхні параметри та стани тригерів після перезапуску.

Зміна cron-виразу або JobDataMap лише в Java-конфігурації після першого запуску
не перезапише вже збережений розклад. Для свідомої зміни існуючого розкладу
потрібно викликати `Scheduler.rescheduleJob`, а для оновлення JobDetail —
`Scheduler.addJob(..., true)`. Видаляти QRTZ_* для звичайного перезапуску не потрібно.
Це одновузлова конфігурація; кластерний запуск кількох застосунків тут не налаштований.

## Jobs, тригери й параметри

Спільна група: `travel-maintenance`. Усі cron-тригери мають явний часовий пояс
`Europe/Kyiv`. `ZoneId.of` перевіряє назву зони перед створенням TimeZone,
тому помилковий ідентифікатор не буде мовчки прийнятий як GMT.

| Job name | Trigger name | Час Europe/Kyiv | Параметри | Misfire |
| --- | --- | --- | --- | --- |
| `expired-featured-cleanup` | `cleanup-nightly` | Щодня 02:00 | Job: graceDays="0" | DoNothing: пропустити втрачений запуск, чекати наступного. |
| `daily-tour-report` | `report-all-morning` | Щодня 08:00 | Job: country="ALL", horizonDays="30" | FireAndProceed: один запуск після відновлення. |
| `daily-tour-report` | `report-poland-evening` | Щодня 18:00 | Trigger: country="Poland"; horizonDays залишається "30" із Job. | DoNothing. |

Параметри читаються через `context.getMergedJobDataMap()`: Quartz сам поєднує
дві мапи, надаючи пріоритет Trigger. У мапах зберігаються лише рядкові значення;
сервіси, DataSource та інші Spring-біни до мап не записуються.

Job-класи мають лише читання/перевірку параметрів, один виклик сервісу та
обробку помилки. Конструкторну ін'єкцію сервісу забезпечує SpringBeanJobFactory
від Spring Boot. Транзакції відкриваються на публічних методах сервісу,
а не всередині Job або його конструктора.

Відсутній/порожній обов'язковий ключ, нечислове або від'ємне число спричиняє
явний IllegalArgumentException, який Job загортає в JobExecutionException.
Зокрема, `horizonDay` не замінює ключ `horizonDays`: якщо правильного ключа
немає, тест підтверджує помилку; якщо він є у JobDetail, залишається його default.

## Ідемпотентність, конкурентність і помилки

Очищення оновлює лише рядки з `featured=TRUE`. Після першого запуску ті самі
рядки більше не підходять під умову, тож другий виклик оновлює нуль рядків.
Тури, які завершуються сьогодні або пізніше, залишаються незмінними.

Звіт використовує ключ `(report_date, country, horizon_days)` та INSERT із
перевіркою відсутності знімка. Другий запуск для того самого ключа нічого
не вставляє і не змінює created_at. Ранковий ALL та вечірній Poland створюють
різні знімки. Знімок охоплює тури зі start_date від поточного дня до
поточного дня плюс horizonDays включно.

На обох Job-класах є `@DisallowConcurrentExecution`. Quartz серіалізує
виконання одного JobKey, у тому числі якщо його викликають різні тригери.
Ця анотація діє на JobKey, а не забороняє глобально всі jobs цього класу.
У конфігурації для звіту існує один JobDetail, на який посилаються два тригери.

Обрана стратегія збою: записати причину в лог і чекати наступного запуску.
`JobExecutionException(..., false)` не вимагає негайного повтору;
unschedule-прапорці теж не встановлюються. Немає циклів або необмежених retry.
Повтор після misfire створює знімок для поточного дня, без історичного backfill.

## Тести й відповідність PDF

Тести в `src/test/java/com/example/travelagency/scheduling/`.

| Сценарій PDF | Доказ |
| --- | --- |
| Jobs і тригери оголошені в одному місці, мають назви/групи та JobKey | QuartzConfig; `QuartzJobsTest.definitions_haveStableNamesGroupsJobKeysAndTwoReportTriggers`. |
| Параметр Trigger має пріоритет, інші defaults збережені | `quartzMergedData_triggerOverridesCountryAndRetainsDefaultHorizon`: справжній Quartz JobExecutionContextImpl; також реальний запуск scheduler-а в інтеграційному тесті. |
| Легкі Job-класи, один виклик сервісу, транзакція в сервісі | Unit-тести verify/verifyNoMoreInteractions; `realDatabaseOperations_areTransactionalAndIdempotent` перевіряє активну транзакцію під час обох операцій. |
| Помилка відсутніх/неправильних параметрів | `misspelledParameter_failsExplicitlyWithoutCallingBusinessLogic`, `invalidNumericParameter_failsWithoutCallingService`, `blankCountry_failsExplicitly`, `nonStringParameter_hasAnExplicitContractError`. |
| Паралельне виконання заборонено | `sameJobKey_secondExecutionIsBlockedUntilFirstCompletes`: справжній JDBC scheduler, другий тригер має стан BLOCKED, максимум одне активне виконання. |
| Кожен тригер має misfire-політику | `everyTrigger_hasAnExplicitMisfirePolicyAndKyivTimeZone` перевіряє інструкцію кожного з трьох тригерів. |
| Cron має явний часовий пояс | Той самий тест перевіряє Europe/Kyiv; інтеграційний тест перевіряє збережений timezone після перезапуску. |
| Повторна обробка не змінює результат | `realDatabaseOperations_areTransactionalAndIdempotent`: справжня H2, два послідовні виклики кожної операції, порівняння рядків і created_at. |
| Збій оброблений, немає нескінченного повтору | `serviceFailure_isWrappedWithoutRefireOrUnscheduling`: обидва Job-класи, один виклик сервісу, початкова причина збережена, всі refire/unschedule-прапорці false. |
| QRTZ_* створені Liquibase, initialize-schema=never | `liquibaseCreatesJdbcStore_andRestartPreservesJobsParametersAndPausedTrigger`: перевіряє 11 таблиць, запис changeset/author, JDBC persistence та властивість never. |

Окремий тест перезапускає два Spring-контексти на одному тимчасовому файловому
H2: збережений horizonDays="45" і стан PAUSED залишаються, кількість jobs=2,
тригерів=3, міграція застосована лише один раз. Перезапуск не моделюється моком.
Тести не торкаються робочого файлу `data/travel-agency.mv.db` і не потребують Keycloak.
Для очікувань використано latch/Awaitility із тайм-аутами, без Thread.sleep.

Частина старих HTTP-тестів попередніх лабораторних вимикає DataSource;
у їхніх логах може бути RAMJobStore. Саме новий QuartzJdbcIntegrationTest
перевіряє JDBC store та реальні QRTZ_* із міграцій.

## Запуск і результати

```bash
cd /Users/dmytro/Desktop/Java2/travel-agency
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn verify
```

Перевірено: 143 тести, 0 failures/errors/skipped; 21 новий сценарій №6.
Покрито 494 із 526 рядків: 93,92%. Поріг JaCoCo 60% збережено.
Звіт: `target/site/jacoco/index.html`.

Тільки №6:

```bash
mvn -Dtest=QuartzJobsTest,QuartzJdbcIntegrationTest,QuartzAdminControllerTest test
```

Ручний запуск із Keycloak:

```bash
docker compose up -d
mvn spring-boot:run
```

Адреса застосунку: `http://localhost:8080`. Готові запити:
[http/lab6-quartz.http](http/lab6-quartz.http). Шляхи `/admin/**` вже захищені
наявним SecurityFilterChain, тому для ручного запуску потрібна роль ADMIN.
POST приймає job у Quartz і повертає 202; це не означає, що job уже завершився.
Після повідомлення про завершення в логах повторіть GET `/admin/quartz/reports`.

## SQL для захисту

У H2 Console використайте JDBC URL із application.yml, користувача `sa` без пароля.

```sql
SELECT ID, AUTHOR, FILENAME FROM DATABASECHANGELOG;
SELECT JOB_NAME, JOB_GROUP, IS_NONCONCURRENT FROM QRTZ_JOB_DETAILS;
SELECT TRIGGER_NAME, TRIGGER_GROUP, JOB_NAME, TRIGGER_STATE, MISFIRE_INSTR FROM QRTZ_TRIGGERS;
SELECT TRIGGER_NAME, CRON_EXPRESSION, TIME_ZONE_ID FROM QRTZ_CRON_TRIGGERS;
SELECT * FROM daily_tour_reports ORDER BY report_date, country;
```

Для наочного очищення за потреби додайте окремий прострочений демонстраційний тур:

```sql
INSERT INTO tours (title, country, city, start_date, end_date, price, available_seats, featured)
VALUES ('Quartz cleanup demo', 'Poland', 'Demo', DATE '2020-01-01', DATE '2020-01-03', 220.00, 5, TRUE);
```

Запустіть cleanup двічі: у логах перший раз змінено цей рядок, другий — нуль
додаткових рядків. Створення такого туру через звичайний API не підходить,
оскільки наявна валідація забороняє минулі дати.

## Порядок захисту

1. Покажіть залежність Quartz, application.yml, нові changeset-и та 11 QRTZ_*.
2. У QuartzConfig покажіть два JobDetail, три Trigger, JobKey, defaults та override Poland.
3. У Job-класах покажіть читання merged data й один виклик сервісу; у сервісі — @Transactional та реальний SQL.
4. Через HTTP запустіть звіт ALL, потім Poland; покажіть збережені знімки та незмінність після повтору.
5. Покажіть інтеграційний тест блокування другого запуску і тести неправильних параметрів/помилок.
6. Покажіть misfire-інструкції та timezone-тест.
7. Запустіть тест перезапуску з файловою H2; поясніть, чому стан і параметри не перезаписуються.
8. Покажіть успішний mvn verify та JaCoCo.

## Джерела

- [Spring Boot 3.3: Quartz Scheduler](https://docs.spring.io/spring-boot/3.3/reference/io/quartz.html).
- Схема з артефакту Quartz 2.3.2: `org/quartz/impl/jdbcjobstore/tables_h2.sql`.
- [SpringBeanJobFactory у Spring Framework 6.1.14](https://github.com/spring-projects/spring-framework/blob/v6.1.14/spring-context-support/src/main/java/org/springframework/scheduling/quartz/SpringBeanJobFactory.java).
