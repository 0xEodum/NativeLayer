# Обновленные цепочки взаимодействия классов YuMSG
**С BackgroundService как координатором бизнес-логики**

## 1. Инициализация приложения (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Запуск приложения (SplashScreen)**
```
BackgroundService.initialize() ->
  SharedPreferencesManager.initialize() ->
  DatabaseManager.initialize(dbPath) ->  // ⚠️ Требует параметр
  CryptoManager.initialize() ->
  StateManager.initialize() ->
  SessionManager.initialize() ->
  UIBridge.initialize(flutterEngine) ->  // ⚠️ Требует параметр
  StateManager.getCurrentAppState() ->
  StateManager.setAppState(INITIALIZING) ->
  SharedPreferencesManager.getAppMode() ->
  StateManager.determineStartScreen() ->
  UIBridge.sendEventToUI(NavigateToScreen)
```

## 2. Выбор режима работы (ОБНОВЛЕНО)

**Сценарий: Пользователь выбирает режим (ModeSelectionScreen)**
```
UIBridge.handleUIMethod("selectMode") ->
  BackgroundService.selectAppMode(mode) ->
    SharedPreferencesManager.setAppMode(mode) ->
    StateManager.setAppState(MODE_SELECTED) ->
    CryptoManager.getDefaultAlgorithms() ->
    SharedPreferencesManager.setCryptoAlgorithms(defaultAlgorithms) ->
    return CompletableFuture<Boolean>
  .thenAccept(success -> UIBridge.sendEventToUI("ModeSelected"))
  .exceptionally(throwable -> UIBridge.onErrorOccurred(error))
```

## 3. Подключение к серверу (ОБНОВЛЕНО)

**Сценарий: Подключение к корпоративному серверу (ServerConnectionScreen)**
```
UIBridge.handleUIMethod("connectToServer") ->
  BackgroundService.connectToServer(host, port, orgName) ->
    SharedPreferencesManager.setServerConfig(host, port, orgName) ->
    
    // Асинхронная цепочка внутри BackgroundService:
    ServerNetworkManager.connect(connectionParams)  // -> CompletableFuture<ConnectionResult>
      .thenCompose(connectionResult -> {
        if (connectionResult.isSuccess()) {
          return ServerNetworkManager.getOrganizationInfo();  // -> CompletableFuture<OrganizationInfo>
        } else {
          throw new ConnectionException(connectionResult.getMessage());
        }
      })
      .thenCompose(orgInfo -> {
        return ServerNetworkManager.getServerAlgorithms();  // -> CompletableFuture<CryptoAlgorithms>
      })
      .thenApply(serverAlgorithms -> {
        SharedPreferencesManager.setCryptoAlgorithms(serverAlgorithms);
        StateManager.setConnectionState(CONNECTED);
        return connectionResult;
      })
  .thenAccept(result -> {
    if (result.isSuccess()) {
      UIBridge.onConnectionStateChanged(CONNECTED);
      UIBridge.sendEventToUI("NavigateToAuth");
    } else {
      UIBridge.onErrorOccurred(new AppError("CONNECTION_FAILED", result.getMessage()));
    }
  })
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("CONNECTION_FAILED", throwable.getMessage()));
    return null;
  });
```

## 4. Серверная авторизация (ОБНОВЛЕНО)

**Сценарий: Вход пользователя на сервере (AuthScreen - server mode)**
```
UIBridge.handleUIMethod("authenticateUser") ->
  BackgroundService.authenticateUser(username, password, email) ->
    UserCredentials credentials = new UserCredentials(username, password, email) ->
    
    // Асинхронная цепочка внутри BackgroundService:
    ServerNetworkManager.authenticateUser(credentials)  // -> CompletableFuture<AuthResult>
      .thenApply(authResult -> {
        if (authResult.isSuccess()) {
          AuthData authData = new AuthData(
            credentials.getUsername(), 
            authResult.getToken(), 
            "orgId", 
            "userId", 
            System.currentTimeMillis() + 3600000
          );
          
          UserSession session = SessionManager.createSession(authData);
          SessionManager.saveUserSession(
            credentials.getUsername(), 
            authResult.getToken(), 
            "orgId", 
            System.currentTimeMillis() + 3600000
          );
          
          StateManager.setAppState(AUTHENTICATED);
        }
        return authResult;
      })
  .thenAccept(authResult -> {
    if (authResult.isSuccess()) {
      UIBridge.sendEventToUI("NavigateToChats");
    } else {
      UIBridge.onErrorOccurred(new AppError("AUTH_FAILED", authResult.getMessage()));
    }
  })
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("AUTH_ERROR", throwable.getMessage()));
    return null;
  });
```

