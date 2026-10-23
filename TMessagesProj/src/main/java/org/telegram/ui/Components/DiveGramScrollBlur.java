package org.telegram.ui.Components;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

public class DiveGramScrollBlur extends RecyclerView.OnScrollListener {

    private static final float MIN_SPEED_DP = 3.5f;
    private static final float MAX_RADIUS_DP = 7f;

    private final View target;
    private long lastTime;
    private float lastRadius;
    private final Runnable reset = () -> setRadius(0f);

    private DiveGramScrollBlur(View target) {
        this.target = target;
    }

    public static void attach(RecyclerView listView) {
        if (listView == null || SharedConfig.dgOptimization) {
            return;
        }
        listView.addOnScrollListener(new DiveGramScrollBlur(listView));
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (SharedConfig.dgOptimization) {
            setRadius(0f);
            return;
        }
        final long now = SystemClock.uptimeMillis();
        final long dt = now - lastTime;
        lastTime = now;
        if (dt <= 0 || dy == 0) {
            return;
        }
        final float speed = Math.abs(dy) / (float) dt;
        final float threshold = AndroidUtilities.dpf2(MIN_SPEED_DP);
        final float radius = speed <= threshold ? 0f : Math.min(
                AndroidUtilities.dpf2(MAX_RADIUS_DP),
                (speed - threshold) * AndroidUtilities.dpf2(1.4f)
        );
        setRadius(radius);
        target.removeCallbacks(reset);
        target.postDelayed(reset, 90);
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            setRadius(0f);
        }
    }

    private void setRadius(float radius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || target == null) {
            return;
        }
        if (Math.abs(radius - lastRadius) < 0.3f) {
            return;
        }
        lastRadius = radius;
        try {
            if (radius < 0.5f) {
                target.setRenderEffect(null);
            } else {
                target.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            }
        } catch (Throwable ignore) {
            lastRadius = 0f;
        }
    }
}
