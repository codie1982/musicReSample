package com.example.musicsample;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class CustomView extends View {
    private List<double[]> peakData;
    private Paint paint;
    private static final double THRESHOLD = 1500; // Eşik değeri

    public CustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(2);
    }

    public void setPeakData(List<double[]> peakData) {
        this.peakData = peakData;
        invalidate(); // View'i yeniden çizmek için
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (peakData == null || peakData.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        double maxFrequency = 0;
        double maxMagnitude = 0;

        // Pik verilerindeki maksimum frekans ve genlik değerlerini bul
        for (double[] peak : peakData) {
            if (peak[0] > maxFrequency) {
                maxFrequency = peak[0];
            }
            if (peak[1] > maxMagnitude) {
                maxMagnitude = peak[1];
            }
        }

        // Genlikleri zamana göre çiz
        for (int i = 0; i < peakData.size(); i++) {
            double[] peak = peakData.get(i);
            double magnitude = peak[1];

            // Eşik değerin altındaki genlikleri filtrele
            if (magnitude < THRESHOLD) {
                continue;
            }

            double time = i * (512.0 / 44100.0); // HopSize / örnekleme hızı
            float x = (float) (time * width / (peakData.size() * (512.0 / 44100.0)));
            float y = (float) (height - (magnitude / maxMagnitude) * height);

            canvas.drawLine(x, height, x, y, paint);
        }
    }
}
