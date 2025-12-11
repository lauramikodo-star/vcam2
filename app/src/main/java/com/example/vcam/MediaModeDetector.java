package com.example.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;

import de.robv.android.xposed.XposedBridge;

/**
 * Utility class to detect media mode (video or image) based on files in the Camera1 folder.
 * If image files exist (virtual.jpg, virtual.png, virtual.bmp), image mode is used.
 * If video file exists (virtual.mp4), video mode is used.
 * Image mode takes priority over video mode if both exist.
 */
public class MediaModeDetector {
    private static final String TAG = "MediaModeDetector";
    
    // Supported image extensions
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"};
    
    // Video file name
    private static final String VIDEO_FILE = "virtual.mp4";
    
    // Image base name
    private static final String IMAGE_BASE_NAME = "virtual";
    
    // Cached fake bitmap for image mode
    private static Bitmap sFakeBitmap;
    private static byte[] sFakeJpegData;
    private static String sCurrentImagePath;
    
    // Current detected mode
    private static MediaMode sCurrentMode = MediaMode.NONE;
    
    /**
     * Detect the media mode based on files in the given path.
     * Image mode takes priority over video mode.
     * 
     * @param basePath The base path to search for media files
     * @return The detected MediaMode
     */
    public static MediaMode detectMode(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            return MediaMode.NONE;
        }
        
        // First check for image files (image mode takes priority)
        String imagePath = findImageFile(basePath);
        if (imagePath != null) {
            sCurrentMode = MediaMode.IMAGE;
            loadFakeImage(imagePath);
            XposedBridge.log("【VCAM】Detected IMAGE mode with file: " + imagePath);
            return MediaMode.IMAGE;
        }
        
        // Then check for video file
        File videoFile = new File(basePath + VIDEO_FILE);
        if (videoFile.exists()) {
            sCurrentMode = MediaMode.VIDEO;
            XposedBridge.log("【VCAM】Detected VIDEO mode with file: " + videoFile.getAbsolutePath());
            return MediaMode.VIDEO;
        }
        