## 5. Локальная авторизация (ОБНОВЛЕНО)

**Сценарий: Вход в локальном режиме (AuthScreen - local mode)**
```
UIBridge.handleUIMethod("setLocalUser") ->
  BackgroundService.setLocalUser(username) ->
    SharedPreferencesManager.setLocalUsername(username) ->
    StateManager.setAppState(AUTHENTICATED) ->
    LocalNetworkManager.startDiscovery() ->
    UserProfile userProfile = SharedPreferencesManager.getUserProfile() ->
    LocalNetworkManager.broadcastPresence(userProfile) ->
    return CompletableFuture<Boolean>
  .thenAccept(success -> UIBridge.sendEventToUI("NavigateToChats"))
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("LOCAL_USER_FAILED", throwable.getMessage()));
    return null;
  });
```

## 6. Инициализация чата (ОБНОВЛЕНО)

**Сценарий: Создание нового чата**
```
UIBridge.handleUIMethod("initializeChat") ->
  BackgroundService.initializeChat(recipientId) ->
    SharedPreferencesManager preferencesManager = SharedPreferencesManager.getInstance() ->
    CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms() ->
    ChatKeys chatKeys = CryptoManager.initializeChatKeysFromPreferences(preferencesManager) ->
    Chat chat = new Chat(UUID.randomUUID().toString(), name) ->
    chat.setKeys(chatKeys) ->
    String chatId = DatabaseManager.saveChat(chat) ->
    StateManager.setActiveChatId(chatId) ->
    
    // Асинхронная отправка:
    NetworkManager.sendChatInitRequest(recipientId, chatUuid, chatKeys.getPublicKeySelf())  // -> CompletableFuture<Void>
      .thenApply(v -> true)
  .thenAccept(success -> UIBridge.sendEventToUI("ChatInitialized"))
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("CHAT_INIT_FAILED", throwable.getMessage()));
    return null;
  });
```

## 7. Отправка сообщения (ОБНОВЛЕНО)

**Сценарий: Пользователь отправляет сообщение (ChatScreen)**
```
UIBridge.handleUIMethod("sendMessage") ->
  BackgroundService.sendMessage(chatId, messageText) ->
    Chat chat = DatabaseManager.getChat(chatId) ->
    ChatKeys chatKeys = chat.getKeys() ->
    byte[] encryptedContent = CryptoManager.encryptMessage(messageText, chatKeys.getSymmetricKey()) ->
    Message message = new Message() ->
    message.setChatId(chatId) ->
    message.setContent(new String(encryptedContent)) ->
    message.setTimestamp(System.currentTimeMillis()) ->
    message.setStatus(MessageStatus.SENDING) ->
    DatabaseManager.saveMessage(message) ->
    
    // Асинхронная отправка:
    StateManager.updateLastActiveTime() ->
    NetworkManager.sendUserMessage(recipientId, chatUuid, encryptedContent)  // -> CompletableFuture<Void>
      .thenApply(v -> true)
  .thenAccept(success -> {
    UIBridge.onProgressUpdate("msg_" + chatId, new Progress("msg_" + chatId, 100, "COMPLETED"));
    UIBridge.sendEventToUI("MessageSent");
  })
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("SEND_FAILED", throwable.getMessage()));
    return null;
  });
```

## 8. Получение сообщения (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Приход сообщения от собеседника**
```
NetworkManager.handlePeerMessage(peerId, messageData) ->  // Приватный метод
  DatabaseManager.getChat(chatId) ->
  CryptoManager.decryptMessage(encryptedContent, symmetricKey) ->
  DatabaseManager.saveMessage(decryptedMessage) ->
  StateManager.getActiveChatId() ->
  UIBridge.onMessageReceived(message) ->
  UIBridge.showNotification(notificationData)
```

