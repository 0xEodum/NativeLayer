package com.yumsg.core.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LocalNetworkManager - P2P Implementation with Algorithm Exchange
 * 
 * Implements peer-to-peer communication for local network mode.
 * Key difference from ServerNetworkManager: MUST transmit crypto algorithms
 * during chat initialization as there's no central authority.
 */
public class LocalNetworkManager implements NetworkManager {
    private static final String TAG = "LocalNetworkManager";
    
    // Network configuration
    private static final int DISCOVERY_PORT = 8888;
    private static final int MESSAGING_PORT = 8889;
    private static final int FILE_TRANSFER_PORT = 8890;
    private static final String MULTICAST_GROUP = "224.0.2.60";
    private static final int DISCOVERY_INTERVAL_MS = 5000;
    private static final int PEER_TIMEOUT_MS = 30000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    
    // Protocol messages
    private static final String MSG_DISCOVERY_REQUEST = "YUMSG_DISCOVERY_REQUEST";
    private static final String MSG_DISCOVERY_RESPONSE = "YUMSG_DISCOVERY_RESPONSE";
    private static final String MSG_USER_MESSAGE = "YUMSG_USER_MESSAGE";
    private static final String MSG_CHAT_INIT_REQUEST = "YUMSG_CHAT_INIT_REQUEST";
    private static final String MSG_CHAT_INIT_RESPONSE = "YUMSG_CHAT_INIT_RESPONSE";
    private static final String MSG_CHAT_INIT_CONFIRM = "YUMSG_CHAT_INIT_CONFIRM";
    private static final String MSG_CHAT_INIT_SIGNATURE = "YUMSG_CHAT_INIT_SIGNATURE";
    private static final String MSG_CHAT_DELETE = "YUMSG_CHAT_DELETE";
    private static final String MSG_FILE_TRANSFER = "YUMSG_FILE_TRANSFER";
    private static final String MSG_STATUS_UPDATE = "YUMSG_STATUS_UPDATE";
    
    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    
    // Core components
    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    
    // Network components
    private WifiManager wifiManager;
    private WifiP2pManager p2pManager;
    private WifiP2pManager.Channel p2pChannel;
    private ConnectivityManager connectivityManager;
    
    // Discovery and messaging
    private MulticastSocket discoverySocket;
    private ServerSocket messagingSocket;
    private ServerSocket fileTransferSocket;
    
    // State management
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile ConnectionMetrics connectionMetrics;
    private volatile String localPeerId;
    private volatile UserProfile localUserProfile;
    
    // Peer management
    private final Map<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, PeerConnection> activePeerConnections = new ConcurrentHashMap<>();
    private final Set<P2PMessageListener> messageListeners = ConcurrentHashMap.newKeySet();
    
    // Message queuing
    private final Queue<QueuedMessage> messageQueue = new ConcurrentLinkedQueue<>();
    
    // Discovery broadcast task
    private ScheduledFuture<?> discoveryBroadcastTask;
    
    /**
     * Constructor
     */
    public LocalNetworkManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create();
        this.executorService = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.connectionMetrics = new ConnectionMetrics();
        this.localPeerId = generatePeerId();
        
