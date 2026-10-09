# Travel Agency - Spring Security Lab 1

Простий Spring Boot застосунок для предметної області **Travel Agency**. Основна сутність - `Tour`.

Лабораторна робота №2 (unit-тести, 85,40% покриття та доказ мутаційної
перевірки): [LAB2_TESTING.md](LAB2_TESTING.md). Для її запуску потрібна Java 17;
команди наведені в цьому файлі. Docker і Keycloak для unit-тестів не потрібні.

Лабораторна робота №3: три кастомні анотації, відповідність AC 1–8,
тести та приклади запитів описані в [LAB3_CUSTOM_ANNOTATIONS.md](LAB3_CUSTOM_ANNOTATIONS.md).
Повний `mvn verify` тепер також запускає локальні HTTP-тести без БД і Keycloak.

Лабораторна робота №4: три пари анотація + AOP-аспект, свідомий self-invocation
і відповідні тести описані в [LAB4_AOP.md](LAB4_AOP.md).
Демонстраційні запити: [http/lab4-aop.http](http/lab4-aop.http).

## Що реалізовано

- REST CRUD API для турів: `GET/POST/PUT/DELETE /api/tours`.
- Простий UI на Thymeleaf: `/tours`.
- Шари `Controller / Service / Repository / Entity / DTO`.
- MapStruct для мапінгу `Tour <-> TourRequest/TourResponse`.
- Liquibase-міграції для створення та наповнення таблиці `tours`.
- Spring Security з двома способами автентифікації:
  - OIDC login для UI через Keycloak.
  - JWT Bearer token для API-запитів.
- Обмеження доступу:
  - через `SecurityFilterChain` для `/admin/**`;
  - через `@PreAuthorize` у `TourService`.
- Готовий HTTP-файл для демонстрації: `http/travel-agency.http`.

## Запуск

### 1. Запустити Keycloak

```bash
docker compose up -d
```

Keycloak буде доступний на `http://localhost:8081`.

Готові користувачі:

| Логін | Пароль | Ролі |
| --- | --- | --- |
| `admin` | `admin` | `USER`, `ADMIN` |
| `user` | `user` | `USER` |

### 2. Запустити Spring Boot застосунок

```bash
mvn spring-boot:run
```

Застосунок буде доступний на `http://localhost:8080`.

База даних H2 створиться автоматично у папці `data/`. Схему створює Liquibase, а Hibernate працює у режимі `validate`.

### 3. Перевірити UI

Відкрийте:

```text
http://localhost:8080/tours
```

Якщо користувач не увійшов, Spring Security перенаправить його на Keycloak login page.

`admin/admin` може створювати, редагувати, видаляти та перемикати `featured`.

`user/user` може переглядати список, але не може виконувати admin-дії.

### 4. Перевірити API через HTTP-файл

Відкрийте `http/travel-agency.http` у IntelliJ IDEA або VS Code REST Client і виконайте запити зверху вниз:

1. `Get ADMIN JWT`
2. `Get USER JWT`
3. CRUD-запити до `/api/tours`
4. Демо 403 для `/admin/status`
5. Демо 403 для `@PreAuthorize`

## Де реалізовані критерії з PDF

| Acceptance criterion | Реалізація |
| --- | --- |
| 1. Базовий CRUD-додаток працює | REST CRUD у `TourRestController`, бізнес-логіка у `TourService`, доступ до БД через `TourRepository`, сутність `Tour`, DTO `TourRequest` і `TourResponse`. MapStruct-мапер: `TourMapper`. |
| 2. Схема БД керується міграціями | `src/main/resources/db/changelog/db.changelog-master.xml`. У `application.yml` вказано `spring.jpa.hibernate.ddl-auto=validate`, тому таблиці створює Liquibase, не Hibernate. |
| 3. Базовий UI існує | `TourPageController`, шаблони `templates/tours/list.html` і `templates/tours/form.html`. UI дозволяє створення, читання, оновлення та видалення турів для `ADMIN`. |
| 4. OIDC-автентифікація для UI | `SecurityConfig` містить `oauth2Login`. Налаштування клієнта Keycloak у `application.yml`; realm/client/users у `keycloak/realm-export.json`. |
| 5. JWT-автентифікація для API | `SecurityConfig` містить `oauth2ResourceServer().jwt(...)`. `application.yml` містить issuer і JWK Set URI. Приклади Bearer-запитів є у `http/travel-agency.http`. |
| 6. Обмеження доступу через SecurityFilterChain | У `SecurityConfig` правило `.requestMatchers("/admin/**").hasRole("ADMIN")`. Перевірка: `GET /admin/status` з USER token повертає 403. |
| 7. Обмеження доступу через @PreAuthorize | У `TourService` методи `create`, `update`, `delete`, `toggleFeatured` мають `@PreAuthorize("hasRole('ADMIN')")`. USER token на `POST /api/tours` повертає 403. |
| 8. Готовність до захисту | `http/travel-agency.http` містить отримання JWT для ADMIN/USER, CRUD-запити та окремі 403-сценарії для обох механізмів захисту. |

## Корисні ендпоінти

| Метод | URL | Доступ |
| --- | --- | --- |
| `GET` | `/api/tours` | authenticated |
| `GET` | `/api/tours/{id}` | authenticated |
| `POST` | `/api/tours` | `ADMIN`, через `@PreAuthorize` |
| `PUT` | `/api/tours/{id}` | `ADMIN`, через `@PreAuthorize` |
| `DELETE` | `/api/tours/{id}` | `ADMIN`, через `@PreAuthorize` |
| `POST` | `/api/tours/{id}/toggle-featured` | `ADMIN`, через `@PreAuthorize` |
| `GET` | `/admin/status` | `ADMIN`, через `SecurityFilterChain` |
| `GET` | `/tours` | authenticated OIDC session |

## Ручне отримання JWT через curl

```bash
curl -X POST http://localhost:8081/realms/travel-agency/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=travel-agency-ui" \
  -d "client_secret=travel-agency-secret" \
  -d "username=admin" \
  -d "password=admin"
```

Отримане `access_token` треба передавати так:

```bash
curl http://localhost:8080/api/tours \
  -H "Authorization: Bearer <access_token>"
```
# travel-agency
