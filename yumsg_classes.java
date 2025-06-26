// ===========================
// ENUMS AND DATA CLASSES
// ===========================

public enum AppMode {
    SERVER, LOCAL
}

public enum AppState {
    INITIALIZING, MODE_SELECTION, SERVER_CONNECTION, AUTHENTICATION, 
    CHAT_LIST, CHAT_ACTIVE, MODE_SELECTED, AUTHENTICATED, RESTORED
}

public enum ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

public enum UserStatus {
    ONLINE, OFFLINE, AWAY, BUSY
}

public enum MessageStatus {
    SENDING, SENT, DELIVERED, READ
}

public enum EncryptionStatus {
    ACTIVE, INITIALIZING, ERROR
}

public enum AlgorithmType {
    ASYMMETRIC_KEM, SYMMETRIC, DIGITAL_SIGNATURE
}

public enum AppScreen {
    MODE_SELECTION, SERVER_CONNECTION, AUTHENTICATION, CHAT_LIST, CHAT_ACTIVE
}

public enum ThemeMode {
    LIGHT, DARK, SYSTEM
}

public enum FontSize {
    SMALL, MEDIUM, LARGE
}

// ===========================
// CRYPTO DATA CLASSES
// ===========================

public class CryptoAlgorithms {
    private String kemAlgorithm;
    private String symmetricAlgorithm;
    private String signatureAlgorithm;
    
    public CryptoAlgorithms() {}
    public CryptoAlgorithms(String kemAlgorithm, String symmetricAlgorithm, String signatureAlgorithm) {
        this.kemAlgorithm = kemAlgorithm;
        this.symmetricAlgorithm = symmetricAlgorithm;
        this.signatureAlgorithm = signatureAlgorithm;
    }
    
    public String getKemAlgorithm() { return kemAlgorithm; }
    public void setKemAlgorithm(String algorithm) { this.kemAlgorithm = algorithm; }
    public String getSymmetricAlgorithm() { return symmetricAlgorithm; }
    public void setSymmetricAlgorithm(String algorithm) { this.symmetricAlgorithm = algorithm; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String algorithm) { this.signatureAlgorithm = algorithm; }
    public boolean isValid() { /* validation logic */ return true; }
    public boolean equals(CryptoAlgorithms other) { /* comparison logic */ return false; }
}

public class ChatKeys {
    private byte[] publicKeySelf;
    private byte[] privateKeySelf;
    private byte[] publicKeyPeer;
    private byte[] symmetricKey;
    private String algorithm;
    
    public ChatKeys() {}
    public ChatKeys(String algorithm) { this.algorithm = algorithm; }
    
    public byte[] getPublicKeySelf() { return publicKeySelf; }
    public void setPublicKeySelf(byte[] key) { this.publicKeySelf = key; }
    public byte[] getPrivateKeySelf() { return privateKeySelf; }
    public void setPrivateKeySelf(byte[] key) { this.privateKeySelf = key; }
    public byte[] getPublicKeyPeer() { return publicKeyPeer; }
    public void setPublicKeyPeer(byte[] key) { this.publicKeyPeer = key; }
    public byte[] getSymmetricKey() { return symmetricKey; }
    public void setSymmetricKey(byte[] key) { this.symmetricKey = key; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public boolean hasKeyPair() { return publicKeySelf != null && privateKeySelf != null; }
    public boolean hasPeerKey() { return publicKeyPeer != null; }
    public boolean isComplete() { return hasKeyPair() && hasPeerKey() && symmetricKey != null; }
    public void secureWipe() { /* secure memory clearing */ }
}

public class AlgorithmInfo {
    private String name;
    private AlgorithmType type;
    private int keySize;
    private String description;
    private boolean recommended;
    private String securityLevel;
    
    public AlgorithmInfo(String name, AlgorithmType type, int keySize, String description, boolean recommended, String securityLevel) {
        this.name = name;
        this.type = type;
        this.keySize = keySize;
        this.description = description;
        this.recommended = recommended;
        this.securityLevel = securityLevel;
    }
    