        Log.d(TAG, "LocalNetworkManager instance created with peer ID: " + localPeerId);
    }
    
    // ===========================
    // NETWORKMANAGER INTERFACE IMPLEMENTATION
    // ===========================
    
    @Override
    public CompletableFuture<ConnectionResult> connect(Map<String, Object> connectionParams) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                Log.d(TAG, "Connecting to local P2P network");
                
                // Extract local user info if provided
                if (connectionParams != null && connectionParams.containsKey("userProfile")) {
                    this.localUserProfile = (UserProfile) connectionParams.get("userProfile");
                }
                
                // Initialize network components
                if (!initializeNetworkComponents()) {
                    return new ConnectionResult(false, "Failed to initialize network components");
                }
                
                // Start discovery
                if (!startDiscovery()) {
                    return new ConnectionResult(false, "Failed to start device discovery");
                }
                
                // Start messaging server
                if (!startMessagingServer()) {
                    return new ConnectionResult(false, "Failed to start messaging server");
                }
                
                // Update connection state
                connectionState = ConnectionState.CONNECTED;
                connectionMetrics.recordConnectionAttempt(true);
                
                Log.i(TAG, "Connected to local P2P network successfully");
                return new ConnectionResult(true, "Connected to local P2P network");
                
            } catch (Exception e) {
                Log.e(TAG, "P2P connection failed", e);
                connectionMetrics.recordConnectionAttempt(false);
                return new ConnectionResult(false, "P2P connection failed: " + e.getMessage());
            } finally {
                lock.writeLock().unlock();
            }
        }, executorService);
    }
    
    @Override
    public void disconnect() {
        lock.writeLock().lock();
        try {
            Log.d(TAG, "Disconnecting from P2P network");
            
            // Stop discovery
            stopDiscovery();
            
            // Close all peer connections
            for (PeerConnection connection : activePeerConnections.values()) {
                connection.close();
            }
            activePeerConnections.clear();
            
            // Close server sockets
            closeSocket(messagingSocket);
            closeSocket(fileTransferSocket);
            
            // Clear discovered peers
            discoveredPeers.clear();
            
            // Update state
            connectionState = ConnectionState.DISCONNECTED;
            
            Log.i(TAG, "Disconnected from P2P network");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED && isDiscovering.get();
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
        // In P2P mode, authentication is local only
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Local authentication for user: " + credentials.getUsername());
                
                // Create local user profile
                UserProfile profile = new UserProfile();
                profile.setUsername(credentials.getUsername());
                profile.setEmail(credentials.getEmail());
                profile.setPassword(credentials.getPassword());
                
                this.localUserProfile = profile;
                
                // Store locally
                SharedPreferencesManager.getInstance().setUserProfile(profile);
                
                return new AuthResult(true, localPeerId, "Local authentication successful");
                
            } catch (Exception e) {
                Log.e(TAG, "Local authentication error", e);
                return new AuthResult(false, null, "Authentication error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<AuthResult> registerUser(UserProfile userInfo) {
        // In P2P mode, registration is local only
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Local registration for user: " + userInfo.getUsername());
                
                this.localUserProfile = userInfo;
                
                // Store locally
                SharedPreferencesManager.getInstance().setUserProfile(userInfo);
                
                return new AuthResult(true, localPeerId, "Local registration successful");
                
            } catch (Exception e) {
                Log.e(TAG, "Local registration error", e);
                return new AuthResult(false, null, "Registration error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<User>> searchUsers(String query) {
        return CompletableFuture.supplyAsync(() -> {
            List<User> users = new ArrayList<>();
            
            // Search among discovered peers
            for (DiscoveredPeer peer : discoveredPeers.values()) {
                if (peer.getUsername().toLowerCase().contains(query.toLowerCase()) ||
                    peer.getDisplayName().toLowerCase().contains(query.toLowerCase())) {
                    
                    User user = new User(peer.getPeerId(), peer.getUsername(), peer.getDisplayName());
                    user.setStatus(peer.isOnline() ? UserStatus.ONLINE : UserStatus.OFFLINE);
                    user.setLastSeen(peer.getLastSeen());
                    users.add(user);
                }
            }
            
            Log.d(TAG, "Found " + users.size() + " users for query: " + query);
            return users;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> updateProfile(UserProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.localUserProfile = profile;
                SharedPreferencesManager.getInstance().setUserProfile(profile);
                
                // Broadcast updated profile to peers
                broadcastPresence();
                
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Profile update error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            // Clear local profile
            this.localUserProfile = null;
            SharedPreferencesManager.getInstance().clearUserSession();
            
            // Disconnect from network
            disconnect();
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(String recipientId, String messageType, Object messageData) {
        return CompletableFuture.runAsync(() -> {
            try {
                PeerConnection connection = activePeerConnections.get(recipientId);
                if (connection == null) {
                    // Try to connect if not connected
                    if (!connectToPeer(recipientId).get(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("Failed to connect to peer");
                    }
                    connection = activePeerConnections.get(recipientId);
                }
                
                if (connection != null) {
                    P2PMessage message = new P2PMessage();
                    message.setType(messageType);
                    message.setData(messageData);
                    message.setSenderId(localPeerId);
                    message.setTimestamp(System.currentTimeMillis());
                    
                    connection.sendMessage(message);
                    Log.d(TAG, "Message sent to peer: " + recipientId);
                } else {
                    throw new RuntimeException("No connection to peer");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
                // Queue message for later delivery
                queueMessage(recipientId, messageType, messageData);
                throw new RuntimeException("Message send failed", e);
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
        
        return sendMessage(recipientId, MSG_USER_MESSAGE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitRequest(String recipientId, String chatUuid, byte[] publicKey) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        
        // CRITICAL: In P2P mode, we MUST send crypto algorithms
        CryptoAlgorithms algorithms = SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        Map<String, String> cryptoAlgorithms = new HashMap<>();
        cryptoAlgorithms.put("asymmetric", algorithms.getKemAlgorithm());
        cryptoAlgorithms.put("symmetric", algorithms.getSymmetricAlgorithm());
        cryptoAlgorithms.put("signature", algorithms.getSignatureAlgorithm());
        messageData.put("crypto_algorithms", cryptoAlgorithms);
        
        Log.d(TAG, "Sending chat init request with algorithms: " + cryptoAlgorithms);
        
        return sendMessage(recipientId, MSG_CHAT_INIT_REQUEST, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitResponse(String recipientId, String chatUuid, 
                                                       byte[] publicKey, byte[] kemCapsule, byte[] userSignature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("public_key", Base64.getEncoder().encodeToString(publicKey));
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        messageData.put("user_signature", Base64.getEncoder().encodeToString(userSignature));
        
        // CRITICAL: In P2P mode, we MUST send crypto algorithms
        CryptoAlgorithms algorithms = SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        Map<String, String> cryptoAlgorithms = new HashMap<>();
        cryptoAlgorithms.put("asymmetric", algorithms.getKemAlgorithm());
        cryptoAlgorithms.put("symmetric", algorithms.getSymmetricAlgorithm());
        cryptoAlgorithms.put("signature", algorithms.getSignatureAlgorithm());
        messageData.put("crypto_algorithms", cryptoAlgorithms);
        
        Log.d(TAG, "Sending chat init response with algorithms: " + cryptoAlgorithms);
        
        return sendMessage(recipientId, MSG_CHAT_INIT_RESPONSE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitConfirm(String recipientId, String chatUuid, byte[] kemCapsule) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("kem_capsule", Base64.getEncoder().encodeToString(kemCapsule));
        
        return sendMessage(recipientId, MSG_CHAT_INIT_CONFIRM, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatInitSignature(String recipientId, String chatUuid, byte[] signature) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("signature", Base64.getEncoder().encodeToString(signature));
        
        return sendMessage(recipientId, MSG_CHAT_INIT_SIGNATURE, messageData);
    }
    
    @Override
    public CompletableFuture<Void> sendChatDelete(String recipientId, String chatUuid) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chat_uuid", chatUuid);
        messageData.put("reason", "user_initiated");
        
        return sendMessage(recipientId, MSG_CHAT_DELETE, messageData);
    }
    
    @Override
    public CompletableFuture<ChatResult> createChat(List<String> participantIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (participantIds == null || participantIds.isEmpty()) {
                    return new ChatResult(false, null, "No participants specified");
                }
                
                String peerId = participantIds.get(0);
                String chatUuid = UUID.randomUUID().toString();
                
                // Ensure connection to peer
                if (!activePeerConnections.containsKey(peerId)) {
                    if (!connectToPeer(peerId).get(5, TimeUnit.SECONDS)) {
                        return new ChatResult(false, null, "Failed to connect to peer");
                    }
                }
                
                // Create local chat object
                Chat chat = new Chat();
                chat.setId(chatUuid);
                chat.setName("Chat with " + peerId);
                chat.setLastActivity(System.currentTimeMillis());
                
                // Store chat locally
                DatabaseManager.getInstance().saveChat(chat);
                
                return new ChatResult(true, chat, "Chat created successfully");
                
            } catch (Exception e) {
                Log.e(TAG, "Chat creation error", e);
                return new ChatResult(false, null, "Chat creation error: " + e.getMessage());
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<Chat>> getChatList() {
        return CompletableFuture.supplyAsync(() -> {
            // Return chats from local database
            return DatabaseManager.getInstance().getAllChats();
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Chat> getChatInfo(String chatId) {
        return CompletableFuture.supplyAsync(() -> {
            // Get chat from local database
            return DatabaseManager.getInstance().getChat(chatId);
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> deleteChat(String chatId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Delete from local database
                DatabaseManager.getInstance().deleteChat(chatId);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Chat deletion error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Boolean> clearChatHistory(String chatId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Clear messages from local database
                DatabaseManager.getInstance().clearChatMessages(chatId);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Clear chat history error", e);
                return false;
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UploadResult> uploadFile(FileInfo file) {
        // P2P file transfer implementation
        return CompletableFuture.supplyAsync(() -> {
            // Not implemented in current version
            return new UploadResult(false, null, "P2P file transfer not implemented");
        }, executorService);
    }
    
    @Override
    public CompletableFuture<DownloadResult> downloadFile(String fileId) {
        // P2P file transfer implementation
        return CompletableFuture.supplyAsync(() -> {
            // Not implemented in current version
            return new DownloadResult(false, null, "P2P file transfer not implemented");
        }, executorService);
    }
    
    @Override
    public CompletableFuture<TransferResult> transferFile(String recipientId, FileInfo file) {
        // P2P file transfer implementation
        return CompletableFuture.supplyAsync(() -> {
            // Not implemented in current version
            return new TransferResult(false, null, "P2P file transfer not implemented");
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> setMessageStatus(String messageId, MessageStatus status) {
        return CompletableFuture.runAsync(() -> {
            // Update status in local database
            DatabaseManager.getInstance().updateMessageStatus(messageId, status);
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> setUserStatus(UserStatus status) {
        return CompletableFuture.runAsync(() -> {
            // Broadcast status update to all connected peers
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("status", status.toString());
            statusData.put("timestamp", System.currentTimeMillis());
            
            P2PMessage statusMessage = new P2PMessage();
            statusMessage.setType(MSG_STATUS_UPDATE);
            statusMessage.setData(statusData);
            statusMessage.setSenderId(localPeerId);
            statusMessage.setTimestamp(System.currentTimeMillis());
            
            // Send to all connected peers
            for (PeerConnection connection : activePeerConnections.values()) {
                try {
                    connection.sendMessage(statusMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send status update to peer", e);
                }
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<UserStatus> getUserStatus(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            DiscoveredPeer peer = discoveredPeers.get(userId);
            if (peer != null && peer.isOnline()) {
                return UserStatus.ONLINE;
            }
            return UserStatus.OFFLINE;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<OrganizationInfo> getOrganizationInfo() {
        // P2P mode: No organization concept
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<CryptoAlgorithms> getServerAlgorithms() {
        // P2P mode: Use local algorithms from preferences
        return CompletableFuture.supplyAsync(() -> {
            return SharedPreferencesManager.getInstance().getCryptoAlgorithms();
        }, executorService);
    }
    
    // ===========================
    // P2P SPECIFIC METHODS
    // ===========================
    
    /**
     * Start device discovery
     */
    private boolean startDiscovery() {
        lock.writeLock().lock();
        try {
            if (isDiscovering.get()) {
                Log.w(TAG, "Discovery already running");
                return true;
            }
            
            Log.d(TAG, "Starting P2P device discovery");
            
            // Initialize discovery socket
            if (!initializeDiscoverySocket()) {
                return false;
            }
            
            // Start discovery listener
            executorService.submit(new DiscoveryListenerTask());
            
            // Start periodic discovery broadcasts
            discoveryBroadcastTask = scheduler.scheduleAtFixedRate(
                this::broadcastPresence,
                0,
                DISCOVERY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
            
            isDiscovering.set(true);
            Log.i(TAG, "P2P device discovery started");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start discovery", e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Stop device discovery
     */
    private void stopDiscovery() {
        lock.writeLock().lock();
        try {
            if (!isDiscovering.get()) {
                return;
            }
            
            Log.d(TAG, "Stopping P2P device discovery");
            
            isDiscovering.set(false);
            
            // Cancel broadcast task
            if (discoveryBroadcastTask != null) {
                discoveryBroadcastTask.cancel(true);
                discoveryBroadcastTask = null;
            }
            
            // Close discovery socket
            if (discoverySocket != null) {
                discoverySocket.close();
                discoverySocket = null;
            }
            
            Log.i(TAG, "P2P device discovery stopped");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Broadcast presence to other peers
     */
    private void broadcastPresence() {
        if (!isDiscovering.get() || localUserProfile == null) {
            return;
        }
        
        try {
            DiscoveryMessage discoveryMsg = new DiscoveryMessage();
            discoveryMsg.setType(MSG_DISCOVERY_REQUEST);
            discoveryMsg.setUsername(localUserProfile.getUsername());
            discoveryMsg.setDisplayName(localUserProfile.getDisplayName());
            discoveryMsg.setPeerId(localPeerId);
            discoveryMsg.setTimestamp(System.currentTimeMillis());
            
            String messageJson = gson.toJson(discoveryMsg);
            byte[] messageBytes = messageJson.getBytes(StandardCharsets.UTF_8);
            
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            DatagramPacket packet = new DatagramPacket(
                messageBytes, 
                messageBytes.length, 
                group, 
                DISCOVERY_PORT
            );
            
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                discoverySocket.send(packet);
                Log.v(TAG, "Presence broadcast sent");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast presence", e);
        }
    }
    
    /**
     * Connect to specific peer
     */
    private CompletableFuture<Boolean> connectToPeer(String peerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DiscoveredPeer peer = discoveredPeers.get(peerId);
                if (peer == null) {
                    Log.w(TAG, "Peer not found: " + peerId);
                    return false;
                }
                
                // Check if already connected
                if (activePeerConnections.containsKey(peerId)) {
                    return true;
                }
                
                Log.d(TAG, "Connecting to peer: " + peerId);
                
                // Create direct socket connection
                Socket socket = new Socket();
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                socket.connect(new InetSocketAddress(peer.getIpAddress(), MESSAGING_PORT), CONNECTION_TIMEOUT_MS);
                
                // Create peer connection
                PeerConnection connection = new PeerConnection(peer, socket);
                activePeerConnections.put(peerId, connection);
                
                // Start connection handler
                executorService.submit(new PeerConnectionHandler(connection));
                
                Log.i(TAG, "Connected to peer: " + peerId);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to peer: " + peerId, e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Initialize network components
     */
    private boolean initializeNetworkComponents() {
        try {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            // Initialize Wi-Fi P2P if available
            p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2pManager != null) {
                p2pChannel = p2pManager.initialize(context, context.getMainLooper(), null);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize network components", e);
            return false;
        }
    }
    
    /**
     * Initialize discovery socket
     */
    private boolean initializeDiscoverySocket() {
        try {
            discoverySocket = new MulticastSocket(DISCOVERY_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            discoverySocket.joinGroup(group);
            discoverySocket.setTimeToLive(1); // Local network only
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize discovery socket", e);
            return false;
        }
    }
    
    /**
     * Start messaging server
     */
    private boolean startMessagingServer() {
        try {
            messagingSocket = new ServerSocket(MESSAGING_PORT);
            fileTransferSocket = new ServerSocket(FILE_TRANSFER_PORT);
            
            // Start server threads
            executorService.submit(new MessagingServerTask());
            executorService.submit(new FileTransferServerTask());
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start messaging server", e);
            return false;
        }
    }
    
    /**
     * Handle incoming discovery message
     */
    private void handleDiscoveryMessage(String message, InetAddress senderAddress) {
        try {
            DiscoveryMessage discoveryMsg = gson.fromJson(message, DiscoveryMessage.class);
            
            // Ignore our own broadcasts
            if (discoveryMsg.getPeerId().equals(localPeerId)) {
                return;
            }
            
            // Create or update discovered peer
            DiscoveredPeer peer = new DiscoveredPeer();
            peer.setPeerId(discoveryMsg.getPeerId());
            peer.setUsername(discoveryMsg.getUsername());
            peer.setDisplayName(discoveryMsg.getDisplayName());
            peer.setIpAddress(senderAddress.getHostAddress());
            peer.setLastSeen(System.currentTimeMillis());
            peer.setOnline(true);
            
            discoveredPeers.put(peer.getPeerId(), peer);
            
            Log.d(TAG, "Discovered peer: " + peer.getDisplayName() + " at " + peer.getIpAddress());
            
            // Send discovery response
            if (MSG_DISCOVERY_REQUEST.equals(discoveryMsg.getType())) {
                sendDiscoveryResponse(senderAddress);
            }
            
            // Notify listeners
            notifyPeerDiscovered(peer);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle discovery message", e);
        }
    }
    
    /**
     * Send discovery response
     */
    private void sendDiscoveryResponse(InetAddress targetAddress) {
        if (localUserProfile == null) {
            return;
        }
        
        try {
            DiscoveryMessage response = new DiscoveryMessage();
            response.setType(MSG_DISCOVERY_RESPONSE);
            response.setUsername(localUserProfile.getUsername());
            response.setDisplayName(localUserProfile.getDisplayName());
            response.setPeerId(localPeerId);
            response.setTimestamp(System.currentTimeMillis());
            
            String messageJson = gson.toJson(response);
            byte[] messageBytes = messageJson.getBytes(StandardCharsets.UTF_8);
            
            DatagramPacket packet = new DatagramPacket(
                messageBytes,
                messageBytes.length,
                targetAddress,
                DISCOVERY_PORT
            );
            
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                discoverySocket.send(packet);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send discovery response", e);
        }
    }
    
    /**
     * Handle incoming P2P message
     */
    private void handleP2PMessage(P2PMessage message, String fromPeerId) {
        try {
            Log.d(TAG, "Handling P2P message type: " + message.getType() + " from " + fromPeerId);
            
            // Update peer last seen
            DiscoveredPeer peer = discoveredPeers.get(fromPeerId);
            if (peer != null) {
                peer.setLastSeen(System.currentTimeMillis());
            }
            
            // Notify listeners
            for (P2PMessageListener listener : messageListeners) {
                try {
                    listener.onMessageReceived(message, fromPeerId);
                } catch (Exception e) {
                    Log.e(TAG, "Error in message listener", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle P2P message", e);
        }
    }
    
    /**
     * Queue message for later delivery
     */
    private void queueMessage(String recipientId, String messageType, Object messageData) {
        QueuedMessage queuedMessage = new QueuedMessage();
        queuedMessage.recipientId = recipientId;
        queuedMessage.messageType = messageType;
        queuedMessage.messageData = messageData;
        queuedMessage.timestamp = System.currentTimeMillis();
        
        messageQueue.offer(queuedMessage);
        Log.d(TAG, "Message queued for later delivery to: " + recipientId);
    }
    
    /**
     * Process queued messages
     */
    private void processQueuedMessages() {
        while (!messageQueue.isEmpty()) {
            QueuedMessage queuedMessage = messageQueue.poll();
            if (queuedMessage != null && activePeerConnections.containsKey(queuedMessage.recipientId)) {
                sendMessage(queuedMessage.recipientId, queuedMessage.messageType, queuedMessage.messageData);
            }
        }
    }
    
    /**
     * Notify listeners about discovered peer
     */
    private void notifyPeerDiscovered(DiscoveredPeer peer) {
        for (P2PMessageListener listener : messageListeners) {
            try {
                listener.onPeerDiscovered(peer);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying peer discovered", e);
            }
        }
    }
    
    /**
     * Generate content hash
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
     * Generate unique peer ID
     */
    private String generatePeerId() {
        return "peer_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Close socket safely
     */
    private void closeSocket(ServerSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing server socket", e);
            }
        }
    }
    
    /**
     * Add P2P message listener
     */
    public void addMessageListener(P2PMessageListener listener) {
        messageListeners.add(listener);
    }
    
    /**
     * Remove P2P message listener
     */
    public void removeMessageListener(P2PMessageListener listener) {
        messageListeners.remove(listener);
    }
    
    /**
     * Get discovered peers
     */
    public List<DiscoveredPeer> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }
    
    // ===========================
    // INNER CLASSES
    // ===========================
    
    /**
     * Discovery listener task
     */
    private class DiscoveryListenerTask implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            
            while (isDiscovering.get() && discoverySocket != null && !discoverySocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    discoverySocket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleDiscoveryMessage(message, packet.getAddress());
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue
                } catch (Exception e) {
                    if (isDiscovering.get()) {
                        Log.e(TAG, "Discovery listener error", e);
                    }
                }
            }
            
            Log.d(TAG, "Discovery listener stopped");
        }
    }
    
    /**
     * Messaging server task
     */
    private class MessagingServerTask implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Messaging server started on port " + MESSAGING_PORT);
            
            while (messagingSocket != null && !messagingSocket.isClosed()) {
                try {
                    Socket clientSocket = messagingSocket.accept();
                    clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    
                    // Handle incoming connection
                    executorService.submit(() -> handleIncomingConnection(clientSocket));
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue
                } catch (Exception e) {
                    if (messagingSocket != null && !messagingSocket.isClosed()) {
                        Log.e(TAG, "Messaging server error", e);
                    }
                }
            }
            
            Log.d(TAG, "Messaging server stopped");
        }
    }
    
    /**
     * File transfer server task
     */
    private class FileTransferServerTask implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "File transfer server started on port " + FILE_TRANSFER_PORT);
            
            while (fileTransferSocket != null && !fileTransferSocket.isClosed()) {
                try {
                    Socket clientSocket = fileTransferSocket.accept();
                    
                    // Handle file transfer in separate thread
                    executorService.submit(() -> handleFileTransfer(clientSocket));
                    
                } catch (Exception e) {
                    if (fileTransferSocket != null && !fileTransferSocket.isClosed()) {
                        Log.e(TAG, "File transfer server error", e);
                    }
                }
            }
            
            Log.d(TAG, "File transfer server stopped");
        }
    }
    
    /**
     * Handle incoming connection
     */
    private void handleIncomingConnection(Socket socket) {
        try {
            // Read peer identification
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String peerIdLine = reader.readLine();
            
            if (peerIdLine != null && peerIdLine.startsWith("PEER_ID:")) {
                String peerId = peerIdLine.substring(8);
                DiscoveredPeer peer = discoveredPeers.get(peerId);
                
                if (peer != null) {
                    PeerConnection connection = new PeerConnection(peer, socket);
                    activePeerConnections.put(peerId, connection);
                    
                    // Start connection handler
                    executorService.submit(new PeerConnectionHandler(connection));
                    
                    Log.d(TAG, "Accepted connection from peer: " + peerId);
                } else {
                    Log.w(TAG, "Unknown peer connection attempt: " + peerId);
                    socket.close();
                }
            } else {
                Log.w(TAG, "Invalid peer connection attempt");
                socket.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle incoming connection", e);
            try {
                socket.close();
            } catch (IOException ignore) {}
        }
    }
    
    /**
     * Handle file transfer
     */
    private void handleFileTransfer(Socket socket) {
        // TODO: Implement P2P file transfer
        Log.d(TAG, "File transfer not implemented");
        try {
            socket.close();
        } catch (IOException ignore) {}
    }
    
    /**
     * Peer connection handler
     */
    private class PeerConnectionHandler implements Runnable {
        private final PeerConnection connection;
        
        public PeerConnectionHandler(PeerConnection connection) {
            this.connection = connection;
        }
        
        @Override
        public void run() {
            try {
                // Send our peer ID
                connection.sendRawMessage("PEER_ID:" + localPeerId);
                
                // Process queued messages for this peer
                processQueuedMessages();
                
                // Read messages
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getSocket().getInputStream())
                );
                
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        P2PMessage message = gson.fromJson(line, P2PMessage.class);
                        handleP2PMessage(message, connection.getPeer().getPeerId());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse P2P message", e);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Peer connection error", e);
            } finally {
                // Remove connection
                activePeerConnections.remove(connection.getPeer().getPeerId());
                connection.close();
                
                Log.d(TAG, "Peer connection closed: " + connection.getPeer().getPeerId());
            }
        }
    }
    
    /**
     * Peer connection class
     */
    private class PeerConnection {
        private final DiscoveredPeer peer;
        private final Socket socket;
        private final PrintWriter writer;
        
        public PeerConnection(DiscoveredPeer peer, Socket socket) throws IOException {
            this.peer = peer;
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }
        
        public void sendMessage(P2PMessage message) throws IOException {
            String json = gson.toJson(message);
            writer.println(json);
        }
        
        public void sendRawMessage(String message) {
            writer.println(message);
        }
        
        public DiscoveredPeer getPeer() {
            return peer;
        }
        
        public Socket getSocket() {
            return socket;
        }
        
        public void close() {
            try {
                writer.close();
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing peer connection", e);
            }
        }
    }
    
    // ===========================
    // DATA CLASSES
    // ===========================
    
    /**
     * Discovered peer information
     */
    public static class DiscoveredPeer {
        private String peerId;
        private String username;
        private String displayName;
        private String ipAddress;
        private long lastSeen;
        private boolean online;
        
        // Getters and setters
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
        
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
    }
    
    /**
     * P2P message
     */
    public static class P2PMessage {
        private String type;
        private Object data;
        private String senderId;
        private long timestamp;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * Discovery message
     */
    private static class DiscoveryMessage {
        private String type;
        private String username;
        private String displayName;
        private String peerId;
        private long timestamp;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * Queued message
     */
    private static class QueuedMessage {
        String recipientId;
        String messageType;
        Object messageData;
        long timestamp;
    }
    
    /**
     * P2P message listener interface
     */
    public interface P2PMessageListener {
        void onMessageReceived(P2PMessage message, String fromPeerId);
        void onPeerDiscovered(DiscoveredPeer peer);
        void onPeerDisconnected(String peerId);
    }
}