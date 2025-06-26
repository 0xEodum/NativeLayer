/**
 * Handle chat initialization request
 * ОБНОВЛЕННАЯ ВЕРСИЯ с установкой правильного статуса инициализации
 */
private void handleChatInitRequest(String fromUserId, Map<String, Object> messageData) {
    try {
        // Извлечение данных из сообщения
        String chatUuid = (String) messageData.get("chat_uuid");
        String peerPublicKeyB64 = (String) messageData.get("public_key");
        @SuppressWarnings("unchecked")
        Map<String, String> algMap = (Map<String, String>) messageData.get("crypto_algorithms");

        // Валидация входных данных
        if (chatUuid == null || peerPublicKeyB64 == null) {
            Log.w(TAG, "Invalid chat init request data");
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

        // Декодирование публичного ключа собеседника
        byte[] peerPublicKeyBytes = Base64.getDecoder().decode(peerPublicKeyB64);
        AsymmetricKeyParameter peerKeyParam = cryptoManager.bytesToKemPublicKey(
            peerPublicKeyBytes, algorithms.getKemAlgorithm());
        byte[] peerPublicKey = cryptoManager.getPublicKeyBytes(
            peerKeyParam, algorithms.getKemAlgorithm());

        // Создание или поиск чата
        Chat chat = findOrCreateChat(chatUuid, fromUserId);

        // ✅ ДОБАВЛЕНИЕ: Установка статуса инициализации для нового чата
        if (chat.getKeyEstablishmentStatus() == null || 
            chat.getKeyEstablishmentStatus().isEmpty()) {
            chat.setKeyEstablishmentStatus("INITIALIZING");
        }

        // Создание ключей чата для ответа
        ChatKeys chatKeys = cryptoManager.createChatKeys(algorithms.getKemAlgorithm());

        // Обновление ключей собеседником
        chatKeys.setPublicKeyPeer(peerPublicKey);

        // Сохранение алгоритмов собеседника
        PeerCryptoInfo peerInfo = chat.getPeerCryptoInfo();
        if (peerInfo == null) {
            peerInfo = new PeerCryptoInfo();
            peerInfo.setPeerId(fromUserId);
        }
        peerInfo.setPeerAlgorithms(algorithms);
        chat.setPeerCryptoInfo(peerInfo);

        // Выполнение KEM инкапсуляции
        SecretWithEncapsulation kemResult = cryptoManager.encapsulateSecret(
            peerPublicKey, algorithms.getKemAlgorithm());
        
        // ✅ ВАЖНО: Сохранение секрета до получения подтверждения
        pendingSecrets.put(chatUuid, kemResult.getSecret());
        
        // Сохранение чата с ключами
        chat.setKeys(chatKeys);
        databaseManager.saveChat(chat);
        
        // Отправка ответа с нашим публичным ключом и капсулой
        networkManager.sendChatInitResponse(fromUserId, chatUuid,
            chatKeys.getPublicKeySelf(), kemResult.getEncapsulation(), null);
        
        Log.i(TAG, "Chat init request processed, response sent. Chat: " + chatUuid);

    } catch (IllegalArgumentException e) {
        Log.e(TAG, "Invalid cryptographic parameters in chat init request", e);
    } catch (Exception e) {
        Log.e(TAG, "Unexpected error in handleChatInitRequest", e);
    }
}