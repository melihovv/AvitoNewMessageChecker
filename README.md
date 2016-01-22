# AvitoNewMessageChecker
Скрипт для проверки новых сообщений в личном кабинете avito.ru.

Скрипт открывает браузер firefox, логинится на avito.ru с логином и паролем,
указаными в config.yaml, и проверяет наличие непрочитанных сообщений. Если такие
есть, отправляет уведомление на почту, также указанную в config.yaml.

Перед запуском следует переименовать config.example.yaml в config.yaml и
заполнить необходимые поля.

Запуск:
java -Dlog4j.configurationFile=/path/to/log.xml /path/to/config.yaml

По умолчанию логи пишутся на консоль и в файл log.txt (меняется в log.xml).