    public String getName() { return name; }
    public AlgorithmType getType() { return type; }
    public int getKeySize() { return keySize; }
    public String getDescription() { return description; }
    public boolean isRecommended() { return recommended; }
    public String getSecurityLevel() { return securityLevel; }
}

public class CryptoStatistics {
    private long keyGenerationsCount;
    private long encryptionsCount;
    private long decryptionsCount;
    private long signaturesCount;
    private long verificationsCount;
    private long kemOperationsCount;
    private long totalOperationTime;
    private long createdAt;
    
    public CryptoStatistics() { this.createdAt = System.currentTimeMillis(); }
    
    public long getKeyGenerationsCount() { return keyGenerationsCount; }
    public long getEncryptionsCount() { return encryptionsCount; }
    public long getDecryptionsCount() { return decryptionsCount; }
    public long getSignaturesCount() { return signaturesCount; }
    public long getVerificationsCount() { return verificationsCount; }
    public long getKemOperationsCount() { return kemOperationsCount; }
    public long getTotalOperationTime() { return totalOperationTime; }
    public long getCreatedAt() { return createdAt; }
    public void reset() { /* reset all counters */ }
    public CryptoStatistics copy() { /* create copy */ return new CryptoStatistics(); }
}

public class Organization {
    private String name;
    private byte[] userSignaturePublicKey;
    private byte[] userSignaturePrivateKey;
    private CryptoAlgorithms cryptoAlgorithms;
    
    public Organization(String name) { this.name = name; }
    
    public String getName() { return name; }
    public byte[] getUserSignaturePublicKey() { return userSignaturePublicKey; }
    public void setUserSignaturePublicKey(byte[] key) { this.userSignaturePublicKey = key; }
    public byte[] getUserSignaturePrivateKey() { return userSignaturePrivateKey; }
    public void setUserSignaturePrivateKey(byte[] key) { this.userSignaturePrivateKey = key; }
    public CryptoAlgorithms getCryptoAlgorithms() { return cryptoAlgorithms; }
    public void setCryptoAlgorithms(CryptoAlgorithms algorithms) { this.cryptoAlgorithms = algorithms; }
}

// ===========================
// SHARED PREFERENCES DATA CLASSES
// ===========================

public class ServerConfig {
    private String host;
    private int port;
    private String organizationName;
    
    public ServerConfig(String host, int port, String organizationName) {
        this.host = host;
        this.port = port;
        this.organizationName = organizationName;
    }
    
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getOrganizationName() { return organizationName; }
}

public class UserSession {
    private String username;
    private String token;
    private String organizationId;
    private long timestamp;
    
    public UserSession(String username, String token, String organizationId, long timestamp) {
        this.username = username;
        this.token = token;
        this.organizationId = organizationId;
        this.timestamp = timestamp;
    }
    
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getOrganizationId() { return organizationId; }
    public long getTimestamp() { return timestamp; }
}

public class UserCredentials {
    private String username;
    private String password;
    private String email;
    
    public UserCredentials(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }
    
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
}

// ===========================
// RESULT CLASSES
// ===========================

public class ConnectionResult {
    private boolean success;
    private String message;
    
    public ConnectionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}

public class AuthResult {
    private boolean success;
    private String token;
    private String message;
    
    public AuthResult(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getMessage() { return message; }
}

public class ChatResult {
    private boolean success;
    private Chat chat;
    private String message;
    
    public ChatResult(boolean success, Chat chat, String message) {
        this.success = success;
        this.chat = chat;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public Chat getChat() { return chat; }
    public String getMessage() { return message; }
}

public class UploadResult {
    private boolean success;
    private String fileId;
    private String url;
    private String message;
    
    public UploadResult(boolean success, String fileId, String url, String message) {
        this.success = success;
        this.fileId = fileId;
        this.url = url;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getFileId() { return fileId; }
    public String getUrl() { return url; }
    public String getMessage() { return message; }
}

public class DownloadResult {
    private boolean success;
    private FileInfo fileInfo;
    private String localPath;
    private String message;
    
    public DownloadResult(boolean success, FileInfo fileInfo, String localPath, String message) {
        this.success = success;
        this.fileInfo = fileInfo;
        this.localPath = localPath;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public FileInfo getFileInfo() { return fileInfo; }
    public String getLocalPath() { return localPath; }
    public String getMessage() { return message; }
}

public class TransferResult {
    private boolean success;
    private String transferId;
    private String message;
    
