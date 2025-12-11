package com.example.vcam;

/**
 * Enum to define the media mode for fake camera.
 * The mode is automatically detected based on files in the Camera1 folder.
 */
public enum MediaMode {
    /**
     * Video mode - uses virtual.mp4 for video replacement
     */
    VIDEO("VIDEO"),
    
    /**
     * Image mode - uses static images (virtual.jpg, virtual.png, virtual.bmp) for camera replacement
     * Similar to FakeCameraHook approach from app cloner
     */
    IMAGE("IMAGE"),
    
    /**
     * No media found - neither video nor image exists
     */
    NONE("NONE");
    
    private final String friendlyName;
    
    MediaMode(String friendlyName) {
        this.friendlyName = friendlyName;
    }
    
    @Override
    public String toString() {
        return friendlyName;
    }
}