## 9. Передача файла (ОБНОВЛЕНО)

**Сценарий: Отправка файла в чате**
```
UIBridge.handleUIMethod("sendFile") ->
  BackgroundService.sendFile(chatId, filePath) ->
    FileInfo fileInfo = new FileInfo() ->
    fileInfo.setPath(filePath) ->
    fileInfo.setName(new File(filePath).getName()) ->
    DatabaseManager.saveFile(fileInfo) ->
    Chat chat = DatabaseManager.getChat(chatId) ->
    ChatKeys chatKeys = chat.getKeys() ->
    CryptoAlgorithms algorithms = SharedPreferencesManager.getCryptoAlgorithms() ->
    EncryptedFileResult encryptedFileResult = CryptoManager.encryptFile(filePath, chatKeys.getSymmetricKey(), algorithms.getSymmetricAlgorithm()) ->
    
    // Асинхронная загрузка:
    FileInfo encryptedFileInfo = new FileInfo() ->
    encryptedFileInfo.setPath(encryptedFileResult.getEncryptedFilePath()) ->
    NetworkManager.uploadFile(encryptedFileInfo)  // -> CompletableFuture<UploadResult>
      .thenCompose(uploadResult -> {
        if (uploadResult.isSuccess()) {
          Message fileMessage = new Message() ->
          fileMessage.setChatId(chatId) ->
          fileMessage.setContent("FILE:" + uploadResult.getFileId()) ->
          DatabaseManager.saveMessage(fileMessage) ->
          
          return NetworkManager.sendUserMessage(recipientId, chatUuid, 
            ("FILE:" + uploadResult.getFileId()).getBytes());
        } else {
          throw new FileUploadException(uploadResult.getMessage());
        }
      })
      .thenApply(v -> true)
  .thenAccept(success -> {
    UIBridge.onProgressUpdate("file_" + chatId, new Progress("file_" + chatId, 100, "FILE_SENT"));
    UIBridge.sendEventToUI("FileSent");
  })
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("FILE_SEND_FAILED", throwable.getMessage()));
    return null;
  });
```

## 10. Обмен ключами KEM (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Установка защищенного соединения**
```
// Инициатор A:
NetworkManager.sendChatInitRequest(recipientId, chatUuid, publicKeyA)  // -> CompletableFuture<Void>

// Получатель B (обработчик):
NetworkManager.handleChatInitRequest() ->  // Приватный обработчик
  CryptoManager.encapsulateSecret(publicKeyA, algorithm) ->
  
  NetworkManager.sendChatInitResponse(recipientId, chatUuid, publicKeyB, kemCapsule, signature)  // -> CompletableFuture<Void>

// Инициатор A (обработчик ответа):
NetworkManager.handleChatInitResponse() ->  // Приватный обработчик
  CryptoManager.extractSecret(kemCapsule, privateKeyA, algorithm) ->
  CryptoManager.deriveSymmetricKey(secretA, secretB) ->
  CryptoManager.completeChatInitialization(chatKeys, secretA, secretB) ->
  DatabaseManager.saveChat(completedChat) ->
  UIBridge.onEncryptionStatusChanged(ACTIVE)
```

## 11. Поиск пользователей (ОБНОВЛЕНО)

**Сценарий: Поиск новых собеседников (ChatListScreen)**
```
UIBridge.handleUIMethod("searchUsers") ->
  BackgroundService.searchUsers(query) ->
    
    // Асинхронный поиск внутри BackgroundService:
    NetworkManager.searchUsers(query)  // -> CompletableFuture<List<User>>
      .thenApply(searchResults -> {
        List<Contact> localContacts = DatabaseManager.getAllContacts();
        // Объединить результаты поиска с локальными контактами
        return searchResults;
      })
  .thenAccept(searchResults -> UIBridge.sendEventToUI("SearchResults"))
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("SEARCH_FAILED", throwable.getMessage()));
    return null;
  });

// После выбора пользователя:
UIBridge.handleUIMethod("startChatWithUser") ->
  BackgroundService.initializeChat(userId) ->  // Использует существующий метод
  [см. сценарий 6 - Инициализация чата]
```

