/**
 * Расширения DatabaseManager для поддержки fingerprint и статусов инициализации ключей
 */

// ===========================
// ДОПОЛНИТЕЛЬНЫЕ КОНСТАНТЫ
// ===========================

// Добавить в DatabaseManager:
private static final String COLUMN_CHAT_FINGERPRINT = "chat_fingerprint";
private static final String COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS = "key_establishment_status";
private static final String COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT = "key_establishment_completed_at";

// Обновленный CREATE_CHATS_TABLE (добавить новые колонки):
private static final String CREATE_CHATS_TABLE = 
    "CREATE TABLE " + TABLE_CHATS + " (" +
    COLUMN_ID + " TEXT PRIMARY KEY, " +
    COLUMN_CHAT_NAME + " TEXT, " +
    COLUMN_CHAT_KEYS + " TEXT, " +
    COLUMN_CHAT_LAST_ACTIVITY + " INTEGER, " +
    COLUMN_CHAT_CREATED_AT + " INTEGER, " +
    COLUMN_CHAT_UPDATED_AT + " INTEGER, " +
    // ✅ НОВЫЕ КОЛОНКИ:
    COLUMN_CHAT_FINGERPRINT + " TEXT, " +
    COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " TEXT DEFAULT 'INITIALIZING', " +
    COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT + " INTEGER" +
    ");";

// ===========================
// НОВЫЕ МЕТОДЫ
// ===========================

/**
 * Обновление статуса инициализации ключей чата
 * @param chatId ID чата
 * @param fingerprint Fingerprint завершенной инициализации
 * @param status Статус ('INITIALIZING', 'ESTABLISHED', 'FAILED')
 * @return true если обновление успешно
 */
public boolean updateChatKeyEstablishment(String chatId, String fingerprint, String status) {
    if (chatId == null || status == null) {
        Log.w(TAG, "Cannot update chat key establishment with null parameters");
        return false;
    }
    
    lock.writeLock().lock();
    try {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CHAT_FINGERPRINT, fingerprint);
        values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS, status);
        values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT, System.currentTimeMillis());
        values.put(COLUMN_CHAT_UPDATED_AT, System.currentTimeMillis());
        
        String whereClause = COLUMN_ID + " = ?";
        String[] whereArgs = {chatId};
        
        int updatedRows = database.update(TABLE_CHATS, values, whereClause, whereArgs);
        
        if (updatedRows > 0) {
            Log.d(TAG, "Updated chat key establishment status: " + chatId + " -> " + status);
            return true;
        } else {
            Log.w(TAG, "Failed to update chat key establishment status for: " + chatId);
            return false;
        }
        
    } catch (SQLiteException e) {
        Log.e(TAG, "Error updating chat key establishment", e);
        return false;
    } finally {
        lock.writeLock().unlock();
    }
}

/**
 * Получение чатов по статусу инициализации ключей
 * @param status Статус для поиска
 * @return Список чатов с указанным статусом
 */
public List<Chat> getChatsByKeyEstablishmentStatus(String status) {
    List<Chat> chats = new ArrayList<>();
    
    if (status == null) {
        Log.w(TAG, "Cannot search chats with null status");
        return chats;
    }
    
    lock.readLock().lock();
    try {
        String selection = COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " = ?";
        String[] selectionArgs = {status};
        String orderBy = COLUMN_CHAT_LAST_ACTIVITY + " DESC";
        
        Cursor cursor = database.query(
            TABLE_CHATS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        );
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Chat chat = chatFromCursor(cursor);
                if (chat != null) {
                    chats.add(chat);
                }
            }
            cursor.close();
        }
        
        Log.d(TAG, "Retrieved " + chats.size() + " chats with status: " + status);
        
    } catch (SQLiteException e) {
        Log.e(TAG, "Error retrieving chats by establishment status", e);
    } finally {
        lock.readLock().unlock();
    }
    
    return chats;
}

/**
 * Получение fingerprint чата
 * @param chatId ID чата
 * @return Fingerprint или null если не найден
 */
