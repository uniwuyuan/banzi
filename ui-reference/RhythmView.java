package com.claw.ime.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.claw.ime.core.RhythmBus;

/**
 * 候选栏空闲时的「律动」视觉 v2：一排会蹦的频谱条。
 *
 * <p>三路能量叠加：</p>
 * <ul>
 *   <li>系统音频频谱（{@link RhythmBus} 快照，采集服务没跑时为零）</li>
 *   <li><b>打字节拍</b>：每次按键（Haptic.tap）发一道从左/右穿过去的涟漪，
 *       松手后还有回弹过冲——不打字也有波在走，不是干等呼吸</li>
 *   <li>待机双正弦：两条速度不同的行波，颜色在青→紫之间漂</li>
 * </ul>
 *
 * <p>条子用弹簧积分（有速度、会过冲）而不是指数平滑，视觉上「弹」起来；
 * 峰值时叠外发光与一条白芯。自身不采集任何东西，只读总线。</p>
 */
public class RhythmView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private static final int MAX_BARS = 64;
    private static final int WAVES = 4;

    private final float[] target = new float[RhythmBus.BANDS];
    private final float[] shown = new float[MAX_BARS + 2];
    private final float[] vel = new float[MAX_BARS + 2];
    private float levelTarget, levelShown;

    // 敲击涟漪：环缓冲，每次按键一道波
    private final long[] waveT0 = new long[WAVES];
    private final int[] waveDir = new int[WAVES];
    private int waveIdx = 0;
    private long seenTapSeq = -1L;

    private boolean feature = true;     // 设置里的「律动」开关
    private boolean active = false;      // 当前是否应该显示（候选栏为空）
    private boolean loop = false;
    private boolean audioOn = false;   // 有音频数据时才画「动起来的条」，否则一排点
    private long lastFrameAt = 0L;
    private long t0 = SystemClock.uptimeMillis();

    public RhythmView(Context c) {
        super(c);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setAlpha(0f);
        glow.setStyle(Paint.Style.FILL);
        core.setStyle(Paint.Style.FILL);
    }

    /** 设置开关变化：关掉时立刻收干净，不再画。 */
    public void setFeature(boolean on) {
        if (feature == on) return;
        feature = on;
        if (!on) {
            animate().cancel();
            setAlpha(0f);
            loop = false;
            invalidate();
        } else {
            setActive(active);
        }
    }

    /** 候选栏空 → true（淡入）；有候选/提示 → false（淡出）。 */
    public void setActive(boolean on) {
        boolean want = on && feature;
        if (active == want) {
            if (want) startLoop();
            return;
        }
        active = want;
        animate().cancel();
        animate().alpha(want ? 1f : 0f)
                .setDuration(want ? 260L : 200L)
                .setInterpolator(want ? new DecelerateInterpolator(1.3f)
                        : new AccelerateInterpolator())
                .start();
        if (want) startLoop();
    }

    private void startLoop() {
        if (loop || !isAttachedToWindow()) return;
        loop = true;
        lastFrameAt = 0L;
        postOnAnimation(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isAttachedToWindow()) { loop = false; return; }
            long now = SystemClock.uptimeMillis();
            pull(now);
            invalidate();
            boolean keep = active || getAlpha() > 0.01f;
            if (keep) {
                // 有音频：45fps 正常 / 25fps 降级；没音频：只是一排静态点，低频轮询即可。
                long frame = !audioOn ? 160L : (PerfGuard.degraded() ? 40L : 22L);
                long wait = Math.max(0L, frame - (SystemClock.uptimeMillis() - now));
                if (wait <= 0L) postOnAnimation(this);
                else postDelayed(this, wait);
            } else {
                loop = false;
            }
        }
    };

    private void pull(long now) {
        RhythmBus.Frame f = RhythmBus.frame;
        boolean fresh = RhythmBus.running
                && now - RhythmBus.updatedAt < 700L;
        audioOn = fresh;
        levelTarget = fresh ? f.level : 0f;
        for (int i = 0; i < target.length; i++) {
            target[i] = fresh && i < f.bands.length ? f.bands[i] : 0f;
        }
        if (!active) {
            levelTarget = 0f;
            java.util.Arrays.fill(target, 0f);
            return;
        }
        // 新的敲击 → 发一道涟漪（左右交替，看着像有人在敲键盘）
        long seq = RhythmBus.tapSeq;
        if (seenTapSeq < 0) seenTapSeq = seq;
        if (seq != seenTapSeq) {
            int n = (int) Math.min(WAVES, seq - seenTapSeq);
            for (int k = 0; k < n; k++) {
                waveT0[waveIdx] = now;
                waveDir[waveIdx] = (waveIdx % 2 == 0) ? 1 : -1;
                waveIdx = (waveIdx + 1) % WAVES;
            }
            seenTapSeq = seq;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active && feature) {
            animate().cancel();
            setAlpha(1f);          // 首次挂载直接显示，淡入交给后续 setActive
            startLoop();
        }
    }

    @Override protected void onDetachedFromWindow() {
        loop = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** 没有音频时的待机形态：一排等距圆点（青→紫渐变），静态不跳。 */
    private void drawDots(Canvas canvas, float w, float h) {
        final float d = getResources().getDisplayMetrics().density;
        final float cy = h * 0.5f;
        final float radius = Math.max(1.5f * d, 1.9f * d);
        final float step = Math.max(radius * 3.1f, 4.6f * d);
        int n = (int) (w / step);
        if (n < 5) n = 5;
        if (n > 72) n = 72;
        final float startX = (w - (n - 1) * step) * 0.5f;
        final float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        bar.setColor(Theme.CYAN);
        bar.setAlpha(120);
        for (int i = 0; i < n; i++) {
            float f = n <= 1 ? 0f : i / (float) (n - 1);
            int color = mix(Theme.CYAN, Theme.PURPLE, 0.15f + 0.7f * f);
            bar.setColor(color);
            // 极轻的亮度呼吸：只变 alpha，不动位置、不动大小
            int a = (int) (112f + 26f * (float) Math.sin(t * 1.1f + i * 0.35f));
            bar.setAlpha(a);
            canvas.drawCircle(startX + i * step, cy, radius, bar);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        final float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;

        long now = SystemClock.uptimeMillis();
        if (!audioOn) {                     // 没有音乐：一排点，不跳
            drawDots(canvas, w, h);
            lastFrameAt = now;
            return;
        }
        float dt = lastFrameAt == 0L ? 16f : Math.min(60f, now - lastFrameAt);
        lastFrameAt = now;
        float dts = dt / 1000f;

        // level 也用弹簧，响度变化时条子整体「顶」一下
        float kl = 1f - (float) Math.exp(-dt / 70f);
        levelShown += (levelTarget - levelShown) * kl;

        final float d = getResources().getDisplayMetrics().density;
        final float cy = h * 0.5f;
        final float maxHalf = Math.max(7f * d, h * 0.48f);
        final float minHalf = 1.6f * d;
        // 条子加粗：2.4dp → 3dp，间距收一点，视觉更「满」
        final float barW = Math.max(2.6f * d, 3.0f * d);
        final float gapMin = 2.6f * d;

        int n = (int) (w / (barW + gapMin));
        if (n < 6) n = 6;
        int cap = PerfGuard.degraded() ? 28 : MAX_BARS;
        if (n > cap) n = cap;
        final float gap = (w - n * barW) / (n + 1f);

        final float t = (now - t0) / 1000f;
        final long tapAge = RhythmBus.tapAt > 0 ? now - RhythmBus.tapAt : 99999L;
        // 敲击余韵：260ms 内整体亮度顶起来
        final float beat = tapAge < 900L ? (float) Math.exp(-tapAge / 260.0) : 0f;

        for (int i = 0; i < n; i++) {
            // 音频频段（相邻取大，避免细条跳空）
            int b = i * RhythmBus.BANDS / n;
            float e = target[b];
            if (b + 1 < target.length) e = Math.max(e, target[b + 1] * 0.6f);
            e = Math.max(e, levelShown * 0.55f);

            // 待机双正弦行波（比旧版幅度大 3 倍多）
            // 有音频但这一拍很轻：留一点底，让条子不是突然贴线
            float idle = 0.14f
                    + 0.060f * (float) Math.sin(t * 1.35f + i * 0.55f);
            if (idle < 0.06f) idle = 0.06f;

            // 敲击涟漪：一道波从一侧扫过去
            float wave = 0f;
            float x = gap + i * (barW + gap);
            for (int k = 0; k < WAVES; k++) {
                long age = now - waveT0[k];
                if (waveT0[k] == 0L || age < 0 || age > 700L) continue;
                float life = 1f - age / 700f;
                float pos = waveDir[k] > 0
                        ? (age / 700f) * (w + 240f) - 120f
                        : w - ((age / 700f) * (w + 240f) - 120f);
                float dist = Math.abs(x - pos);
                if (dist < 150f) {
                    float bump = (float) Math.exp(-(dist * dist) / (2 * 95f * 95f));
                    wave = Math.max(wave, life * life * bump * 1.25f);
                }
            }
            // 敲击整体鼓一下
            wave = Math.max(wave, beat * 0.60f * (0.7f + 0.3f * (float) Math.sin(i * 1.7f + t * 8f)));

            float vTarget = Math.max(e * 1.45f, wave * 1.35f);
            vTarget = Math.max(vTarget, idle);
            if (vTarget > 1f) vTarget = 1f;

            // 弹簧：v 有速度、会过冲（旧版指数平滑永远「软塌塌」）
            float k = 300f;      // 刚度
            float damp = 21f;    // 阻尼
            float acc = (vTarget - shown[i]) * k - vel[i] * damp;
            vel[i] += acc * dts;
            shown[i] += vel[i] * dts;
            if (shown[i] < 0f) { shown[i] = 0f; vel[i] = 0f; }
            if (shown[i] > 1.35f) { shown[i] = 1.35f; vel[i] = 0f; }

            float v = shown[i];
            if (v <= 0.005f) continue;

            float half = minHalf + (maxHalf - minHalf) * (float) Math.pow(Math.min(1f, v), 0.55f);
            // 颜色：青 → 紫，加一点时间漂移，峰值偏白
            float mixf = n <= 1 ? 0f : (i / (float) (n - 1)) * 0.85f
                    + 0.10f * (float) Math.sin(t * 0.9f + i * 0.07f);
            if (mixf < 0f) mixf = 0f;
            if (mixf > 1f) mixf = 1f;
            int color = mix(Theme.CYAN, Theme.PURPLE, mixf);
            int alpha = (int) (80f + 175f * Math.min(1f, v));

            // 外发光：峰值才有，两层，宽一圈
            if (v >= 0.16f) {
                glow.setColor(color);
                glow.setAlpha((int) (alpha * 0.16f));
                r.set(x - barW * 1.7f, cy - half - 2.2f * d,
                        x + barW + barW * 1.7f, cy + half + 2.2f * d);
                canvas.drawRoundRect(r, barW, barW, glow);
                glow.setAlpha((int) (alpha * 0.30f));
                r.set(x - barW * 0.55f, cy - half - 0.8f * d,
                        x + barW + barW * 0.55f, cy + half + 0.8f * d);
                canvas.drawRoundRect(r, barW * 0.7f, barW * 0.7f, glow);
            }

            bar.setColor(color);
            bar.setAlpha(alpha);
            r.set(x, cy - half, x + barW, cy + half);
            canvas.drawRoundRect(r, barW * 0.5f, barW * 0.5f, bar);

            // 白芯：强信号时中间透一条亮芯，像真在发光
            if (v >= 0.55f) {
                core.setColor(0xFFFFFFFF);
                core.setAlpha((int) (110f * (v - 0.55f) / 0.8f));
                float cw = Math.max(1.2f * d, barW * 0.34f);
                r.set(x + (barW - cw) * 0.5f, cy - half * 0.82f,
                        x + (barW + cw) * 0.5f, cy + half * 0.82f);
                canvas.drawRoundRect(r, cw * 0.5f, cw * 0.5f, core);
            }
        }
    }

    private static int mix(int a, int b, float f) {
        if (f <= 0f) return a;
        if (f >= 1f) return b;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * f);
        int rg = (int) (ag + (bg - ag) * f);
        int rb = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }
}