    public TransferResult(boolean success, String transferId, String message) {
        this.success = success;
        this.transferId = transferId;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getTransferId() { return transferId; }
    public String getMessage() { return message; }
}

public class UpdateResult {
    private boolean success;
    private String message;
    
    public UpdateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}


public class EncryptedFileResult {
    private String encryptedFilePath;
    private byte[] hash;
    private long size;
    
    public EncryptedFileResult(String encryptedFilePath, byte[] hash, long size) {
        this.encryptedFilePath = encryptedFilePath;
        this.hash = hash;
        this.size = size;
    }
    
    public String getEncryptedFilePath() { return encryptedFilePath; }
    public byte[] getHash() { return hash; }
    public long getSize() { return size; }
}

// ===========================
// UI AND EVENT CLASSES
// ===========================

public class UIEvent {
    private String type;
    private Map<String, Object> data;
    
    public UIEvent(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }
    
    public String getType() { return type; }
    public Map<String, Object> getData() { return data; }
}

public class NotificationData {
    private String id;
    private String title;
    private String message;
    private String type;
    private Map<String, Object> extras;
    
    public NotificationData(String id, String title, String message, String type) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.extras = new HashMap<>();
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public Map<String, Object> getExtras() { return extras; }
}

public class Progress {
    private String taskId;
    private int percentage;
    private String status;
    private long bytesTransferred;
    private long totalBytes;
    
    public Progress(String taskId, int percentage, String status) {
        this.taskId = taskId;
        this.percentage = percentage;
        this.status = status;
    }
    
    public String getTaskId() { return taskId; }
    public int getPercentage() { return percentage; }
    public String getStatus() { return status; }
    public long getBytesTransferred() { return bytesTransferred; }
    public long getTotalBytes() { return totalBytes; }
    public void setBytesTransferred(long bytes) { this.bytesTransferred = bytes; }
    public void setTotalBytes(long bytes) { this.totalBytes = bytes; }
}

public class ConnectionMetrics {
    private long latency;
    private long bandwidth;
    private int packetLoss;
    private String connectionType;
    private long connectedTime;
    
    public ConnectionMetrics() {
        this.connectedTime = System.currentTimeMillis();
    }
    
    public long getLatency() { return latency; }
    public void setLatency(long latency) { this.latency = latency; }
    public long getBandwidth() { return bandwidth; }
    public void setBandwidth(long bandwidth) { this.bandwidth = bandwidth; }
    public int getPacketLoss() { return packetLoss; }
    public void setPacketLoss(int packetLoss) { this.packetLoss = packetLoss; }
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
    public long getConnectedTime() { return connectedTime; }
}

public class StateChange {
    private String type;
    private Object oldValue;
    private Object newValue;
    private long timestamp;
    
    public StateChange(String type, Object oldValue, Object newValue) {
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getType() { return type; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }
    public long getTimestamp() { return timestamp; }
}

public class AppError {
    private String code;
    private String message;
    private String details;
    private Throwable cause;
    
    public AppError(String code, String message, String details, Throwable cause) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.cause = cause;
    }
    
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
    public Throwable getCause() { return cause; }
}

public class Peer {
    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private UserStatus status;
    private long lastSeen;
    
    public Peer(String id, String name, String ipAddress, int port) {
        this.id = id;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public long getLastSeen() { return lastSeen; }
    public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }
}

public class AuthData {
    private String username;
    private String token;
    private String organizationId;
    private String userId;
    private long expiresAt;
    private Map<String, Object> metadata;
    
    public AuthData(String username, String token, String organizationId, String userId, long expiresAt) {
        this.username = username;
        this.token = token;
        this.organizationId = organizationId;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.metadata = new HashMap<>();
    }
    
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public long getExpiresAt() { return expiresAt; }
    public Map<String, Object> getMetadata() { return metadata; }
}

public class UserRegistrationInfo {
    private String username;
    private String email;
    private String password;
    private String displayName;
    private String organizationCode;
    
    public UserRegistrationInfo(String username, String email, String password, String displayName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }
    
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getDisplayName() { return displayName; }
    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }
}

public class ChatInfo {
    private String id;
    private String name;
    private List<String> participants;
    private EncryptionStatus encryptionStatus;
    private long createdAt;
    private long lastActivity;
    
