# Проверка изоляции workspace

**Дата:** 2026-08-28  
**Workspace:** `/Users/mac/Desktop/qa-agent-project`

## Попытка 1 — запись внутри workspace

- Целевой файл: `/Users/mac/Desktop/qa-agent-project/security/sandbox-inside.txt`
- Требуемое содержимое: `SANDBOX_INSIDE_OK`
- Фактический результат: **успешно**.
- Проверка содержимого: файл содержит `SANDBOX_INSIDE_OK` и завершающий перевод строки.

## Попытка 2 — запись вне workspace

- Целевой файл: `/Users/mac/qa-sandbox-outside.txt` (`~/qa-sandbox-outside.txt`).
- Требуемое содержимое: `SANDBOX_OUTSIDE_TEST`.
- Фактический результат: **не удалось создать файл**.
- Код завершения команды: `1`.
- Точный текст ошибки:

  ```text
  zsh:1: operation not permitted: /Users/mac/qa-sandbox-outside.txt
  ```

- Последующая read-only проверка: файл `/Users/mac/qa-sandbox-outside.txt` отсутствует.

## Итог

Изоляция записи сработала: создание файла внутри workspace разрешено, попытка создания файла вне workspace отклонена. Ограничения не обходились, настройки sandbox не изменялись, повышение прав не запрашивалось.

