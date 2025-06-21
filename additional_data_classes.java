package com.yumsg.core.data;

import android.util.Base64;
import java.util.Arrays;

/**
 * OrganizationKeys - Data class for storing organization signature keys
 * 
 * Represents the cryptographic identity of an organization including
 * the signature algorithm and corresponding key pair.
 */
public class OrganizationKeys {
    private String organizationName;
    private String signatureAlgorithm;
    private byte[] publicSignatureKey;
    private byte[] privateSignatureKey;
    private long createdAt;
    private boolean isActive;
    
    /**
     * Default constructor
     */
    public OrganizationKeys() {
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }
    
    /**
     * Constructor with parameters
     */
    public OrganizationKeys(String organizationName, String signatureAlgorithm, 
                           byte[] publicSignatureKey, byte[] privateSignatureKey) {
        this.organizationName = organizationName;
        this.signatureAlgorithm = signatureAlgorithm;
        this.publicSignatureKey = publicSignatureKey;
        this.privateSignatureKey = privateSignatureKey;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }
    
    // Getters and setters
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }
    
    public byte[] getPublicSignatureKey() { return publicSignatureKey; }
    public void setPublicSignatureKey(byte[] publicSignatureKey) { this.publicSignatureKey = publicSignatureKey; }
    
    public byte[] getPrivateSignatureKey() { return privateSignatureKey; }
    public void setPrivateSignatureKey(byte[] privateSignatureKey) { this.privateSignatureKey = privateSignatureKey; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    /**
     * Check if the key pair is complete
     */
    public boolean isComplete() {
        return organizationName != null && !organizationName.trim().isEmpty() &&
               signatureAlgorithm != null && !signatureAlgorithm.trim().isEmpty() &&
               publicSignatureKey != null && publicSignatureKey.length > 0 &&
               privateSignatureKey != null && privateSignatureKey.length > 0;
    }
    
    /**
     * Get public key as Base64 string
     */
    public String getPublicKeyBase64() {
        return publicSignatureKey != null ? Base64.encodeToString(publicSignatureKey, Base64.NO_WRAP) : null;
    }
    
    /**
     * Set public key from Base64 string
     */
    public void setPublicKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.publicSignatureKey = Base64.decode(base64Key, Base64.NO_WRAP);
        }
    }
    
    /**
     * Get private key as Base64 string
     */
    public String getPrivateKeyBase64() {
        return privateSignatureKey != null ? Base64.encodeToString(privateSignatureKey, Base64.NO_WRAP) : null;
    }
    
    /**
     * Set private key from Base64 string
     */
    public void setPrivateKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.privateSignatureKey = Base64.decode(base64Key, Base64.NO_WRAP);
        }
    }
    
    /**
     * Generate unique ID for this organization key set
     */
    public String getId() {
        return organizationName + ":" + signatureAlgorithm;
    }
    
    /**
     * Secure wipe of sensitive key material
     */
    public void secureWipe() {
        if (privateSignatureKey != null) {
            Arrays.fill(privateSignatureKey, (byte) 0);
        }
        if (publicSignatureKey != null) {
            Arrays.fill(publicSignatureKey, (byte) 0);
        }
    }
    
    @Override
    public String toString() {
        return "OrganizationKeys{" +
                "organizationName='" + organizationName + '\'' +
                ", signatureAlgorithm='" + signatureAlgorithm + '\'' +
                ", hasPublicKey=" + (publicSignatureKey != null && publicSignatureKey.length > 0) +
                ", hasPrivateKey=" + (privateSignatureKey != null && privateSignatureKey.length > 0) +
                ", createdAt=" + createdAt +
                ", isActive=" + isActive +
                '}';
    }
}

/**
 * PeerCryptoInfo - Information about peer's cryptographic capabilities
 * 
 * Stores the cryptographic algorithms and signature key used by a specific peer
 * in a chat conversation.
 */
public class PeerCryptoInfo {
    private String peerId;
    private CryptoAlgorithms peerAlgorithms;
    private byte[] peerSignaturePublicKey;
    private String peerSignatureAlgorithm;
    private long lastUpdated;
    private boolean verified;
    
    /**
     * Default constructor
     */
    public PeerCryptoInfo() {
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }
    
    /**
     * Constructor with parameters
     */
    public PeerCryptoInfo(String peerId, CryptoAlgorithms peerAlgorithms, 
                         byte[] peerSignaturePublicKey, String peerSignatureAlgorithm) {
        this.peerId = peerId;
        this.peerAlgorithms = peerAlgorithms;
        this.peerSignaturePublicKey = peerSignaturePublicKey;
        this.peerSignatureAlgorithm = peerSignatureAlgorithm;
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }
    
    // Getters and setters
    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }
    
    public CryptoAlgorithms getPeerAlgorithms() { return peerAlgorithms; }
    public void setPeerAlgorithms(CryptoAlgorithms peerAlgorithms) { 
        this.peerAlgorithms = peerAlgorithms;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public byte[] getPeerSignaturePublicKey() { return peerSignaturePublicKey; }
    public void setPeerSignaturePublicKey(byte[] peerSignaturePublicKey) { 
        this.peerSignaturePublicKey = peerSignaturePublicKey;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getPeerSignatureAlgorithm() { return peerSignatureAlgorithm; }
    public void setPeerSignatureAlgorithm(String peerSignatureAlgorithm) { 
        this.peerSignatureAlgorithm = peerSignatureAlgorithm;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    
    /**
     * Check if peer crypto info is complete
     */
    public boolean isComplete() {
        return peerId != null && !peerId.trim().isEmpty() &&
               peerAlgorithms != null && peerAlgorithms.isValid() &&
               peerSignaturePublicKey != null && peerSignaturePublicKey.length > 0 &&
               peerSignatureAlgorithm != null && !peerSignatureAlgorithm.trim().isEmpty();
    }
    
    /**
     * Get peer signature public key as Base64 string
     */
    public String getPeerSignaturePublicKeyBase64() {
        return peerSignaturePublicKey != null ? 
               Base64.encodeToString(peerSignaturePublicKey, Base64.NO_WRAP) : null;
    }
    
    /**
     * Set peer signature public key from Base64 string
     */
    public void setPeerSignaturePublicKeyFromBase64(String base64Key) {
        if (base64Key != null && !base64Key.trim().isEmpty()) {
            this.peerSignaturePublicKey = Base64.decode(base64Key, Base64.NO_WRAP);
            this.lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * Check if peer uses compatible algorithms
     */
    public boolean isCompatibleWith(CryptoAlgorithms myAlgorithms) {
        if (peerAlgorithms == null || myAlgorithms == null) {
            return false;
        }
        
        // For simplicity, consider compatible if signature algorithms match
        // In practice, you might want more sophisticated compatibility checks
        return peerSignatureAlgorithm != null && 
               peerSignatureAlgorithm.equals(myAlgorithms.getSignatureAlgorithm());
    }
    
    @Override
    public String toString() {
        return "PeerCryptoInfo{" +
                "peerId='" + peerId + '\'' +
                ", peerAlgorithms=" + peerAlgorithms +
                ", hasSignatureKey=" + (peerSignaturePublicKey != null && peerSignaturePublicKey.length > 0) +
                ", peerSignatureAlgorithm='" + peerSignatureAlgorithm + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", verified=" + verified +
                '}';
    }
}