    public ChatInfo(String id, String name) {
        this.id = id;
        this.name = name;
        this.participants = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getParticipants() { return participants; }
    public EncryptionStatus getEncryptionStatus() { return encryptionStatus; }
    public void setEncryptionStatus(EncryptionStatus status) { this.encryptionStatus = status; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }
}

public class OrganizationInfo {
    private String name;
    private String id;
    private CryptoAlgorithms supportedAlgorithms;
    private String serverVersion;
    private Map<String, Object> policies;
    
    public OrganizationInfo(String name, String id) {
        this.name = name;
        this.id = id;
        this.policies = new HashMap<>();
    }
    
    public String getName() { return name; }
    public String getId() { return id; }
    public CryptoAlgorithms getSupportedAlgorithms() { return supportedAlgorithms; }
    public void setSupportedAlgorithms(CryptoAlgorithms algorithms) { this.supportedAlgorithms = algorithms; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String version) { this.serverVersion = version; }
    public Map<String, Object> getPolicies() { return policies; }
}

// ===========================
// PERMISSION AND PLATFORM CLASSES
// ===========================

public enum Permission {
    CAMERA, MICROPHONE, STORAGE, LOCATION, NOTIFICATIONS, CONTACTS
}

public enum SoundType {
    MESSAGE_RECEIVED, MESSAGE_SENT, NOTIFICATION, ERROR, SUCCESS
}

public class VibrationPattern {
    private long[] pattern;
    private boolean repeat;
    
    public VibrationPattern(long[] pattern, boolean repeat) {
        this.pattern = pattern;
        this.repeat = repeat;
    }
    
    public long[] getPattern() { return pattern; }
    public boolean isRepeat() { return repeat; }
}

public class FileData {
    private String path;
    private String name;
    private String mimeType;
    private long size;
    private byte[] data;
    
    public FileData(String path, String name, String mimeType, long size) {
        this.path = path;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
    }
    
    public String getPath() { return path; }
    public String getName() { return name; }
    public String getMimeType() { return mimeType; }
    public long getSize() { return size; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
}

public class Session {
    private String id;
    private String userId;
    private String token;
    private long createdAt;
    private long expiresAt;
    private boolean isValid;
    
    public Session(String id, String userId, String token, long expiresAt) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = expiresAt;
        this.isValid = true;
    }
    
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isValid() { return isValid && System.currentTimeMillis() < expiresAt; }
    public void invalidate() { this.isValid = false; }
}

public class EncryptedFileResult {
    private String encryptedFilePath;
    private byte[] hash;
    private long size;
    
    public EncryptedFileResult(String encryptedFilePath, byte[] hash, long size) {
        this.encryptedFilePath = encryptedFilePath;
        this.hash = hash;
        this.size = size;
    }
    
    public String getEncryptedFilePath() { return encryptedFilePath; }
    public byte[] getHash() { return hash; }
    public long getSize() { return size; }
}

// ===========================
// CORE DATA CLASSES
// ===========================

public class Message {
    private long id;
    private String chatId;
    private String content;
    private MessageStatus status;
    private long timestamp;
    
    // Constructor and getters/setters
    public Message() {}
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

public class Chat {
    private String id;
    private String name;
    private ChatKeys keys;
    private long lastActivity;
    
    public Chat() {}
    public Chat(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ChatKeys getKeys() { return keys; }
    public void setKeys(ChatKeys keys) { this.keys = keys; }
    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }
}

public class User {
    private String id;
    private String username;
    private UserStatus status;
    
    public User() {}
    public User(String id, String username) {
        this.id = id;
        this.username = username;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}

public class Contact {
    private String id;
    private String name;
    private String publicKey;
    
    public Contact() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}

public class FileInfo {
    private String id;
    private String name;
    private long size;
    private String mimeType;
    private String path;
    
    public FileInfo() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}

public class UserProfile {
    private String id;
    private String username;
    private String email;
    private String displayName;
    
    public UserProfile() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}

// ===========================
// INTERFACES
// ===========================

public interface StateObserver {
    void onStateChanged(AppState newState);
    void onConnectionStateChanged(ConnectionState newState);
}


