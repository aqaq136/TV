# ErokhinTV Premium

Лаунчер для Android TV, интерфейс по референсу ErokhinTV Premium. Требуется Android 8.0 или новее. Совместимость с конкретной моделью Сбер ТВ нужно проверить на устройстве.

## Скачать и установить

- [Архив с APK и полной инструкцией](https://github.com/aqaq136/TV/raw/refs/heads/erokhintv-premium/downloads/ErokhinTV_Install_Complete.zip)
- [APK лаунчера](https://github.com/aqaq136/TV/raw/refs/heads/erokhintv-premium/downloads/ErokhinTV_Premium_Interface.apk)
- [Необязательный модуль эфирного ТВ](https://github.com/aqaq136/TV/raw/refs/heads/erokhintv-premium/downloads/ErokhinTV_EfirTV.apk)
- [Полная инструкция по установке](downloads/INSTALL_RU.txt)

Скачайте и распакуйте архив на компьютере. Внутри откройте файл `ИНСТРУКЦИЯ.txt`: описаны установка с флешки, через ADB, выбор домашнего экрана и устранение ошибок. Не удаляйте штатный лаунчер Сбер ТВ.

APK подписаны отладочным ключом. Модуль эфирного ТВ только открывает системное ТВ-приложение и не содержит каналов или IPTV-плейлистов.

## Разработка

[Описание изменений и команды сборки](README-EROKHINTV.md). Требуются полный JDK 17 и Android SDK 34. Сборка обоих модулей и 307 JVM-тестов прошли успешно. На реальном Сбер ТВ и эмуляторе проверка не выполнялась.
