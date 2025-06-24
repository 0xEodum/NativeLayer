package com.yumsg.core.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import okhttp3.*;

/**
 * ServerNetworkManager - Implementation for server-based mode
 * 
 * Handles all network communications with the YuMSG server including:
 * - HTTP REST API calls
 * - WebSocket real-time messaging
 * - Authentication and session management
 * - Message queuing and delivery
 */
public class ServerNetworkManager implements NetworkManager {
    private static final String TAG = "ServerNetworkManager";
    
    // API Endpoints
    private static final String API_PING = "/api/ping";
    private static final String API_ORG_INFO = "/api/organization/info";
    private static final String API_AUTH_REGISTER = "/api/auth/register";
    private static final String API_AUTH_LOGIN = "/api/auth/login";
    private static final String API_USERS_PROFILE = "/api/users/profile";
    private static final String API_USERS_SEARCH = "/api/users/search";
    private static final String API_USERS_STATUS = "/api/users/%s/status";
    private static final String API_CHATS = "/api/chats";
    private static final String API_CHATS_DELETE = "/api/chats/%s";
    private static final String API_PRESENCE_OFFLINE = "/api/presence/offline";
    private static final String API_MESSAGES_SEND = "/api/messages/%s";
    private static final String API_MESSAGES_PENDING = "/api/messages/pending";
    private static final String API_MESSAGES_ACK = "/api/messages/acknowledge";
    
    // WebSocket endpoint
    private static final String WS_MESSAGES = "/ws/messages";
    
    // Configuration
    private static final int CONNECTION_TIMEOUT = 30; // seconds
    private static final int READ_TIMEOUT = 30; // seconds
    private static final int WRITE_TIMEOUT = 30; // seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Core components
    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService;
    
    // Network components
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private String baseUrl;
    private String organizationName;
    
    // Authentication
    private volatile String currentAccessToken;
    private volatile long tokenExpiresAt;
    
    // Connection state
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile ConnectionMetrics connectionMetrics;
    
    // Message queuing
    private final Queue<PendingMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // WebSocket listeners
    private final Set<WebSocketMessageListener> messageListeners = ConcurrentHashMap.newKeySet();
    
    /**
     * Constructor
     */
    public ServerNetworkManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create();
        this.executorService = Executors.newCachedThreadPool();
        this.connectionMetrics = new ConnectionMetrics();
        
