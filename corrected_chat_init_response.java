/**
 * Handle incoming chat initialization response
 * ИСПРАВЛЕННАЯ ВЕРСИЯ с правильной обработкой ключей
 */
private void handleChatInitResponse(String fromUserId, Map<String, Object> messageData) {
    try {
        // Извлечение данных из сообщения
        String chatUuid = (String) messageData.get("chat_uuid");
        String peerPublicKeyB64 = (String) messageData.get("public_key");
        String capsuleB64 = (String) messageData.get("kem_capsule");
        @SuppressWarnings("unchecked")
        Map<String, String> algMap = (Map<String, String>) messageData.get("crypto_algorithms");

        // Валидация входных данных
        if (chatUuid == null || peerPublicKeyB64 == null || capsuleB64 == null) {
            Log.w(TAG, "Invalid chat init response data");
            return;
        }

        // ✅ ИСПРАВЛЕНИЕ 1: Эффективный поиск чата
        Chat chat = databaseManager.getChat(chatUuid);
        if (chat == null) {
            Log.w(TAG, "Chat not found for init response: " + chatUuid);
            return;
        }

        // Определение алгоритмов шифрования
        CryptoAlgorithms algorithms;
        if (algMap != null) {
            algorithms = new CryptoAlgorithms(
                    algMap.get("asymmetric"),
                    algMap.get("symmetric"),
                    algMap.get("signature"));
        } else {
            algorithms = preferencesManager.getCryptoAlgorithms();
        }

        // Получение текущих ключей чата
        ChatKeys chatKeys = chat.getKeys();
        if (chatKeys == null) {
            Log.w(TAG, "Chat keys missing for chat: " + chatUuid);
            return;
        }

        // Обработка публичного ключа собеседника
        byte[] peerKeyBytes = Base64.getDecoder().decode(peerPublicKeyB64);
        AsymmetricKeyParameter peerKeyParam = cryptoManager.bytesToKemPublicKey(
            peerKeyBytes, algorithms.getKemAlgorithm());
        byte[] peerPublicKey = cryptoManager.getPublicKeyBytes(
            peerKeyParam, algorithms.getKemAlgorithm());
        chatKeys.setPublicKeyPeer(peerPublicKey);

        // Извлечение секрета собеседника из KEM capsule
        byte[] capsuleBytes = Base64.getDecoder().decode(capsuleB64);
        byte[] secretB = cryptoManager.extractSecret(
            capsuleBytes, 
            chatKeys.getPrivateKeySelf(), 
            algorithms.getKemAlgorithm()
        );

        // Создание собственного секрета для собеседника
        SecretWithEncapsulation kemResult = cryptoManager.encapsulateSecret(
            peerPublicKey, algorithms.getKemAlgorithm());
        byte[] secretA = kemResult.getSecret();
        byte[] capsuleA = kemResult.getEncapsulation();

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

        // Отправка подтверждения инициализации
        networkManager.sendChatInitConfirm(fromUserId, chatUuid, capsuleA);

        // ✅ ДОБАВЛЕНИЕ 7: Уведомление UI о завершении инициализации
        if (uiBridge != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("chatId", chat.getId());
            eventData.put("fingerprint", fingerprint);
            eventData.put("status", "ESTABLISHED");
            uiBridge.sendEventToUI("ChatEstablished", eventData);
        }

        Log.i(TAG, "Chat initialization completed successfully. Chat: " + chatUuid + 
               ", Fingerprint: " + fingerprint);

    } catch (IllegalArgumentException e) {
        Log.e(TAG, "Invalid cryptographic parameters in chat init response", e);
    } catch (SecurityException e) {
        Log.e(TAG, "Security error during chat initialization", e);
    } catch (Exception e) {
        Log.e(TAG, "Unexpected error in handleChatInitResponse", e);
    }
}