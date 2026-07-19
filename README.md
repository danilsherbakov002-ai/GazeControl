# GazeControl

Android-приложение: управление телефоном взглядом (курсор по взгляду, клик по задержке взгляда — как Eye Tracking в iOS), плюс моргание левым/правым глазом = назад/домой.

## Сборка APK ТОЛЬКО С ТЕЛЕФОНА (без компьютера)

Идея: код пушится на GitHub с телефона через Termux, а сама сборка APK происходит на серверах GitHub Actions (workflow уже лежит в `.github/workflows/build.yml`). Готовый APK потом скачивается из вкладки Actions.

### 1. Установи на телефон
- **Termux** — из F-Droid (https://f-droid.org/packages/com.termux/), НЕ из Play Store (там устаревшая версия)
- Аккаунт на GitHub (обычный, бесплатный)

### 2. В Termux
```
pkg update -y
pkg install -y git unzip gh
termux-setup-storage
```
(разреши доступ к хранилищу, когда всплывёт запрос)

### 3. Достань файлы проекта
Если этот .zip лежит в Загрузках телефона:
```
cd ~/storage/downloads
unzip GazeControl.zip
cd GazeControl
```

### 4. Скачай модель MediaPipe (у Termux уже есть полный доступ в интернет с телефона — в отличие от моей песочницы)
```
curl -L -o app/src/main/assets/face_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
```

### 5. Залей код на GitHub
```
gh auth login
gh repo create GazeControl --private --source=. --remote=origin --push
```
(`gh auth login` спросит логин через браузер — просто следуй подсказкам)

Если `gh repo create` не сработает, вручную:
```
git init
git add -A
git commit -m "initial"
git branch -M main
git remote add origin https://github.com/ТВОЙ_НИК/GazeControl.git
git push -u origin main
```
(репозиторий сначала создай на github.com через браузер, кнопка "New repository")

### 6. Дождись сборки
Открой в браузере телефона: `github.com/ТВОЙ_НИК/GazeControl/actions` — там появится запуск workflow "Build APK" (занимает 2-4 минуты). Когда станет зелёным — зайди внутрь запуска, внизу раздел **Artifacts** → скачай `GazeControl-debug-apk`. Внутри архива будет `app-debug.apk`.

### 7. Установи APK
Открой скачанный `app-debug.apk` через файловый менеджер, разреши установку из неизвестных источников — и приложение появится на телефоне.

### После правок кода
Просто повторяй с шага 5 (`git add -A && git commit -m "fix" && git push`) — Actions пересоберёт APK автоматически при каждом пуше.

## Как пользоваться в приложении

1. Открой приложение → **"Включить службу специальных возможностей"** → найди GazeControl в списке → включи.
2. **"Калибровка"** — 9 точек, на каждую смотри ~1.2 сек, не отводя взгляд.
3. **"Запустить управление взглядом"** — появится розовое кольцо-курсор, следующее за взглядом. Задержи взгляд на месте (по умолчанию 800 мс, регулируется ползунком) — произойдёт клик.
4. Моргание **левым** глазом = "Назад". Моргание **правым** глазом = "Домой".

## Технические детали

- **Отслеживание взгляда**: MediaPipe Face Landmarker (478 точек лица + радужка + blendshapes для моргания), кадры с фронтальной камеры через CameraX (`GazeEngine.kt`, `CameraGazeSource.kt`).
- **Калибровка**: 9-точечная, линейная регрессия методом наименьших квадратов (`CalibrationManager.kt`).
- **Курсор и клики**: `GazeAccessibilityService.kt`, оверлей через `TYPE_ACCESSIBILITY_OVERLAY` (доп. разрешение "поверх экрана" не требуется), клики через `dispatchGesture`.
- **Фоновая работа**: `GazeTrackingService.kt` — foreground-сервис с камерой, координаты идут через `GazeBus.kt`.

## Известные ограничения

- Оценка взгляда упрощённая (радужка относительно уголков глаза), без полной 3D-модели головы — держи телефон примерно на том же расстоянии, что и при калибровке.
- Сильное смещение телефона/головы после калибровки снижает точность — перекалибруйся через MainActivity при необходимости.
- Ориентация экрана (портрет/ландшафт) во время калибровки должна совпадать с той, в которой будешь пользоваться.
