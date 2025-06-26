/**
 * Handle chat initialization confirmation
 * ИСПРАВЛЕННАЯ ВЕРСИЯ с правильной обработкой ключей
 */
private void handleChatInitConfirm(String fromUserId, Map<String, Object> messageData) {
    try {
        // Извлечение данных из сообщения
        String chatUuid = (String) messageData.get("chat_uuid");
        String capsuleB64 = (String) messageData.get("kem_capsule");

        // Валидация входных данных
        if (chatUuid == null || capsuleB64 == null) {
            Log.w(TAG, "Invalid chat init confirm data");
            return;
        }

        // ✅ ИСПРАВЛЕНИЕ 1: Эффективный поиск чата
        Chat chat = databaseManager.getChat(chatUuid);
        if (chat == null) {
            Log.w(TAG, "Chat not found for init confirm: " + chatUuid);
            return;
        }

        // Получение текущих ключей чата
        ChatKeys chatKeys = chat.getKeys();
        if (chatKeys == null) {
            Log.w(TAG, "Chat keys missing for chat: " + chatUuid);
            return;
        }

        // Определение алгоритмов шифрования
        CryptoAlgorithms algorithms = preferencesManager.getCryptoAlgorithms();

        // Извлечение секрета из полученной KEM capsule
        byte[] capsuleBytes = Base64.getDecoder().decode(capsuleB64);
        byte[] secretB = cryptoManager.extractSecret(
            capsuleBytes, 
            chatKeys.getPrivateKeySelf(), 
            algorithms.getKemAlgorithm()
        );

        // Извлечение secretA из полученной капсулы (от инициатора A)
        byte[] secretA = cryptoManager.extractSecret(
            capsuleBytes, 
            chatKeys.getPrivateKeySelf(), 
            algorithms.getKemAlgorithm()
        );

        // Получение secretB из временного хранения (создан в handleChatInitRequest)
        byte[] secretB = pendingSecrets.remove(chatUuid);
        if (secretB == null) {
            Log.w(TAG, "No pending secret for chat: " + chatUuid);
            return;
        }

        // ✅ ИСПРАВЛЕНИЕ 2: Сохранение результата completeChatInitialization
        ChatKeys updatedKeys = cryptoManager.completeChatInitialization(
            chatKeys, secretA, secretB);

        // ✅ ДОБАВЛЕНИЕ 3: Генерация fingerprint для аутентификации
        String fingerprint = cryptoManager.generateChatFingerprintFromKeys(updatedKeys);

        // ✅ ДОБАВЛЕНИЕ 4: Создание "чистых" ключей только с симметричным ключом
        ChatKeys cleanedKeys = new ChatKeys(updatedKeys.getAlgorithm());
        cleanedKeys.setSymmetricKey(updatedKeys.getSymmetricKey());

        // Обновление чата с очищенными ключами
        chat.setKeys(cleanedKeys);
        
        // ✅ ДОБАВЛЕНИЕ 5: Обновление статуса инициализации в БД
        databaseManager.updateChatKeyEstablishment(chat.getId(), fingerprint, "ESTABLISHED");
        
        // Сохранение обновленного чата
        databaseManager.saveChat(chat);

        // ✅ ДОБАВЛЕНИЕ 6: Безопасная очистка полных ключей из памяти
        cryptoManager.secureClearChatKeys(updatedKeys);

        // ✅ ДОБАВЛЕНИЕ 7: Уведомление UI о завершении инициализации
        if (uiBridge != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("chatId", chat.getId());
            eventData.put("fingerprint", fingerprint);
            eventData.put("status", "ESTABLISHED");
            uiBridge.sendEventToUI("ChatEstablished", eventData);
        }

        Log.i(TAG, "Chat initialization confirmed successfully. Chat: " + chatUuid + 
               ", Fingerprint: " + fingerprint);

    } catch (IllegalArgumentException e) {
        Log.e(TAG, "Invalid cryptographic parameters in chat init confirm", e);
    } catch (SecurityException e) {
        Log.e(TAG, "Security error during chat initialization confirm", e);
    } catch (Exception e) {
        Log.e(TAG, "Unexpected error in handleChatInitConfirm", e);
    }
}