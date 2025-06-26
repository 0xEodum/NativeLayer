/**
 * Расширения класса Chat для поддержки fingerprint и статусов инициализации
 */
public class Chat {
    // Существующие поля...
    private String id;
    private String name;
    private ChatKeys keys;
    private long lastActivity;
    private long createdAt;
    private long updatedAt;
    
    // ✅ НОВЫЕ ПОЛЯ:
    private String fingerprint;
    private String keyEstablishmentStatus = "INITIALIZING";
    private long keyEstablishmentCompletedAt;
    
    // Существующие конструкторы и методы...
    public Chat() {}
    
    public Chat(String id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.keyEstablishmentStatus = "INITIALIZING";
    }
    
    // ===========================
    // НОВЫЕ ГЕТТЕРЫ И СЕТТЕРЫ
    // ===========================
    
    /**
     * Получить fingerprint чата для аутентификации
     * @return fingerprint или null если инициализация не завершена
     */
    public String getFingerprint() {
        return fingerprint;
    }
    
    /**
     * Установить fingerprint чата
     * @param fingerprint Fingerprint завершенной инициализации
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    /**
     * Получить статус инициализации ключей
     * @return "INITIALIZING", "ESTABLISHED", или "FAILED"
     */
    public String getKeyEstablishmentStatus() {
        return keyEstablishmentStatus;
    }
    
    /**
     * Установить статус инициализации ключей
     * @param status Новый статус
     */
    public void setKeyEstablishmentStatus(String status) {
        this.keyEstablishmentStatus = status;
    }
    
    /**
     * Получить время завершения инициализации ключей
     * @return timestamp или 0 если не завершена
     */
    public long getKeyEstablishmentCompletedAt() {
        return keyEstablishmentCompletedAt;
    }
    
    /**
     * Установить время завершения инициализации ключей
     * @param completedAt timestamp завершения
     */
    public void setKeyEstablishmentCompletedAt(long completedAt) {
        this.keyEstablishmentCompletedAt = completedAt;
    }
    
    // ===========================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ===========================
    
    /**
     * Проверить, завершена ли инициализация ключей
     * @return true если ключи установлены и готовы к использованию
     */
    public boolean isKeyEstablishmentComplete() {
        return "ESTABLISHED".equals(keyEstablishmentStatus) && 
               keys != null && 
               keys.isComplete();
    }
    
    /**
     * Проверить, провалилась ли инициализация ключей
     * @return true если инициализация провалилась
     */
    public boolean isKeyEstablishmentFailed() {
        return "FAILED".equals(keyEstablishmentStatus);
    }
    
    /**
     * Проверить, находится ли чат в процессе инициализации
     * @return true если инициализация в процессе
     */
    public boolean isKeyEstablishmentInProgress() {
        return "INITIALIZING".equals(keyEstablishmentStatus);
    }
    
    /**
     * Получить время с момента завершения инициализации
     * @return миллисекунды с завершения, или -1 если не завершена
     */
    public long getTimeSinceEstablishment() {
        if (!isKeyEstablishmentComplete() || keyEstablishmentCompletedAt == 0) {
            return -1;
        }
        return System.currentTimeMillis() - keyEstablishmentCompletedAt;
    }
    
    /**
     * Проверить, готов ли чат для отправки сообщений
     * @return true если можно отправлять зашифрованные сообщения
     */
    public boolean isReadyForMessaging() {
        return isKeyEstablishmentComplete() && 
               keys != null && 
               keys.getSymmetricKey() != null;
    }
    
    /**
     * Получить краткую информацию о статусе для отладки
     * @return строка с информацией о статусе
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(keyEstablishmentStatus);
        
        if (fingerprint != null) {
            sb.append(", Fingerprint: ").append(fingerprint.substring(0, 8)).append("...");
        }
        
        if (keyEstablishmentCompletedAt > 0) {
            long timeSince = getTimeSinceEstablishment();
            if (timeSince >= 0) {
                sb.append(", Established ").append(timeSince / 1000).append("s ago");
            }
        }
        
        return sb.toString();
    }
    
    // ===========================
    // ОБНОВЛЕННЫЕ МЕТОДЫ
    // ===========================
    
    /**
     * Обновленный toString с новыми полями
     */
    @Override
    public String toString() {
        return "Chat{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", keyEstablishmentStatus='" + keyEstablishmentStatus + '\'' +
                ", fingerprint='" + (fingerprint != null ? fingerprint.substring(0, 8) + "..." : "null") + '\'' +
                ", hasKeys=" + (keys != null) +
                ", lastActivity=" + lastActivity +
                '}';
    }
    
    /**
     * Равенство объектов (существующий метод без изменений)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chat chat = (Chat) o;
        return Objects.equals(id, chat.id);
    }
    
    /**
     * Hash code (существующий метод без изменений)
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}