public String getChatFingerprint(String chatId) {
    if (chatId == null) {
        return null;
    }
    
    lock.readLock().lock();
    try {
        String[] columns = {COLUMN_CHAT_FINGERPRINT};
        String selection = COLUMN_ID + " = ?";
        String[] selectionArgs = {chatId};
        
        Cursor cursor = database.query(
            TABLE_CHATS,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            null
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int fingerprintIndex = cursor.getColumnIndex(COLUMN_CHAT_FINGERPRINT);
            String fingerprint = cursor.getString(fingerprintIndex);
            cursor.close();
            return fingerprint;
        }
        
        if (cursor != null) {
            cursor.close();
        }
        
        return null;
        
    } catch (SQLiteException e) {
        Log.e(TAG, "Error retrieving chat fingerprint", e);
        return null;
    } finally {
        lock.readLock().unlock();
    }
}

/**
 * Обновленный метод chatFromCursor с поддержкой новых полей
 */
private Chat chatFromCursor(Cursor cursor) {
    try {
        int idIndex = cursor.getColumnIndex(COLUMN_ID);
        int nameIndex = cursor.getColumnIndex(COLUMN_CHAT_NAME);
        int keysIndex = cursor.getColumnIndex(COLUMN_CHAT_KEYS);
        int lastActivityIndex = cursor.getColumnIndex(COLUMN_CHAT_LAST_ACTIVITY);
        int createdAtIndex = cursor.getColumnIndex(COLUMN_CHAT_CREATED_AT);
        int updatedAtIndex = cursor.getColumnIndex(COLUMN_CHAT_UPDATED_AT);
        
        // ✅ НОВЫЕ ИНДЕКСЫ:
        int fingerprintIndex = cursor.getColumnIndex(COLUMN_CHAT_FINGERPRINT);
        int statusIndex = cursor.getColumnIndex(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS);
        int completedAtIndex = cursor.getColumnIndex(COLUMN_CHAT_KEY_ESTABLISHMENT_COMPLETED_AT);
        
        Chat chat = new Chat();
        chat.setId(cursor.getString(idIndex));
        chat.setName(cursor.getString(nameIndex));
        chat.setLastActivity(cursor.getLong(lastActivityIndex));
        chat.setCreatedAt(cursor.getLong(createdAtIndex));
        chat.setUpdatedAt(cursor.getLong(updatedAtIndex));
        
        // ✅ НОВЫЕ ПОЛЯ:
        if (fingerprintIndex >= 0) {
            chat.setFingerprint(cursor.getString(fingerprintIndex));
        }
        if (statusIndex >= 0) {
            chat.setKeyEstablishmentStatus(cursor.getString(statusIndex));
        }
        if (completedAtIndex >= 0) {
            chat.setKeyEstablishmentCompletedAt(cursor.getLong(completedAtIndex));
        }
        
        // Десериализация ключей
        String keysJson = cursor.getString(keysIndex);
        if (keysJson != null && !keysJson.isEmpty()) {
            try {
                ChatKeys keys = gson.fromJson(keysJson, ChatKeys.class);
                chat.setKeys(keys);
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Error parsing chat keys JSON", e);
                // Возвращаем чат без ключей, но с остальными данными
            }
        }
        
        return chat;
        
    } catch (Exception e) {
        Log.e(TAG, "Error creating chat from cursor", e);
        return null;
    }
}

/**
 * Cleanup метод для зависших инициализаций
 * Вызывается при старте приложения
 */
public void cleanupStaleInitializations(long maxAgeMs) {
    lock.writeLock().lock();
    try {
        long cutoffTime = System.currentTimeMillis() - maxAgeMs;
        
        ContentValues values = new ContentValues();
        values.put(COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS, "FAILED");
        values.put(COLUMN_CHAT_UPDATED_AT, System.currentTimeMillis());
        
        String whereClause = COLUMN_CHAT_KEY_ESTABLISHMENT_STATUS + " = ? AND " +
                           COLUMN_CHAT_CREATED_AT + " < ?";
        String[] whereArgs = {"INITIALIZING", String.valueOf(cutoffTime)};
        
        int updatedRows = database.update(TABLE_CHATS, values, whereClause, whereArgs);
        
        if (updatedRows > 0) {
            Log.i(TAG, "Cleaned up " + updatedRows + " stale chat initializations");
        }
        
    } catch (SQLiteException e) {
        Log.e(TAG, "Error during stale initialization cleanup", e);
    } finally {
        lock.writeLock().unlock();
    }
}