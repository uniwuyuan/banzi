package com.claw.ime.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 隐匿模式状态提示：⌄ 撤出键上的暗紫呼吸（见 docs/PRIVACY-SPEC.md §3）。
 *
 *  - 底色在 #2A1B3D（暗紫）与 #4B2A6B 之间按余弦（正弦相位）缓慢呼吸；
 *  - L1：周期 ≈2.4s，峰值 alpha ≈0.55；
 *  - L2：呼吸更明显：周期 ≈1.8s，峰值 alpha ≈0.9；
 *  - 无边线、不额外加光晕（就是一层垫在键文字下面的半透明紫）。
 *
 * 级别 0 时调用 {@link #stop()}，由服务恢复原键面。级别变化调 {@link #setPrivacyLevel(int)}
 * 立即生效（无需重建键盘）。ValueAnimator 只在可见时跑，随 Drawable 挂载生命周期。
 */
public class PrivacyBreathDrawable extends Drawable implements ValueAnimator.AnimatorUpdateListener {

    /** 呼吸低谷色（暗紫）。 */
    private static final int DARK = 0xFF2A1B3D;
    /** 呼吸峰值色（更亮的暗紫）。 */
    private static final int LIGHT = 0xFF4B2A6B;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float cornerRadius;

    /** 0..1 呼吸相位（0=最淡，1=最亮）；动画在两条腿之间往返。 */
    private float t = 0f;
    /** 1=L1 / 2=L2；0=不显示。 */
    private int level = 0;
    private ValueAnimator anim;

    /** @param cornerRadiusPx 圆角半径（px），与键面圆角一致。 */
    public PrivacyBreathDrawable(float cornerRadiusPx) {
        this.cornerRadius = Math.max(0f, cornerRadiusPx);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 当前级别（0/1/2）。 */
    public int level() { return level; }

    /** 是否正在呼吸。 */
    public boolean isRunning() { return anim != null && anim.isRunning(); }

    /**
     * 设置级别并立即生效：0 → 停并清空；1/2 → 按各自周期/峰值重新起呼吸。
     * 可重复调用（幂等）；running 时换级别会按新参数重起。
     */
    public void setPrivacyLevel(int level) {
        int lv = level < 0 ? 0 : (level > 2 ? 2 : level);
        if (lv == this.level && (lv == 0 ? anim == null : isRunning())) return;
        this.level = lv;
        if (lv <= 0) {
            stop();
            return;
        }
        restart();
    }

    /** 开始呼吸（级别为 0 时什么都不做）。 */
    public void start() {
        if (level <= 0 || isRunning()) return;
        restart();
    }

    /** 停止呼吸并画回全透明（键面由服务恢复）。 */
    public void stop() {
        if (anim != null) {
            anim.removeUpdateListener(this);
            anim.cancel();
            anim = null;
        }
        t = 0f;
        invalidateSelf();
    }

    private void restart() {
        if (anim != null) {
            anim.removeUpdateListener(this);
            anim.cancel();
        }
        t = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        // 一级腿 = 半个周期：0→1 再 1→0，合起来就是一个完整呼吸周期。
        anim.setDuration(periodMs() / 2L);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(this);
        try { anim.start(); } catch (Throwable ignore) { }
        invalidateSelf();
    }

    private long periodMs() { return level >= 2 ? 1800L : 2400L; }

    private float peakAlpha() { return level >= 2 ? 0.9f : 0.55f; }

    @Override public void onAnimationUpdate(ValueAnimator animation) {
        Object v = animation.getAnimatedValue();
        t = v instanceof Float ? ((Float) v).floatValue() : 0f;
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        if (level <= 0 || t <= 0.001f) return;      // 0 相位 = 完全透明（省一次绘制）
        // 正弦相位：breath = 0.5 - 0.5·cos(π·t)，t=0→0，t=1→1（再 REVERSE 回去）。
        float breath = (float) (0.5d - 0.5d * Math.cos(Math.PI * t));
        int color = blend(DARK, LIGHT, breath);
        int alpha = Math.round(255f * peakAlpha() * breath);
        paint.setColor((color & 0x00FFFFFF) | ((alpha & 0xFF) << 24));
        rect.set(getBounds());
        if (cornerRadius > 0f) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        else canvas.drawRect(rect, paint);
    }

    /** 两色线性插值（保持不透明，alpha 由外部乘）。 */
    private static int blend(int a, int b, float k) {
        if (k <= 0f) return a;
        if (k >= 1f) return b;
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }

    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
