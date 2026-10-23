package org.telegram.ui.Components;

import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.SharedConfig;

public final class DiveGramJelly {

    private static final float PRESSED_SCALE = 0.88f;

    private DiveGramJelly() {
    }

    public static void attach(View view) {
        if (view == null) {
            return;
        }
        final SpringAnimation scaleX = new SpringAnimation(view, DynamicAnimation.SCALE_X);
        final SpringAnimation scaleY = new SpringAnimation(view, DynamicAnimation.SCALE_Y);
        scaleX.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        scaleY.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        view.setOnTouchListener((v, event) -> {
            if (!SharedConfig.dgJellyAvatars) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    animate(scaleX, scaleY, PRESSED_SCALE, 1f, 650f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animate(scaleX, scaleY, 1f, 0.38f, 480f);
                    break;
            }
            return false;
        });
    }

    private static void animate(SpringAnimation scaleX, SpringAnimation scaleY, float target, float damping, float stiffness) {
        scaleX.setSpring(new SpringForce(target).setDampingRatio(damping).setStiffness(stiffness));
        scaleY.setSpring(new SpringForce(target).setDampingRatio(damping).setStiffness(stiffness));
        scaleX.animateToFinalPosition(target);
        scaleY.animateToFinalPosition(target);
    }
}
