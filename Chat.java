public class Chat {
    private String id;
    private String name;
    private ChatKeys keys;
    private long lastActivity;
    private PeerCryptoInfo peerCryptoInfo;
    
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
    public PeerCryptoInfo getPeerCryptoInfo() { return peerCryptoInfo; }
    public void setPeerCryptoInfo(PeerCryptoInfo peerCryptoInfo) { this.peerCryptoInfo = peerCryptoInfo; }
}