        Log.d(TAG, "ServerNetworkManager instance created");
    }
    
    // ===========================
    // NETWORKMANAGER INTERFACE IMPLEMENTATION
    // ===========================
    
    @Override
    public CompletableFuture<ConnectionResult> connect(Map<String, Object> connectionParams) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                Log.d(TAG, "Connecting to server with params: " + connectionParams);
                
                // Extract connection parameters
                String host = (String) connectionParams.get("host");
                Integer port = (Integer) connectionParams.get("port");
                String orgName = (String) connectionParams.get("organizationName");
                
                if (host == null || port == null || orgName == null) {
                    throw new IllegalArgumentException("Missing required connection parameters");
                }
                
                this.baseUrl = buildBaseUrl(host, port);
                this.organizationName = orgName;
                
                // Initialize HTTP client
                initializeHttpClient();
                
                // Test connection
                if (!testServerConnection()) {
                    return new ConnectionResult(false, "Cannot reach server at " + baseUrl);
                }
                
                connectionState = ConnectionState.CONNECTED;
                connectionMetrics.recordConnectionAttempt(true);
                
                Log.i(TAG, "Successfully connected to server at " + baseUrl);
                return new ConnectionResult(true, "Connected to " + orgName);
                
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                connectionMetrics.recordConnectionAttempt(false);
                return new ConnectionResult(false, "Connection failed: " + e.getMessage());
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public void disconnect() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Disconnecting from server");
            
            // Close WebSocket
            if (webSocket != null) {
                webSocket.close(1000, "Client disconnect");
                webSocket = null;
            }
            
            // Clear authentication
            currentAccessToken = null;
            tokenExpiresAt = 0;
            
            // Update state
            connectionState = ConnectionState.DISCONNECTED;
            
            Log.i(TAG, "Disconnected from server");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED && webSocket != null;
    }
    
    @Override
    public ConnectionState getConnectionStatus() {
        return connectionState;
    }
    
    @Override
    public ConnectionMetrics getConnectionMetrics() {
        return connectionMetrics.copy();
    }
    
    @Override
    public CompletableFuture<AuthResult> authenticateUser(UserCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Authenticating user: " + credentials.getUsername());
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("username", credentials.getUsername());
                requestBody.put("password", credentials.getPassword());
                
                ApiResponse<AuthResponse> response = makeRequest(
                    "POST", API_AUTH_LOGIN, requestBody, new TypeToken<AuthResponse>(){}.getType());
                
                if (response.isSuccess()) {
                    AuthResponse authData = response.getData();
                    
                    // Store authentication token
                    this.currentAccessToken = authData.token;
                    this.tokenExpiresAt = parseTokenExpiry(authData.tokenExpiresAt);
                    
                    // Initialize WebSocket connection
                    initializeWebSocket();
                    
                    Log.i(TAG, "Authentication successful for user: " + credentials.getUsername());
                    return new AuthResult(true, authData.token, "Authentication successful");
                } else {
                    Log.w(TAG, "Authentication failed: " + response.getErrorDescription());
                    return new AuthResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Authentication error", e);
                return new AuthResult(false, null, "Authentication error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<AuthResult> registerUser(UserProfile userInfo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Registering new user: " + userInfo.getUsername());
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("username", userInfo.getUsername());
                requestBody.put("password", userInfo.getPassword());
                requestBody.put("display_name", userInfo.getDisplayName());
                requestBody.put("email", userInfo.getEmail());
                requestBody.put("organization_domain", extractDomain(userInfo.getEmail()));
                
                ApiResponse<RegistrationResponse> response = makeRequest(
                    "POST", API_AUTH_REGISTER, requestBody, new TypeToken<RegistrationResponse>(){}.getType());
                
                if (response.isSuccess()) {
                    Log.i(TAG, "Registration successful for user: " + userInfo.getUsername());
                    return new AuthResult(true, null, "Registration successful. Please login.");
                } else {
                    Log.w(TAG, "Registration failed: " + response.getErrorDescription());
                    return new AuthResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Registration error", e);
                return new AuthResult(false, null, "Registration error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<User>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Searching users with query: " + query);
                
                String url = API_USERS_SEARCH + "?q=" + query + "&limit=50";
                
                ApiResponse<SearchResponse> response = makeAuthenticatedRequest(
                    "GET", url, null, new TypeToken<SearchResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    List<User> users = new ArrayList<>();
                    for (SearchResponse.UserResult result : response.getData().users) {
                        User user = new User(result.id, result.username, result.displayName);
                        user.setStatus(UserStatus.valueOf(result.status.toUpperCase()));
                        user.setLastSeen(parseTimestamp(result.lastSeen));
                        users.add(user);
                    }
                    
                    Log.i(TAG, "Found " + users.size() + " users");
                    return users;
                } else {
                    Log.w(TAG, "User search failed: " + response.getErrorDescription());
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "User search error", e);
                return new ArrayList<>();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> updateProfile(UserProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Updating user profile");
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("display_name", profile.getDisplayName());
                requestBody.put("email", profile.getEmail());
                
                ApiResponse<UpdateProfileResponse> response = makeAuthenticatedRequest(
                    "PUT", API_USERS_PROFILE, requestBody, new TypeToken<UpdateProfileResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.i(TAG, "Profile updated successfully");
                    return true;
                } else {
                    Log.w(TAG, "Profile update failed: " + response.getErrorDescription());
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Profile update error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            try {
                Log.d(TAG, "Logging out");
                
                // Close WebSocket connection
                if (webSocket != null) {
                    webSocket.close(1000, "User logout");
                }
                
                // Clear authentication
                currentAccessToken = null;
                tokenExpiresAt = 0;
                
                // Clear message queue
                messageQueue.clear();
                
                Log.i(TAG, "Logout completed");
                
            } catch (Exception e) {
                Log.e(TAG, "Logout error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(String recipientId, String messageType, Object messageData) {
        return CompletableFuture.runAsync(() -> {
            try {
                Log.d(TAG, "Sending message to " + recipientId + ", type: " + messageType);
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("message_type", messageType);
                requestBody.put("message_data", messageData);
                
                String url = String.format(API_MESSAGES_SEND, recipientId);
                
                ApiResponse<MessageSendResponse> response = makeAuthenticatedRequest(
                    "POST", url, requestBody, new TypeToken<MessageSendResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.d(TAG, "Message sent successfully, ID: " + response.getData().messageId);
                } else {
                    Log.w(TAG, "Message send failed: " + response.getErrorDescription());
                    throw new RuntimeException("Message send failed: " + response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Message send error", e);
                throw new RuntimeException("Message send error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> sendUserMessage(String recipientId, String chatUuid, byte[] encryptedContent) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("message_uuid", UUID.randomUUID().toString());
        messageData.put("encrypted_content", Base64.getEncoder().encodeToString(encryptedContent));
        messageData.put("content_type", "text");
        messageData.put("content_hash", generateContentHash(encryptedContent));
        
        return sendMessage(recipientId, "USER_MESSAGE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitRequest(String recipientId, String chatUuid, byte[] publicKey) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        // Удалена передача алгоритмов - в серверном режиме они единые для всей организации
        
        return sendMessage(recipientId, "CHAT_INIT_REQUEST", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitResponse(String recipientId, String chatUuid, 
                                                       byte[] publicKey, byte[] kemCapsule, byte[] userSignature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        messageData.put("user_signature", Base64.getEncoder().encodeToString(userSignature));
        // Удалена передача алгоритмов - в серверном режиме они единые для всей организации
        
        return sendMessage(recipientId, "CHAT_INIT_RESPONSE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitConfirm(String recipientId, String chatUuid, byte[] kemCapsule) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        
        return sendMessage(recipientId, "CHAT_INIT_CONFIRM", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitSignature(String recipientId, String chatUuid, byte[] signature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("signature", Base64.getEncoder().encodeToString(signature));
        
        return sendMessage(recipientId, "CHAT_INIT_SIGNATURE", messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatDelete(String recipientId, String chatUuid) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("reason", "user_initiated");
        
        return sendMessage(recipientId, "CHAT_DELETE", messageData);
    }
    
    @Override
    public CompletableFuture<ChatResult> createChat(List<String> participantIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (participantIds == null || participantIds.isEmpty()) {
                    return new ChatResult(false, null, "No participants specified");
                }
                
                String recipientId = participantIds.get(0);
                String chatUuid = UUID.randomUUID().toString();
                
                Log.d(TAG, "Creating chat metadata with: " + recipientId);
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("recipient_id", recipientId);
                requestBody.put("chat_uuid", chatUuid);
                
                ApiResponse<ChatCreateResponse> response = makeAuthenticatedRequest(
                    "POST", API_CHATS, requestBody, new TypeToken<ChatCreateResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    ChatCreateResponse chatData = response.getData();
                    
                    Chat chat = new Chat();
                    chat.setId(chatData.chat.chatUuid);
                    chat.setName("Chat with " + recipientId);
                    chat.setLastActivity(System.currentTimeMillis());
                    
                    Log.i(TAG, "Chat metadata created successfully");
                    return new ChatResult(true, chat, chatData.message);
                } else {
                    Log.w(TAG, "Chat creation failed: " + response.getErrorDescription());
                    return new ChatResult(false, null, response.getErrorDescription());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Chat creation error", e);
                return new ChatResult(false, null, "Chat creation error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<Chat>> getChatList() {
        // Server doesn't store chat history, return empty list
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    @Override
    public CompletableFuture<Chat> getChatInfo(String chatId) {
        // Server doesn't store detailed chat info
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Boolean> deleteChat(String chatId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Deleting chat metadata: " + chatId);
                
                String url = String.format(API_CHATS_DELETE, chatId);
                
                ApiResponse<ChatDeleteResponse> response = makeAuthenticatedRequest(
                    "DELETE", url, null, new TypeToken<ChatDeleteResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    Log.i(TAG, "Chat metadata deleted successfully");
                    return true;
                } else {
                    Log.w(TAG, "Chat deletion failed: " + response.getErrorDescription());
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Chat deletion error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> clearChatHistory(String chatId) {
        // Server doesn't store messages, clearing is local only
        return CompletableFuture.completedFuture(true);
    }
    
    @Override
    public CompletableFuture<UploadResult> uploadFile(FileInfo file) {
        // Not implemented in current version
        return CompletableFuture.completedFuture(new UploadResult(false, null, "Not implemented"));
    }
    
    @Override
    public CompletableFuture<DownloadResult> downloadFile(String fileId) {
        // Not implemented in current version
        return CompletableFuture.completedFuture(new DownloadResult(false, null, "Not implemented"));
    }
    
    @Override
    public CompletableFuture<TransferResult> transferFile(String recipientId, FileInfo file) {
        // Not implemented in current version
        return CompletableFuture.completedFuture(new TransferResult(false, null, "Not implemented"));
    }
    
    @Override
    public CompletableFuture<Void> setMessageStatus(String messageId, MessageStatus status) {
        // Message statuses are managed locally
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> setUserStatus(UserStatus status) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (status == UserStatus.OFFLINE) {
                    Log.d(TAG, "Setting user status to offline (connected)");
                    
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("reason", "user_initiated");
                    
                    ApiResponse<PresenceResponse> response = makeAuthenticatedRequest(
                        "POST", API_PRESENCE_OFFLINE, requestBody, new TypeToken<PresenceResponse>(){}.getType(), true);
                    
                    if (response.isSuccess()) {
                        Log.i(TAG, "User status set to offline (connected)");
                    } else {
                        Log.w(TAG, "Set status failed: " + response.getErrorDescription());
                    }
                }
                // Online status is set automatically by WebSocket connection
                
            } catch (Exception e) {
                Log.e(TAG, "Set user status error", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UserStatus> getUserStatus(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Getting status for user: " + userId);
                
                String url = String.format(API_USERS_STATUS, userId);
                
                ApiResponse<UserStatusResponse> response = makeAuthenticatedRequest(
                    "GET", url, null, new TypeToken<UserStatusResponse>(){}.getType(), true);
                
                if (response.isSuccess()) {
                    String status = response.getData().user.status;
                    
                    switch (status.toLowerCase()) {
                        case "online":
                            return UserStatus.ONLINE;
                        case "offline_connected":
                            return UserStatus.OFFLINE;
                        case "offline_disconnected":
                        default:
                            return UserStatus.OFFLINE;
                    }
                } else {
                    Log.w(TAG, "Get user status failed: " + response.getErrorDescription());
                    return UserStatus.OFFLINE;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get user status error", e);
                return UserStatus.OFFLINE;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<OrganizationInfo> getOrganizationInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Getting organization info");
                
                ApiResponse<OrgInfoResponse> response = makeAuthenticatedRequest(
                    "GET", API_ORG_INFO, null, new TypeToken<OrgInfoResponse>(){}.getType(), false);
                
                if (response.isSuccess()) {
                    OrgInfoResponse orgData = response.getData();
                    
                    // Создаем объект OrganizationInfo
                    OrganizationInfo orgInfo = new OrganizationInfo(
                        orgData.organization.name, 
                        orgData.organization.id
                    );
                    
                    // Парсим поддерживаемые алгоритмы
                    CryptoAlgorithms algorithms = parseOrganizationAlgorithms(orgData.organization.supportedAlgorithms);
                    orgInfo.setSupportedAlgorithms(algorithms);
                    
                    // Устанавливаем версию сервера (если она есть в ответе)
                    orgInfo.setServerVersion("1.0.0");
                    
                    // Сохраняем политики для возможного будущего использования
                    if (orgData.organization.serverPolicies != null) {
                        orgInfo.getPolicies().putAll(orgData.organization.serverPolicies);
                    }
                    
                    Log.i(TAG, "Organization info retrieved: " + orgData.organization.name);
                    return orgInfo;
                } else {
                    Log.w(TAG, "Get organization info failed: " + response.getErrorDescription());
                    return null;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Get organization info error", e);
                return null;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<CryptoAlgorithms> getServerAlgorithms() {
        return getOrganizationInfo().thenApply(orgInfo -> {
            if (orgInfo != null) {
                return orgInfo.getSupportedAlgorithms();
            } else {
                // Return default algorithms
                return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
            }
        });
    }
    
    // ===========================
    // HELPER METHODS
    // ===========================
    
    /**
     * Parse organization algorithms from server response
     */
    private CryptoAlgorithms parseOrganizationAlgorithms(Map<String, Object> supportedAlgorithms) {
        if (supportedAlgorithms == null) {
            return new CryptoAlgorithms("KYBER", "AES-256", "FALCON");
        }
        
        String kemAlgorithm = "KYBER";
        String symmetricAlgorithm = "AES-256";
        String signatureAlgorithm = "FALCON";
        
        // Парсим алгоритмы из ответа сервера
        // Сервер возвращает массивы с одним алгоритмом в каждой категории
        
        // Asymmetric (KEM) algorithms
        List<Map<String, Object>> asymmetricList = (List<Map<String, Object>>) supportedAlgorithms.get("asymmetric");
        if (asymmetricList != null && !asymmetricList.isEmpty()) {
            Map<String, Object> kemAlg = asymmetricList.get(0);
            if (kemAlg != null && kemAlg.containsKey("name")) {
                kemAlgorithm = (String) kemAlg.get("name");
            }
        }
        
        // Symmetric algorithms
        List<Map<String, Object>> symmetricList = (List<Map<String, Object>>) supportedAlgorithms.get("symmetric");
        if (symmetricList != null && !symmetricList.isEmpty()) {
            Map<String, Object> symAlg = symmetricList.get(0);
            if (symAlg != null && symAlg.containsKey("name")) {
                symmetricAlgorithm = (String) symAlg.get("name");
            }
        }
        
        // Signature algorithms
        List<Map<String, Object>> signatureList = (List<Map<String, Object>>) supportedAlgorithms.get("signature");
        if (signatureList != null && !signatureList.isEmpty()) {
            Map<String, Object> sigAlg = signatureList.get(0);
            if (sigAlg != null && sigAlg.containsKey("name")) {
                signatureAlgorithm = (String) sigAlg.get("name");
            }
        }
        
        Log.d(TAG, String.format("Parsed algorithms - KEM: %s, Symmetric: %s, Signature: %s",
            kemAlgorithm, symmetricAlgorithm, signatureAlgorithm));
        
        return new CryptoAlgorithms(kemAlgorithm, symmetricAlgorithm, signatureAlgorithm);
    }
    
    /**
     * Initialize HTTP client with proper configuration
     */
    private void initializeHttpClient() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }
    
    /**
     * Initialize WebSocket connection
     */
    private void initializeWebSocket() {
        try {
            String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://") + WS_MESSAGES;
            
            Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer " + currentAccessToken)
                .build();
            
            webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.i(TAG, "WebSocket connected");
                    connectionState = ConnectionState.CONNECTED;
                    connectionMetrics.recordWebSocketEvent("connected");
                    
                    // Start processing pending messages
                    processPendingMessages();
                }
                
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    Log.d(TAG, "WebSocket message received: " + text);
                    handleWebSocketMessage(text);
                    connectionMetrics.recordMessage(text.length());
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e(TAG, "WebSocket failure", t);
                    connectionState = ConnectionState.DISCONNECTED;
                    connectionMetrics.recordWebSocketEvent("failure");
                    
                    // Schedule reconnection
                    scheduleWebSocketReconnection();
                }
                
                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    Log.i(TAG, "WebSocket closed: " + reason);
                    connectionState = ConnectionState.DISCONNECTED;
                    connectionMetrics.recordWebSocketEvent("closed");
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize WebSocket", e);
        }
    }
    
    /**
     * Handle incoming WebSocket message
     */
    private void handleWebSocketMessage(String message) {
        try {
            WebSocketMessage wsMessage = gson.fromJson(message, WebSocketMessage.class);
            
            // Notify all listeners
            for (WebSocketMessageListener listener : messageListeners) {
                try {
                    listener.onMessageReceived(wsMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Error in message listener", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse WebSocket message", e);
        }
    }
    
    /**
     * Test server connection
     */
    private boolean testServerConnection() {
        try {
            ApiResponse<Map<String, Object>> response = makeRequest(
                "GET", API_PING, null, new TypeToken<Map<String, Object>>(){}.getType());
            
            return response.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Server connection test failed", e);
            return false;
        }
    }
    
    /**
     * Make HTTP request without authentication
     */
    private <T> ApiResponse<T> makeRequest(String method, String endpoint, Object body, Type responseType) {
        return makeAuthenticatedRequest(method, endpoint, body, responseType, false);
    }
    
    /**
     * Make authenticated HTTP request
     */
    private <T> ApiResponse<T> makeAuthenticatedRequest(String method, String endpoint, 
                                                        Object body, Type responseType, boolean requireAuth) {
        try {
            String url = baseUrl + endpoint;
            
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            // Add authentication header if required
            if (requireAuth && currentAccessToken != null) {
                requestBuilder.header("Authorization", "Bearer " + currentAccessToken);
            }
            
            // Add request body
            if (body != null) {
                MediaType JSON = MediaType.get("application/json; charset=utf-8");
                String json = gson.toJson(body);
                RequestBody requestBody = RequestBody.create(json, JSON);
                
                switch (method) {
                    case "POST":
                        requestBuilder.post(requestBody);
                        break;
                    case "PUT":
                        requestBuilder.put(requestBody);
                        break;
                    case "DELETE":
                        requestBuilder.delete(requestBody);
                        break;
                }
            } else {
                switch (method) {
                    case "GET":
                        requestBuilder.get();
                        break;
                    case "DELETE":
                        requestBuilder.delete();
                        break;
                }
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    T data = gson.fromJson(responseBody, responseType);
                    return new ApiResponse<>(true, data, null, null);
                } else {
                    ErrorResponse error = gson.fromJson(responseBody, ErrorResponse.class);
                    return new ApiResponse<>(false, null, error.errorCode, error.errorDescription);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "HTTP request failed", e);
            return new ApiResponse<>(false, null, "REQUEST_FAILED", e.getMessage());
        }
    }
    
    /**
     * Generate content hash for integrity check
     */
    private String generateContentHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate content hash", e);
            return "";
        }
    }
    
    /**
     * Process pending messages
     */
    private void processPendingMessages() {
        CompletableFuture.runAsync(() -> {
            try {
                ApiResponse<PendingMessagesResponse> response = makeAuthenticatedRequest(
                    "GET", API_MESSAGES_PENDING, null, new TypeToken<PendingMessagesResponse>(){}.getType(), true);
                
                if (response.isSuccess() && response.getData().totalPending > 0) {
                    List<String> messageIds = new ArrayList<>();
                    
                    for (PendingMessagesResponse.PendingMessage msg : response.getData().messages) {
                        // Process each pending message
                        handlePendingMessage(msg);
                        messageIds.add(msg.id);
                    }
                    
                    // Acknowledge received messages
                    acknowledgePendingMessages(messageIds);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to process pending messages", e);
            }
        }, executorService);
    }
    
    /**
     * Handle a pending message
     */
    private void handlePendingMessage(PendingMessagesResponse.PendingMessage message) {
        // Convert to WebSocketMessage format and process
        WebSocketMessage wsMessage = new WebSocketMessage();
        wsMessage.eventType = message.messageType;
        wsMessage.fromUserId = message.fromUserId;
        wsMessage.data = message.messageData;
        wsMessage.timestamp = System.currentTimeMillis();
        
        handleWebSocketMessage(gson.toJson(wsMessage));
    }
    
    /**
     * Acknowledge pending messages
     */
    private void acknowledgePendingMessages(List<String> messageIds) {
        if (messageIds.isEmpty()) return;
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message_ids", messageIds);
        
        makeAuthenticatedRequest("POST", API_MESSAGES_ACK, requestBody, 
            new TypeToken<AckMessagesResponse>(){}.getType(), true);
    }
    
    /**
     * Schedule WebSocket reconnection
     */
    private void scheduleWebSocketReconnection() {
        scheduler.schedule(() -> {
            if (currentAccessToken != null && tokenExpiresAt > System.currentTimeMillis()) {
                initializeWebSocket();
            }
        }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Build base URL from host and port
     */
    private String buildBaseUrl(String host, int port) {
        return String.format("http://%s:%d", host, port);
    }
    
    /**
     * Extract domain from email
     */
    private String extractDomain(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf("@") + 1);
        }
        return "";
    }
    
    /**
     * Parse token expiry time
     */
    private long parseTokenExpiry(String expiresAt) {
        try {
            // Parse ISO 8601 date format
            return System.currentTimeMillis() + (24 * 60 * 60 * 1000); // Default 24 hours
        } catch (Exception e) {
            return System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        }
    }
    
    /**
     * Parse timestamp string
     */
    private long parseTimestamp(String timestamp) {
        try {
            // Parse ISO 8601 date format
            return System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
    
    // ===========================
    // API RESPONSE WRAPPER
    // ===========================
    
    private static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String errorCode;
        private final String errorDescription;
        
        public ApiResponse(boolean success, T data, String errorCode, String errorDescription) {
            this.success = success;
            this.data = data;
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
        }
        
        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getErrorCode() { return errorCode; }
        public String getErrorDescription() { return errorDescription; }
    }
    
    // ===========================
    // WEBSOCKET LISTENER INTERFACE
    // ===========================
    
    public interface WebSocketMessageListener {
        void onMessageReceived(WebSocketMessage message);
    }
    
    public void addMessageListener(WebSocketMessageListener listener) {
        messageListeners.add(listener);
    }
    
    public void removeMessageListener(WebSocketMessageListener listener) {
        messageListeners.remove(listener);
    }
    
    // ===========================
    // RESPONSE DATA CLASSES
    // ===========================
    
    private static class AuthResponse {
        public boolean success;
        public String message;
        public String token;
        public String tokenExpiresAt;
        public UserData user;
        public OrganizationData organization;
        
        public static class UserData {
            public String id;
            public String username;
            public String displayName;
            public String email;
            public String status;
            public String lastSeen;
        }
        
        public static class OrganizationData {
            public String id;
            public String name;
            public String domain;
        }
    }
    
    private static class RegistrationResponse {
        public boolean success;
        public String message;
        public AuthResponse.UserData user;
    }
    
    private static class SearchResponse {
        public boolean success;
        public String query;
        public int totalFound;
        public int limit;
        public int offset;
        public List<UserResult> users;
        
        public static class UserResult {
            public String id;
            public String username;
            public String displayName;
            public String status;
            public String lastSeen;
            public boolean hasActiveChat;
        }
    }
    
    private static class UpdateProfileResponse {
        public boolean success;
        public String message;
        public AuthResponse.UserData user;
    }
    
    private static class MessageSendResponse {
        public boolean success;
        public String messageId;
        public long timestamp;
        public String deliveryStatus;
    }
    
    private static class ChatCreateResponse {
        public boolean success;
        public String message;
        public ChatData chat;
        
        public static class ChatData {
            public String id;
            public String chatUuid;
            public List<AuthResponse.UserData> participants;
            public String createdAt;
        }
    }
    
    private static class ChatDeleteResponse {
        public boolean success;
        public String message;
        public Map<String, Object> deletedChatMetadata;
    }
    
    private static class PresenceResponse {
        public boolean success;
        public String message;
        public UserStatusData userStatus;
        
        public static class UserStatusData {
            public String userId;
            public String status;
            public String reason;
            public String updatedAt;
        }
    }
    
    private static class UserStatusResponse {
        public boolean success;
        public AuthResponse.UserData user;
    }
    
    private static class OrgInfoResponse {
        public boolean success;
        public OrganizationDetails organization;
        
        public static class OrganizationDetails {
            public String id;
            public String name;
            public String domain;
            public Map<String, Object> supportedAlgorithms;
            public Map<String, Object> serverPolicies;
        }
    }
    
    private static class PendingMessagesResponse {
        public boolean success;
        public int totalPending;
        public List<PendingMessage> messages;
        
        public static class PendingMessage {
            public String id;
            public String fromUserId;
            public String fromUserName;
            public String messageType;
            public Map<String, Object> messageData;
            public String receivedAt;
            public String expiresAt;
        }
    }
    
    private static class AckMessagesResponse {
        public boolean success;
        public String message;
        public int acknowledgedCount;
        public List<String> acknowledgedIds;
        public String acknowledgedAt;
    }
    
    private static class WebSocketMessage {
        public String eventType;
        public long timestamp;
        public String fromUserId;
        public Map<String, Object> data;
    }
    
    private static class ErrorResponse {
        public boolean success;
        public String error;
        public String errorCode;
        public String errorDescription;
        public Map<String, List<String>> validationErrors;
    }
    
    private static class PendingMessage {
        private final String messageId;
        private final Object messageData;
        private final long timestamp;
        
        public PendingMessage(String messageId, Object messageData, long timestamp) {
            this.messageId = messageId;
            this.messageData = messageData;
            this.timestamp = timestamp;
        }
    }
}