package com.example.vcam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageWriter;
import android.os.Build;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import de.robv.android.xposed.XposedBridge;

public class ImageSurfaceRenderer implements Runnable {
    private final Surface surface;
    private final Bitmap bitmap;
    private boolean running = false;
    private Thread thread;
    private final boolean fillImage;
    private final boolean useImageWriter;
    private final int targetWidth;
    private final int targetHeight;
    private ImageWriter imageWriter;
    private byte[] cachedJpegData;
    private Bitmap scaledBitmap;

    /**
     * Standard constructor for preview surfaces that support lockCanvas
     */
    public ImageSurfaceRenderer(Surface surface, Bitmap bitmap, boolean fillImage) {
        this(surface, bitmap, fillImage, false, 0, 0);
    }

    /**
     * Extended constructor for ImageReader surfaces that may require ImageWriter
     * 
     * @param surface The target surface
     * @param bitmap The source bitmap to render
     * @param fillImage Whether to fill (crop) or fit (letterbox) the image
     * @param useImageWriter Whether to use ImageWriter for JPEG format surfaces
     * @param targetWidth Target width for JPEG encoding
     * @param targetHeight Target height for JPEG encoding
     */
    public ImageSurfaceRenderer(Surface surface, Bitmap bitmap, boolean fillImage, 
                                 boolean useImageWriter, int targetWidth, int targetHeight) {
        this.surface = surface;
        this.bitmap = bitmap;
        this.fillImage = fillImage;
        this.useImageWriter = useImageWriter;
        this.targetWidth = targetWidth > 0 ? targetWidth : (bitmap != null ? bitmap.getWidth() : 640);
        this.targetHeight = targetHeight > 0 ? targetHeight : (bitmap != null ? bitmap.getHeight() : 480);
        
        // Pre-scale and encode bitmap for JPEG mode
        if (useImageWriter && bitmap != null) {
            prepareJpegData();
        }
    }

    /**
     * Prepare JPEG data by scaling bitmap to target dimensions and encoding
     */
    private void prepareJpegData() {
        try {
            // Scale bitmap to target dimensions
            scaledBitmap = MediaModeDetector.resizeBitmap(bitmap, targetWidth, targetHeight, fillImage);
            
            // Encode to JPEG
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
            cachedJpegData = stream.toByteArray();
            
            XposedBridge.log("【VCAM】ImageSurfaceRenderer prepared JPEG data: " + 
                            targetWidth + "x" + targetHeight + ", size: " + cachedJpegData.length);
        } catch (Exception e) {
            XposedBridge.log("【VCAM】ImageSurfaceRenderer prepareJpegData error: " + e.toString());
        }
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join(500);
            } catch (InterruptedException e) {
                // Ignore
            }
            thread = null;
        }
        
        // Clean up ImageWriter
        if (imageWriter != null) {
            try {
                imageWriter.close();
            } catch (Exception e) {
                // Ignore
            }
            imageWriter = null;
        }
    }

    @Override
    public void run() {
        if (useImageWriter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runWithImageWriter();
        } else {
            runWithLockCanvas();
        }
    }

    /**
     * Render using ImageWriter for JPEG format ImageReader surfaces
     * This is required for surfaces that don't support lockCanvas (like JPEG ImageReader)
     */
    private void runWithImageWriter() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            XposedBridge.log("【VCAM】ImageWriter requires API 23+, falling back to lockCanvas");
            runWithLockCanvas();
            return;
        }

        try {
            // Create ImageWriter for JPEG format
            imageWriter = ImageWriter.newInstance(surface, 2, ImageFormat.JPEG);
            XposedBridge.log("【VCAM】ImageWriter created for JPEG surface");
        } catch (Exception e) {
            XposedBridge.log("【VCAM】Failed to create ImageWriter: " + e.toString());
            // Fall back to lockCanvas method
            runWithLockCanvas();
            return;
        }

        while (running && surface != null && surface.isValid()) {
            try {
                if (cachedJpegData != null && imageWriter != null) {
                    // Dequeue an image from the ImageWriter
                    Image image = imageWriter.dequeueInputImage();
                    if (image != null) {
                        try {
                            // Get the JPEG plane
                            Image.Plane[] planes = image.getPlanes();
                            if (planes != null && planes.length > 0) {
                                ByteBuffer buffer = planes[0].getBuffer();
                                buffer.clear();
                                buffer.put(cachedJpegData);
                            }
                            
                            // Queue the image to be written to the surface
                            imageWriter.queueInputImage(image);
                        } catch (Exception e) {
                            XposedBridge.log("【VCAM】ImageWriter queue error: " + e.toString());
                            image.close();
                        }
                    }
                }
            } catch (IllegalStateException e) {
                // ImageWriter might be closed or surface invalid
                XposedBridge.log("【VCAM】ImageWriter state error: " + e.getMessage());
                break;
            } catch (Exception e) {
                XposedBridge.log("【VCAM】ImageWriter error: " + e.toString());
            }

            try {
                // Lower frame rate for capture surfaces
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Render using traditional lockCanvas method for preview surfaces
     */
    private void runWithLockCanvas() {
        while (running && surface != null && surface.isValid()) {
            Canvas canvas = null;
            try {
                // Note: lockCanvas might fail on some surfaces (e.g. from MediaCodec or ImageReader on some APIs)
                canvas = surface.lockCanvas(null);
                if (canvas != null && bitmap != null && !bitmap.isRecycled()) {
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();

                    // Clear canvas
                    canvas.drawColor(Color.BLACK);

                    // Calculate scaling
                    float scaleX = (float) width / bitmap.getWidth();
                    float scaleY = (float) height / bitmap.getHeight();
                    float scale = fillImage ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);

                    float scaledWidth = scale * bitmap.getWidth();
                    float scaledHeight = scale * bitmap.getHeight();

                    float left = (width - scaledWidth) / 2;
                    float top = (height - scaledHeight) / 2;

                    Rect destRect = new Rect((int)left, (int)top, (int)(left + scaledWidth), (int)(top + scaledHeight));

                    Paint paint = new Paint();
                    paint.setFilterBitmap(true);
                    canvas.drawBitmap(bitmap, null, destRect, paint);
                }
            } catch (IllegalArgumentException e) {
                // Surface might be invalid or not lockable (common for ImageReader surfaces)
                // For ImageReader surfaces, we need to use ImageWriter instead
                if (useImageWriter) {
                    XposedBridge.log("【VCAM】lockCanvas failed for ImageReader surface, but ImageWriter mode not available");
                }
                // Sleep and continue - the surface might become available later
            } catch (Exception e) {
                XposedBridge.log("【VCAM】ImageSurfaceRenderer error: " + e.toString());
            } finally {
                if (canvas != null && surface != null) {
                    try {
                        surface.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        XposedBridge.log("【VCAM】ImageSurfaceRenderer unlock error: " + e.toString());
                    }
                }
            }

            try {
                // 30 FPS
                Thread.sleep(33);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