## 12. Выход из приложения (ОБНОВЛЕНО)

**Сценарий: Logout пользователя**
```
UIBridge.handleUIMethod("logout") ->
  BackgroundService.logout() ->
    SessionManager.logout() ->  // Очищает локальные данные
    
    // Асинхронный выход внутри BackgroundService:
    List<Chat> allChats = DatabaseManager.getAllChats() ->
    NetworkManager.logout()  // -> CompletableFuture<Void>
      .thenRun(() -> {
        CryptoManager.cleanup();  // Очистка ключей
        StateManager.resetState();
      })
      .thenApply(v -> true)
  .thenAccept(success -> UIBridge.sendEventToUI("NavigateToModeSelection"))
  .exceptionally(throwable -> {
    // Логируем ошибку, но все равно выходим
    UIBridge.sendEventToUI("NavigateToModeSelection");
    return null;
  });
```

## 13. Смена темы интерфейса (ОБНОВЛЕНО)

**Сценарий: Переключение темной/светлой темы (SettingsScreen)**
```
UIBridge.handleUIMethod("changeTheme") ->
  BackgroundService.changeTheme(themeName) ->
    ThemeMode oldTheme = SharedPreferencesManager.getThemeMode() ->
    ThemeMode newTheme = ThemeMode.valueOf(themeName) ->
    SharedPreferencesManager.setThemeMode(newTheme) ->
    StateManager.notifyStateChange(new StateChange("THEME_CHANGED", oldTheme, newTheme)) ->
    return CompletableFuture<Boolean>
  .thenAccept(success -> UIBridge.sendEventToUI("ThemeUpdated"))
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("THEME_CHANGE_FAILED", throwable.getMessage()));
    return null;
  });
```

## 14. Изменение алгоритмов шифрования (ОБНОВЛЕНО)

**Сценарий: Пользователь меняет алгоритмы (EncryptionSettingsScreen - local mode)**
```
UIBridge.handleUIMethod("updateCryptoAlgorithms") ->
  BackgroundService.updateCryptoAlgorithms(kemAlg, symAlg, sigAlg) ->
    CryptoAlgorithms newAlgorithms = new CryptoAlgorithms(kemAlg, symAlg, sigAlg) ->
    CryptoManager.validateCryptoAlgorithms(newAlgorithms) ->
    CryptoAlgorithms oldAlgorithms = SharedPreferencesManager.getCryptoAlgorithms() ->
    SharedPreferencesManager.setCryptoAlgorithms(newAlgorithms) ->
    CryptoManager.resetStatistics() ->
    StateManager.notifyStateChange(new StateChange("CRYPTO_ALGORITHMS_CHANGED", oldAlgorithms, newAlgorithms)) ->
    return CompletableFuture<Boolean>
  .thenAccept(success -> UIBridge.sendEventToUI("AlgorithmsUpdated"))
  .exceptionally(throwable -> {
    UIBridge.onErrorOccurred(new AppError("ALGORITHMS_UPDATE_FAILED", throwable.getMessage()));
    return null;
  });
```

## 15. Восстановление после сбоя (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Восстановление состояния при перезапуске**
```
BackgroundService.initialize() ->
  StateManager.restoreState() ->
  SharedPreferencesManager.getAppMode() ->
  
  if (appMode == SERVER) {
    SessionManager.getCurrentSession() ->
    SessionManager.isSessionValid() ->
    
    if (sessionValid) {
      // Асинхронное восстановление серверного соединения:
      NetworkManager.connect(savedServerConfig)  // -> CompletableFuture<ConnectionResult>
        .thenRun(() -> {
          DatabaseManager.getAllChats();
          StateManager.setAppState(RESTORED);
          UIBridge.sendEventToUI(StateRestored);
        })
        .exceptionally(throwable -> {
          // Если не удалось восстановить соединение, направляем на переподключение
          StateManager.setAppState(SERVER_CONNECTION);
          UIBridge.sendEventToUI(NavigateToServerConnection);
          return null;
        });
    }
  } else if (appMode == LOCAL) {
    LocalNetworkManager.startDiscovery();
    DatabaseManager.getAllChats();
    StateManager.setAppState(RESTORED);
    UIBridge.sendEventToUI(StateRestored);
  }
```