        sCurrentMode = MediaMode.NONE;
        XposedBridge.log("【VCAM】No media file found in: " + basePath);
        return MediaMode.NONE;
    }
    
    /**
     * Find an image file in the given path.
     * 
     * @param basePath The base path to search
     * @return The full path to the image file, or null if not found
     */
    public static String findImageFile(String basePath) {
        for (String ext : IMAGE_EXTENSIONS) {
            File imageFile = new File(basePath + IMAGE_BASE_NAME + ext);
            if (imageFile.exists()) {
                return imageFile.getAbsolutePath();
            }
        }
        
        // Also check for numbered images like 1000.bmp (for compatibility)
        File numberedBmp = new File(basePath + "1000.bmp");
        if (numberedBmp.exists()) {
            return numberedBmp.getAbsolutePath();
        }
        
        return null;
    }
    
    /**
     * Load a fake image from the given path.
     * 
     * @param imagePath The path to the image file
     */
    public static void loadFakeImage(String imagePath) {
        try {
            if (imagePath.equals(sCurrentImagePath) && sFakeBitmap != null) {
                // Already loaded
                return;
            }
            
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap != null) {
                sFakeBitmap = bitmap;
                sFakeJpegData = bitmapToJpeg(bitmap);
                sCurrentImagePath = imagePath;
                XposedBridge.log("【VCAM】Loaded fake image: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                XposedBridge.log("【VCAM】Failed to decode image: " + imagePath);
            }
        } catch (Exception e) {
            XposedBridge.log("【VCAM】Error loading fake image: " + e.toString());
        }
    }
    
    /**
     * Get the current fake bitmap for image mode.
     * 
     * @return The fake bitmap, or null if not loaded
     */
    public static Bitmap getFakeBitmap() {
        return sFakeBitmap;
    }
    
    /**
     * Set the fake bitmap directly (for runtime updates).
     * 
     * @param bitmap The new fake bitmap
     */
    public static void setFakeBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            sFakeBitmap = bitmap;
            sFakeJpegData = bitmapToJpeg(bitmap);
            sCurrentImagePath = null; // Clear path since this is a runtime update
            sCurrentMode = MediaMode.IMAGE;
            XposedBridge.log("【VCAM】Set fake bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        }
    }
    
    /**
     * Get the fake JPEG data for image mode.
     * 
     * @return The JPEG byte array, or null if not loaded
     */
    public static byte[] getFakeJpegData() {
        return sFakeJpegData;
    }
    
    /**
     * Get the current media mode.
     * 
     * @return The current MediaMode
     */
    public static MediaMode getCurrentMode() {
        return sCurrentMode;
    }
    
    /**
     * Check if video mode is active.
     * 
     * @return true if video mode is active
     */
    public static boolean isVideoMode() {
        return sCurrentMode == MediaMode.VIDEO;
    }
    
    /**
     * Check if image mode is active.
     * 
     * @return true if image mode is active
     */
    public static boolean isImageMode() {
        return sCurrentMode == MediaMode.IMAGE;
    }
    
    /**
     * Convert bitmap to JPEG byte array.
     * 
     * @param bitmap The bitmap to convert
     * @return The JPEG byte array
     */
    public static byte[] bitmapToJpeg(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
        return stream.toByteArray();
    }
    
    /**
     * Resize bitmap to target dimensions while preserving aspect ratio.
     * 
     * @param bitmap The source bitmap
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @param fillImage If true, fill the target (crop edges). If false, fit inside (black bars).
     * @return The resized bitmap
     */
    public static Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight, boolean fillImage) {
        if (bitmap == null || targetWidth <= 0 || targetHeight <= 0) {
            return createFallbackImage(targetWidth, targetHeight);
        }
        
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        
        Bitmap resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        
        float scaleX = (float) targetWidth / bitmap.getWidth();
        float scaleY = (float) targetHeight / bitmap.getHeight();
        
        // If fillImage is true, use max scale to fill (cropping edges)
        // Otherwise, use min scale to fit (showing black bars)
        float scale = fillImage ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
        
        float scaledWidth = scale * bitmap.getWidth();
        float scaledHeight = scale * bitmap.getHeight();
        
        float left = (targetWidth - scaledWidth) / 2;
        float top = (targetHeight - scaledHeight) / 2;
        
        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, null, targetRect, paint);
        
        return resultBitmap;
    }
    
    /**
     * Create a fallback image when no image is available.
     * 
     * @param width Image width
     * @param height Image height
     * @return A fallback bitmap
     */
    public static Bitmap createFallbackImage(int width, int height) {
        if (width <= 0) width = 640;
        if (height <= 0) height = 480;
        
        Bitmap fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fallback);
        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("No Image", width / 2f, height / 2f, paint);
        return fallback;
    }
    
    /**
     * Convert bitmap to NV21 format (YUV).
     * 
     * @param bitmap The source bitmap
     * @return NV21 byte array
     */
    public static byte[] bitmapToNV21(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return rgb2YCbCr420(pixels, width, height);
    }
    
    /**
     * Convert RGB pixels to YCbCr420 (NV21) format.
     */
    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = (pixels[i * width + j]) & 0x00FFFFFF;
                int r = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb >> 16) & 0xFF;
                // YUV conversion formula
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                y = y < 16 ? 16 : (Math.min(y, 255));
                u = u < 0 ? 0 : (Math.min(u, 255));
                v = v < 0 ? 0 : (Math.min(v, 255));
                // Assign values
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + (i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }
    
    /**
     * Rotate bitmap by specified degrees.
     * 
     * @param bitmap The source bitmap
     * @param degrees Rotation angle in degrees
     * @return The rotated bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
        if (bitmap == null) return null;
        
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    
    /**
     * Flip bitmap horizontally.
     * 
     * @param bitmap The source bitmap
     * @return The flipped bitmap
     */
    public static Bitmap flipBitmapHorizontally(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    
    /**
     * Refresh the media mode detection.
     * 
     * @param basePath The base path to search
     * @return The newly detected MediaMode
     */
    public static MediaMode refresh(String basePath) {
        // Clear cached data
        sFakeBitmap = null;
        sFakeJpegData = null;
        sCurrentImagePath = null;
        sCurrentMode = MediaMode.NONE;
        
        // Re-detect
        return detectMode(basePath);
    }
}
