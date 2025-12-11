package com.example.vcam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Surface;

import de.robv.android.xposed.XposedBridge;

public class ImageSurfaceRenderer implements Runnable {
    private final Surface surface;
    private final Bitmap bitmap;
    private boolean running = false;
    private Thread thread;
    private final boolean fillImage;

    public ImageSurfaceRenderer(Surface surface, Bitmap bitmap, boolean fillImage) {
        this.surface = surface;
        this.bitmap = bitmap;
        this.fillImage = fillImage;
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
    }

    @Override
    public void run() {
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
                 // Surface might be invalid or not lockable
                 // XposedBridge.log("【VCAM】ImageSurfaceRenderer error: " + e.getMessage());
                 // If we can't lock canvas, we can't draw. Exit loop?
                 // It might be temporary, or permanent.
                 // For now, sleep and retry.
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