## 16. Автоматический вход по сохраненной сессии (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Определение стартового экрана (SplashScreen) - determineStartScreen()**

[Остается без изменений, так как эта логика выполняется в StateManager.determineStartScreen() и координируется BackgroundService.scheduleConnectionRestoration()]

---

## Ключевые изменения в архитектуре

### 1. **Четкое разделение ответственности**
- **UIBridge**: Только мост между UI и бизнес-логикой
- **BackgroundService**: Координатор всей бизнес-логики
- **Остальные компоненты**: Сфокусированы на своих специфических задачах

### 2. **Единая точка координации**
- Вся сложная бизнес-логика инкапсулирована в BackgroundService
- Легкость тестирования и отладки
- Повторное использование логики

### 3. **Асинхронная обработка**
- Все сетевые операции возвращают `CompletableFuture`
- Композиция операций через `.thenCompose()`, `.thenAccept()`
- Обязательная обработка ошибок через `.exceptionally()`

### 4. **Улучшенная обработка ошибок**
- Унифицированная обработка через `AppError`
- UI получает конкретные ошибки и может реагировать соответственно
- Graceful degradation для критических операций

### 5. **Чистая цепочка вызовов**
```
Flutter UI -> UIBridge -> BackgroundService -> [Business Components]
     ↑                                              ↓
     ←-------- UI Events ←-------- Results ←-------
```

## 13. Смена темы интерфейса (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Переключение темной/светлой темы (SettingsScreen)**
```
UIBridge.handleUIMethod("changeTheme") ->
  SharedPreferencesManager.setThemeMode(newTheme) ->
  StateManager.notifyStateChange(new StateChange("THEME_CHANGED", oldTheme, newTheme)) ->
  UIBridge.sendEventToUI(ThemeUpdated)
```

## 14. Изменение алгоритмов шифрования (БЕЗ ИЗМЕНЕНИЙ)

**Сценарий: Пользователь меняет алгоритмы (EncryptionSettingsScreen - local mode)**
```
UIBridge.handleUIMethod("updateCryptoAlgorithms") ->
  CryptoManager.validateCryptoAlgorithms(newAlgorithms) ->
  SharedPreferencesManager.setCryptoAlgorithms(newAlgorithms) ->
  CryptoManager.resetStatistics() ->
  DatabaseManager.getAllChats() ->
  StateManager.notifyStateChange(new StateChange("CRYPTO_ALGORITHMS_CHANGED", oldAlgorithms, newAlgorithms)) ->
  UIBridge.sendEventToUI(AlgorithmsUpdated)
```

## 15. Восстановление после сбоя (ОБНОВЛЕНО)

**Сценарий: Восстановление состояния при перезапуске**
```
BackgroundService.initialize() ->
  StateManager.restoreState() ->
  SharedPreferencesManager.getAppMode() ->
  
  if (appMode == SERVER) {
    SessionManager.getCurrentSession() ->
    SessionManager.isSessionValid() ->
    
    if (sessionValid) {
      // Асинхронное восстановление серверного соединения:
      NetworkManager.connect(savedServerConfig)  // -> CompletableFuture<ConnectionResult>
        .thenRun(() -> {
          DatabaseManager.getAllChats();
          StateManager.setAppState(RESTORED);
          UIBridge.sendEventToUI(StateRestored);
        })
        .exceptionally(throwable -> {
          // Если не удалось восстановить соединение, направляем на переподключение
          StateManager.setAppState(SERVER_CONNECTION);
          UIBridge.sendEventToUI(NavigateToServerConnection);
          return null;
        });
    }
  } else if (appMode == LOCAL) {
    LocalNetworkManager.startDiscovery();
    DatabaseManager.getAllChats();
    StateManager.setAppState(RESTORED);
    UIBridge.sendEventToUI(StateRestored);
  }
```

## 16. Автоматический вход по сохраненной сессии (ДЕТАЛЬНЫЙ АНАЛИЗ)

**Сценарий: Определение стартового экрана (SplashScreen) - determineStartScreen()**

Этот сценарий является самым сложным, так как он определяет весь пользовательский опыт при запуске приложения.

