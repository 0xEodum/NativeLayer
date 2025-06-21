package com.yumsg.core.session;

// ... existing imports ...
import com.yumsg.core.network.NetworkManager;
import com.yumsg.core.network.ServerNetworkManager;

/**
 * SessionManager - Updated Implementation with ServerNetworkManager Integration
 * 
 * ИЗМЕНЕНИЯ:
 * - Добавлена интеграция с NetworkManager для token refresh
 * - Исправлен refreshSession() для делегирования в ServerNetworkManager
 * - Добавлены методы для coordination с network layer
 */
public class SessionManager {
    // ... existing code ...
    
    // NEW: Network manager reference for token refresh
    private volatile NetworkManager networkManager;
    
    // ... existing constructor and methods ...
    
    /**
     * Set network manager for token refresh operations
     * This will be called from BackgroundService when it's implemented
     */
    public void setNetworkManager(NetworkManager networkManager) {
        lock.writeLock().lock();
        try {
            this.networkManager = networkManager;
            Log.d(TAG, "NetworkManager set for session refresh operations");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Refresh session token - UPDATED to use ServerNetworkManager
     */
    public boolean refreshSession() {
        checkInitialized();
        
        lock.writeLock().lock();
        try {
            if (currentSession == null) {
                Log.w(TAG, "No session to refresh");
                return false;
            }
            
            if (currentSession.isRefreshTokenExpired()) {
                Log.w(TAG, "Refresh token expired - cannot refresh session");
                destroySession();
                return false;
            }
            
            Log.d(TAG, "Session refresh requested for: " + currentSession.getUsername());
            
            // Check if we have ServerNetworkManager for actual token refresh
            if (networkManager instanceof ServerNetworkManager) {
                ServerNetworkManager serverManager = (ServerNetworkManager) networkManager;
                
                try {
                    // Call server to refresh token
                    CompletableFuture<SessionAuthData> refreshFuture = serverManager.refreshSessionToken(
                        currentSession.getRefreshToken());
                    
                    // Wait for refresh result (blocking call - consider making async)
                    SessionAuthData refreshedSession = refreshFuture.get(10, TimeUnit.SECONDS);
                    
                    if (refreshedSession != null) {
                        // Update current session with new tokens
                        currentSession.updateTokens(
                            refreshedSession.getAccessToken(),
                            refreshedSession.getRefreshToken(),
                            refreshedSession.getExpiresAt());
                        
                        // Save updated session to storage
                        saveSessionToStorage();
                        
                        Log.i(TAG, "Session refreshed successfully");
                        return true;
                    } else {
                        Log.w(TAG, "Session refresh failed - received null response");
                        return false;
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Session refresh failed", e);
                    return false;
                }
                
            } else {
                Log.w(TAG, "No ServerNetworkManager available for session refresh");
                
                // Fallback: extend session locally (as before) - only for development
                if (currentSession.needsRefresh()) {
                    long newExpiresAt = System.currentTimeMillis() + DEFAULT_SESSION_TIMEOUT;
                    currentSession.updateAccessToken(currentSession.getAccessToken(), newExpiresAt);
                    saveSessionToStorage();
                    
                    Log.d(TAG, "Session extended locally (fallback mode)");
                    return true;
                }
                
                return false;
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if session refresh is available
     */
    public boolean canRefreshSession() {
        lock.readLock().lock();
        try {
            return currentSession != null && 
                   !currentSession.isRefreshTokenExpired() &&
                   networkManager instanceof ServerNetworkManager;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get current access token for network requests
     */
    public String getCurrentAccessToken() {
        lock.readLock().lock();
        try {
            return currentSession != null ? currentSession.getAccessToken() : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if access token needs refresh
     */
    public boolean needsTokenRefresh() {
        lock.readLock().lock();
        try {
            return currentSession != null && currentSession.needsRefresh();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Handle token expiration notification from network layer
     */
    public void onTokenExpired() {
        Log.w(TAG, "Token expired notification received");
        
        // Attempt automatic refresh if possible
        if (canRefreshSession()) {
            Log.d(TAG, "Attempting automatic token refresh");
            if (!refreshSession()) {
                Log.w(TAG, "Automatic token refresh failed, session will be destroyed");
                destroySession();
            }
        } else {
            Log.w(TAG, "Cannot refresh token, destroying session");
            destroySession();
        }
    }
    
    // ... rest of existing methods remain unchanged ...
}