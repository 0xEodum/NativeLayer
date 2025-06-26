/**
 * Find or create chat with proper status initialization
 * ОБНОВЛЕННАЯ ВЕРСИЯ с установкой статуса инициализации
 */
private Chat findOrCreateChat(String chatUuid, String peerUserId) {
    // ✅ ИСПРАВЛЕНИЕ: Используем эффективный поиск
    Chat chat = databaseManager.getChat(chatUuid);
    
    if (chat == null) {
        // Создаем новый чат
        chat = new Chat(chatUuid, "Chat with " + peerUserId);
        chat.setLastActivity(System.currentTimeMillis());
        
        // ✅ ДОБАВЛЕНИЕ: Установка статуса инициализации для нового чата
        chat.setKeyEstablishmentStatus("INITIALIZING");
        
        // Сохраняем новый чат
        databaseManager.saveChat(chat);
        
        Log.d(TAG, "Created new chat: " + chatUuid + " with status INITIALIZING");
    } else {
        // Обновляем активность существующего чата
        chat.setLastActivity(System.currentTimeMillis());
        
        Log.d(TAG, "Found existing chat: " + chatUuid + 
               " with status: " + chat.getKeyEstablishmentStatus());
    }
    
    return chat;
}