### Полная логика determineStartScreen():

```java
// В StateManager.determineStartScreen()
public AppScreen determineStartScreen() {
    
    // 1. Проверка первичной настройки
    boolean firstSetupCompleted = SharedPreferencesManager.getInstance().isFirstSetupCompleted();
    if (!firstSetupCompleted) {
        return AppScreen.MODE_SELECTION;
    }
    
    // 2. Получение режима работы
    AppMode appMode = SharedPreferencesManager.getInstance().getAppMode();
    if (appMode == null) {
        return AppScreen.MODE_SELECTION;
    }
    
    // 3. Логика для серверного режима
    if (appMode == AppMode.SERVER) {
        return determineServerModeStartScreen();
    }
    
    // 4. Логика для локального режима  
    if (appMode == AppMode.LOCAL) {
        return determineLocalModeStartScreen();
    }
    
    // 5. Fallback
    return AppScreen.MODE_SELECTION;
}

private AppScreen determineServerModeStartScreen() {
    // Проверяем конфигурацию сервера
    ServerConfig serverConfig = SharedPreferencesManager.getInstance().getServerConfig();
    if (serverConfig == null) {
        return AppScreen.SERVER_CONNECTION;
    }
    
    // Проверяем сессию
    UserSession session = SharedPreferencesManager.getInstance().getUserSession();
    if (session == null) {
        return AppScreen.AUTHENTICATION;
    }
    
    // Проверяем валидность сессии
    boolean sessionValid = SessionManager.getInstance().isSessionValid();
    if (!sessionValid) {
        // Токен истек, нужна повторная авторизация
        SharedPreferencesManager.getInstance().clearUserSession();
        return AppScreen.AUTHENTICATION;
    }
    
    // Сессия валидна - попытаемся восстановить соединение асинхронно
    // Но сначала возвращаем экран чатов, а восстановление идет в фоне
    scheduleConnectionRestoration();
    return AppScreen.CHAT_LIST;
}

private AppScreen determineLocalModeStartScreen() {
    // Проверяем наличие локального пользователя
    String localUsername = SharedPreferencesManager.getInstance().getLocalUsername();
    if (localUsername == null || localUsername.trim().isEmpty()) {
        return AppScreen.AUTHENTICATION;
    }
    
    // Пользователь есть - сразу в чаты
    return AppScreen.CHAT_LIST;
}
```

### Полная цепочка автоматического входа по сессии:

