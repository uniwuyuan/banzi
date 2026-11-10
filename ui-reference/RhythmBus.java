package com.claw.ime.core;

/**
 * 「律动」数据总线：系统音频采集服务写入，键盘里的 RhythmView 每帧只读。
 *
 * <p>只传一个不可变快照对象，读侧不做加锁，也不会读到写一半的数组。
 * 采集服务没跑时 frame 恒为全零，视图自然退化成待机呼吸。</p>
 */
public final class RhythmBus {

    /** 频段数量（与服务端、视图端保持一致）。 */
    public static final int BANDS = 20;

    /** 不可变快照。level = 0..1 响度，bands = 0..1 各频段能量。 */
    public static final class Frame {
        public final float level;
        public final float[] bands;
        Frame(float level, float[] bands) {
            this.level = level;
            this.bands = bands;
        }
    }

    public static final Frame SILENT = new Frame(0f, new float[BANDS]);

    /** 采集服务是否在跑（有 MediaProjection 授权并成功开录）。 */
    public static volatile boolean running = false;

    /** 最近一次写入的时间（uptimeMillis）；视图用它判断数据是否过期。 */
    public static volatile long updatedAt = 0L;

    /** 当前快照。 */
    public static volatile Frame frame = SILENT;

    // ---------------- 打字节拍（没有音频采集时也能律动） ----------------
    /** 最近一次按键时间（uptimeMillis），RhythmView 用来做「敲击涟漪」。 */
    public static volatile long tapAt = 0L;
    /** 按键计数：视图靠它发现新的一次敲击。 */
    public static volatile long tapSeq = 0L;

    /** 键盘按键按下时调用：只记时间与序号，不震动、不采集。 */
    public static void tap() {
        tapAt = android.os.SystemClock.uptimeMillis();
        tapSeq++;
    }


    public static void publish(float level, float[] bands) {
        float lv = level;
        if (lv < 0f) lv = 0f;
        if (lv > 1f) lv = 1f;
        float[] b = new float[BANDS];
        if (bands != null) {
            for (int i = 0; i < BANDS; i++) {
                float v = i < bands.length ? bands[i] : 0f;
                if (v < 0f) v = 0f;
                if (v > 1f) v = 1f;
                b[i] = v;
            }
        }
        frame = new Frame(lv, b);
        updatedAt = android.os.SystemClock.uptimeMillis();
    }

    public static void reset() {
        frame = SILENT;
        updatedAt = 0L;
        running = false;
    }
}