```
BackgroundService.initialize() ->
  [Инициализация всех компонентов] ->
  StateManager.determineStartScreen() ->
  
  // Проверка первичной настройки:
  SharedPreferencesManager.isFirstSetupCompleted() ->
  
  // Если настройка завершена:
  SharedPreferencesManager.getAppMode() ->
  
  // Для серверного режима:
  if (appMode == SERVER) {
    SharedPreferencesManager.getServerConfig() ->
    
    if (serverConfig != null) {
      SharedPreferencesManager.getUserSession() ->
      
      if (session != null) {
        SessionManager.isSessionValid() ->
        
        if (sessionValid) {
          // АВТОМАТИЧЕСКИЙ ВХОД - асинхронное восстановление соединения:
          BackgroundService.scheduleConnectionRestoration() ->
          
          ServerNetworkManager.connect(serverConfig)  // -> CompletableFuture<ConnectionResult>
            .thenCompose(connectionResult -> {
              if (connectionResult.isSuccess()) {
                StateManager.setConnectionState(CONNECTED);
                
                // Попытка обновить токен если нужно:
                return SessionManager.refreshSession() 
                  ? CompletableFuture.completedFuture(true)
                  : CompletableFuture.failedFuture(new TokenRefreshException());
              } else {
                throw new ConnectionException(connectionResult.getMessage());
              }
            })
            .thenRun(() -> {
              // Успешное восстановление:
              StateManager.setAppState(AUTHENTICATED);
              UIBridge.onConnectionStateChanged(CONNECTED);
              
              // Загружаем данные:
              DatabaseManager.getAllChats();
              
              // Уведомляем UI о успешном восстановлении сессии:
              UIBridge.sendEventToUI(new UIEvent("SESSION_RESTORED", 
                Map.of("success", true, "message", "Автоматический вход выполнен")));
            })
            .exceptionally(throwable -> {
              // Ошибка восстановления соединения:
              StateManager.setConnectionState(ERROR);
              SharedPreferencesManager.clearUserSession();  // Очищаем невалидную сессию
              
              UIBridge.sendEventToUI(new UIEvent("SESSION_RESTORATION_FAILED", 
                Map.of("error", throwable.getMessage(), "action", "NAVIGATE_TO_AUTH")));
              
              return null;
            });
          
          // Сразу возвращаем CHAT_LIST, восстановление идет в фоне:
          return AppScreen.CHAT_LIST;
          
        } else {
          // Сессия невалидна:
          SharedPreferencesManager.clearUserSession();
          return AppScreen.AUTHENTICATION;
        }
      } else {
        // Нет сохраненной сессии:
        return AppScreen.AUTHENTICATION;
      }
    } else {
      // Нет конфигурации сервера:
      return AppScreen.SERVER_CONNECTION;
    }
  }
  
  // Для локального режима:
  else if (appMode == LOCAL) {
    SharedPreferencesManager.getLocalUsername() ->
    
    if (localUsername != null) {
      // Запускаем обнаружение устройств в фоне:
      LocalNetworkManager.startDiscovery();
      LocalNetworkManager.broadcastPresence(userProfile);
      
      StateManager.setAppState(AUTHENTICATED);
      return AppScreen.CHAT_LIST;
    } else {
      return AppScreen.AUTHENTICATION;
    }
  }
  
  // Fallback:
  else {
    return AppScreen.MODE_SELECTION;
  }
  
  // Финальная навигация:
  -> UIBridge.sendEventToUI(NavigateToScreen(determinedScreen))
```

### Особенности восстановления сессии:

1. **Немедленная навигация**: Пользователь сразу попадает в чаты, соединение восстанавливается в фоне
2. **Graceful degradation**: Если восстановление не удалось, пользователь перенаправляется на авторизацию
3. **Обновление токена**: Автоматическое обновление токена через `refreshSession()`
4. **Очистка невалидных данных**: Автоматическая очистка истекших сессий

### Возможные состояния UI во время восстановления:

```
1. SplashScreen -> CHAT_LIST (сессия валидна)
   ├─ Показать индикатор "Восстановление соединения..."
   ├─ При успехе: скрыть индикатор, показать актуальные данные
   └─ При ошибке: показать уведомление + переход на AUTH

2. SplashScreen -> AUTHENTICATION (сессия невалидна)

3. SplashScreen -> SERVER_CONNECTION (нет конфигурации сервера)

4. SplashScreen -> MODE_SELECTION (первый запуск)
```

## Дополнительные методы в StateManager:

```java
// В StateManager нужно добавить:
private void scheduleConnectionRestoration() {
    // Запуск асинхронного восстановления соединения
}

public boolean shouldShowConnectionProgress() {
    // Определяет, нужно ли показывать индикатор восстановления соединения
}

public boolean isRestoringConnection() {
    // Проверяет, идет ли процесс восстановления
}
```

### 1. **Асинхронная обработка**
- Все сетевые операции теперь возвращают `CompletableFuture`
- Используется композиция через `.thenCompose()`, `.thenAccept()`, `.thenRun()`
- Обязательная обработка ошибок через `.exceptionally()`

### 2. **Обработка ошибок**
- Каждая асинхронная цепочка должна иметь обработчик ошибок
- Используется класс `AppError` для унифицированного представления ошибок
- UI уведомляется об ошибках через `UIBridge.onErrorOccurred()`

### 3. **Прогресс операций**
- Долгие операции (загрузка файлов) сообщают о прогрессе через `UIBridge.onProgressUpdate()`
- Используется класс `Progress` для передачи информации о состоянии

### 4. **Композиция операций**
- Сложные операции разбиваются на последовательность асинхронных шагов
- Возможность отмены операций через `CompletableFuture.cancel()`

### 5. **Типизация параметров**
- Уточнены типы параметров (например, `UserProfile` вместо `userInfo`)
- Добавлены обязательные параметры для методов инициализации