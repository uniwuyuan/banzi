package com.claw.ime.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * 统一二级页渲染器：数字页 / 符号页 / Emoji 页（规格见 docs/ALT-PAGES-SPEC.md v2）。
 *
 * <p>几何：4 行可见 × (0.85 × KEYH) 的**可滚动网格** + 1 行 × (0.6 × KEYH) 页签栏 == 4 × KEYH，
 * 与 26 键页（3 行字母 + 底部行）逐像素等高。右侧 18dp 竖条只画 4 个小圆点当**滚动位置指示**
 * （不是翻页点；随滚动高亮移动；内容不足一屏不画）。</p>
 *
 * <p>数据：Theme.PAGES（设计台 pages）优先；null/坏 JSON → assets/alt_pages.json。
 * v2 用扁平 chars 列表（不分页）；数字页另有 side.chars 与 grid。</p>
 *
 * <p>滚动与点按互斥：直接用 ScrollView 的原生触摸斜率（约 8dp）——拖动即滚动、取消按键，
 * 不自己实现一套手势。</p>
 */
public final class AltPages {

    /** 输入法侧回调。只暴露动作，不暴露 View/输入法内部。 */
    public interface Host {
        void altCommit(String text);        // 上屏（字符/emoji 都走这里）
        void altOpen(String pageId);        // "num" / "sym" / "emoji" / "letter"
        void altBackspace();
        void altEnter();
        void altSpace();
        void altEditCharLib();              // 长按「导入」页签
        void altEditNumSide();              // 长按数字页左竖栏（prefs num_side_chars）
        void onAltCalc();                   // 计算（x÷y / 等号后触发；求值在 service）
    }

    public static final String PAGE_NUM = "num";
    public static final String PAGE_SYM = "sym";
    public static final String PAGE_EMOJI = "emoji";
    public static final String PAGE_LETTER = "letter";

    private static final int GRID_ROWS = 4;          // 可见行数
    private static final int GRID_COLS = 6;
    private static final float GRID_ROW_F = 0.85f;   // 每行高 = 0.85 × KEYH
    private static final int RAIL_W_DP = 18;         // 右侧滚动指示条
    private static final int DOT_DP = 6;
    private static final int DOT_GAP_DP = 12;
    private static final int NUM_SIDE_MIN = 4;       // 左列至少露 4 格
    private static final int RECENT_MAX = 48;
    private static final String ASSET = "alt_pages.json";
    private static final String PREFS = "claw_ime";
    private static final String PREFS_NUM_SIDE = "num_side_chars";
    private static final String CHAR_LIB = "char_lib.txt";
    private static final String EMOJI_RECENT = "emoji_recent.txt";

    /** 兜底常量（assets 也缺时才用）。 */
    private static final String[] NUM_SIDE_DEFAULT = {
            "+", "-", "×", "÷", "=", "%", "(", ")", "[", "]", "{", "}", "@", "#", "&", "*",
            "\"", "'", "!", "?", ":", ";", "_", "~"};
    private static final String[] NUM_DIGITS_DEFAULT = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String[] NUM_BOTTOM_DEFAULT = {"Back", "0", "."};
    private static final String[] NUM_FUNCS_DEFAULT = {"del", "space", "calc", "enter"};

    private final Context ctx;
    private final Host host;

    private JSONObject data;
    private int dataGen = -1;        // 对应 Theme.PAGES_GEN（设计台保存后热重载）
    private String active = PAGE_NUM;
    private int symTab = 0, emojiTab = 0;

    private ArrayList<String> recent;      // emoji 最近使用（懒加载）
    private ArrayList<String> charLib;     // 导入字符库（懒加载）
    private ArrayList<String> numSide;     // 数字页左竖栏字符（prefs 覆盖）

    public AltPages(Context c, Host host) {
        this.ctx = c.getApplicationContext();
        this.host = host;
    }

    // ============================================================ 对外

    /** 构建一整页；每次调用返回新 View（高度恒为 4 × KEYH）。 */
    public View build(String pageId) {
        active = normalize(pageId);
        ensureData();

        int keyH = dp(Theme.KEYH);
        int totalH = keyH * 4;
        int rowH = Math.max(1, Math.round(dp(Theme.KEYH) * GRID_ROW_F));
        if (rowH * GRID_ROWS >= totalH) rowH = Math.max(1, (totalH - 1) / GRID_ROWS);
        int gridH = rowH * GRID_ROWS;
        int railH = Math.max(1, totalH - gridH);   // 补齐尾差 → 总高严格 == 4 × KEYH

        if (PAGE_NUM.equals(active)) {
            return buildNumPage(totalH);   // v9：宽 1:3:1 五等分，4 行占满，无页签栏
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, totalH));

        LinearLayout gridZone = new LinearLayout(ctx);
        gridZone.setOrientation(LinearLayout.HORIZONTAL);
        gridZone.setGravity(Gravity.CENTER_VERTICAL);
        gridZone.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridH));

        {
            ScrollView sc = new ScrollView(ctx);
            sc.setVerticalScrollBarEnabled(false);
            sc.setFillViewport(false);
            sc.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            ArrayList<String> chars = gridChars();
            if (chars.isEmpty() && (isLibTab() || isRecentTab())) {
                sc.addView(hintView(isLibTab()
                        ? "长按「导入」页签，粘贴你的字符"
                        : "用过的 emoji 会出现在这里", rowH), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, gridH));
            } else {
                sc.addView(buildScrollContent(chars, rowH), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            gridZone.addView(sc, new LinearLayout.LayoutParams(0, gridH, 1f));
            gridZone.addView(buildScrollDots(sc),
                    new LinearLayout.LayoutParams(dp(RAIL_W_DP), gridH));
        }
        root.addView(gridZone, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridH));

        root.addView(buildTabs(railH), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, railH));
        return root;
    }

    /** 记住上次位置，如 "sym:cjk" / "emoji:smile" / "num:1"。 */
    public String currentPos() {
        if (PAGE_SYM.equals(active)) return PAGE_SYM + ":" + tabId(symTab);
        if (PAGE_EMOJI.equals(active)) return PAGE_EMOJI + ":" + tabId(emojiTab);
        return PAGE_NUM + ":1";
    }

    // ============================================================ 数字页（v9 定稿）

    /**
     * 数字页 v9：宽 = 左 1 : 中 3 : 右 1（五等分，左右纵栏与数字键等宽、完全对称）；
     * 高 = 4 行占满 4 × KEYH，无第 5 行、无底部页签栏、无滚动位置点。
     */
    private View buildNumPage(int totalH) {
        int gap = Math.max(0, Math.round(dp(Theme.KEYGAP)));
        int cellH = Math.max(1, (totalH - gap * 3) / 4);   // 4 行 + 3 个行间距 = totalH

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, totalH));

        // ---- 左列：1 格宽，可上下滚动（点按上屏 / 长按编辑）
        ScrollView sideSc = new ScrollView(ctx);
        sideSc.setVerticalScrollBarEnabled(false);
        sideSc.setFillViewport(false);
        sideSc.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout sideBox = new LinearLayout(ctx);
        sideBox.setOrientation(LinearLayout.VERTICAL);
        ArrayList<String> sideChars = numSideChars();
        for (int i = 0; i < sideChars.size(); i++) {
            View cell = sideCell(sideChars.get(i), cellH);
            sideBox.addView(cell, rowLp(cellH, gap, i > 0));
        }
        sideSc.addView(sideBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sideSc, new LinearLayout.LayoutParams(0, totalH, 1f));

        // ---- 中间：3 列 × 4 行（九宫格 + Back/0/.）
        LinearLayout center = new LinearLayout(ctx);
        center.setOrientation(LinearLayout.VERTICAL);
        String[] digits = numDigits();
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                int idx = r * 3 + c;
                String ch = idx < digits.length ? digits[idx] : "";
                View v = digitCell(ch, cellH);
                row.addView(v, cellLp(cellH));
            }
            center.addView(row, rowLp(cellH, gap, r > 0));
        }
        LinearLayout lastRow = new LinearLayout(ctx);
        lastRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] bottom = numBottom();
        lastRow.addView(backCell(cellH), cellLp(cellH));
        lastRow.addView(digitCell(bottom.length > 1 ? bottom[1] : "0", cellH), cellLp(cellH));
        lastRow.addView(digitCell(bottom.length > 2 ? bottom[2] : ".", cellH), cellLp(cellH));
        center.addView(lastRow, rowLp(cellH, gap, true));
        root.addView(center, new LinearLayout.LayoutParams(0, totalH, 3f));

        // ---- 右列：1 格宽 4 格（⌫ / 空格 / x÷y / ⏎）
        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        String[] funcs = numFuncs();
        for (int i = 0; i < funcs.length; i++) {
            View v = funcCell(funcs[i], cellH);
            right.addView(v, rowLp(cellH, gap, i > 0));
        }
        root.addView(right, new LinearLayout.LayoutParams(0, totalH, 1f));
        return root;
    }

    /** 行/格布局参数：首格无上间距，之后每格上间距 = gap（保证 4 行总和 == totalH）。 */
    private LinearLayout.LayoutParams rowLp(int h, int gap, boolean withTopGap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        if (withTopGap) lp.setMargins(Math.round(dp(Theme.KEYGAP)) / 2, gap, Math.round(dp(Theme.KEYGAP)) / 2, 0);
        else lp.setMargins(Math.round(dp(Theme.KEYGAP)) / 2, 0, Math.round(dp(Theme.KEYGAP)) / 2, 0);
        return lp;
    }

    /** 中区/右列格：等分宽度 + 左右各半间距。 */
    private LinearLayout.LayoutParams cellLp(int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
        int half = Math.round(dp(Theme.KEYGAP)) / 2;
        lp.setMargins(half, 0, half, 0);
        return lp;
    }

    private Button baseCell(String text, float fs, int color) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, fs);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(Theme.press(ctx, glassBg(Theme.SURFACE, Theme.KEYRADIUS), 0x33FFFFFF));
        return b;
    }

    private View digitCell(final String ch, int h) {
        Button b = baseCell(ch, 28f, Theme.TEXT);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.altCommit(ch); }
        });
        return b;
    }

    private View sideCell(final String ch, int h) {
        Button b = baseCell(ch, 21f, Theme.CYAN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                host.altCommit(ch);
                if ("=".equals(ch)) host.onAltCalc();   // 等号照常上屏，同时试算
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                host.altEditNumSide();
                return true;
            }
        });
        return b;
    }

    private View backCell(int h) {
        Button b = baseCell("Back", 14f, Theme.MUTED);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.altOpen(PAGE_LETTER); }
        });
        return b;
    }

    /** 右列一格：按 funcs 里的 id 分派（del / space / calc / enter）。 */
    private View funcCell(String kind, int h) {
        if ("calc".equals(kind)) {
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackground(Theme.press(ctx, glassBg(Theme.SURFACE, Theme.KEYRADIUS), 0x33FFFFFF));
            TextView main = new TextView(ctx);
            main.setText("x÷y");
            main.setTextColor(Theme.CYAN);
            main.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            main.setGravity(Gravity.CENTER);
            TextView sub = new TextView(ctx);
            sub.setText("计算");
            sub.setTextColor(Theme.DIM);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            sub.setGravity(Gravity.CENTER);
            box.addView(main);
            box.addView(sub);
            box.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { host.onAltCalc(); }
            });
            return box;
        }
        final String label;
        final float fs;
        if ("del".equals(kind)) { label = "⌫"; fs = 17f; }
        else if ("space".equals(kind)) { label = "空格"; fs = 14f; }
        else if ("enter".equals(kind)) { label = "⏎"; fs = 17f; }
        else { label = kind; fs = 14f; }
        final String kind2 = kind;
        Button b = baseCell(label, fs, Theme.CYAN);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if ("del".equals(kind2)) host.altBackspace();
                else if ("space".equals(kind2)) host.altSpace();
                else if ("enter".equals(kind2)) host.altEnter();
            }
        });
        return b;
    }

    private ArrayList<String> numSideChars() {
        if (numSide != null) return numSide;
        ArrayList<String> out = null;
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREFS_NUM_SIDE, "");
            out = parseChars(raw);
        } catch (Throwable t) {
            out = null;
        }
        if (out == null || out.isEmpty()) out = dataNumSideChars();
        if (out == null || out.isEmpty()) {
            out = new ArrayList<String>();
            for (int i = 0; i < NUM_SIDE_DEFAULT.length; i++) out.add(NUM_SIDE_DEFAULT[i]);
        }
        numSide = out;
        return out;
    }

    private String[] numDigits() {
        // 兼容三种写法：扁平 9 个（["1".."9"]）、3×3 嵌套（设计台保存 [[..],[..],[..]]）、缺省
        try {
            JSONObject num = data.optJSONObject(PAGE_NUM);
            JSONArray arr = num == null ? null : num.optJSONArray("digits");
            if (arr == null || arr.length() == 0) return NUM_DIGITS_DEFAULT;
            ArrayList<String> flat = new ArrayList<String>();
            for (int i = 0; i < arr.length(); i++) {
                Object o = arr.opt(i);
                if (o instanceof JSONArray) {
                    JSONArray row = (JSONArray) o;
                    for (int j = 0; j < row.length(); j++) flat.add(row.optString(j, ""));
                } else if (o != null) {
                    flat.add(arr.optString(i, ""));
                }
            }
            if (flat.size() < 9) return NUM_DIGITS_DEFAULT;
            String[] out = new String[9];
            for (int i = 0; i < 9; i++) out[i] = flat.get(i);
            return out;
        } catch (Throwable t) {
            return NUM_DIGITS_DEFAULT;
        }
    }

    private String[] numBottom() {
        return dataStringArray("bottom", NUM_BOTTOM_DEFAULT);
    }

    private String[] numFuncs() {
        String[] def = NUM_FUNCS_DEFAULT;
        try {
            JSONObject num = data.optJSONObject(PAGE_NUM);
            JSONArray arr = num == null ? null : num.optJSONArray("funcs");
            if (arr == null || arr.length() == 0) return def;
            String[] out = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                // 兼容两种写法：{"k":"del","t":"⌫"}（内置数据）或 "del"（设计台保存）
                String k = o != null ? o.optString("k", "") : arr.optString(i, "");
                out[i] = (k == null || k.length() == 0) ? def[Math.min(i, def.length - 1)] : k;
            }
            return out;
        } catch (Throwable t) {
            return def;
        }
    }

    private String[] dataStringArray(String key, String[] def) {
        try {
            JSONObject num = data.optJSONObject(PAGE_NUM);
            JSONArray arr = num == null ? null : num.optJSONArray(key);
            if (arr == null || arr.length() == 0) return def;
            String[] out = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                String v = arr.optString(i, "");
                out[i] = v == null ? "" : v;
            }
            return out;
        } catch (Throwable t) {
            return def;
        }
    }

    // ---- 数字页对外 API（对话框用）

    public String numSideText() {
        ArrayList<String> chars = numSideChars();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < chars.size(); i++) {
            if (i > 0) b.append(' ');
            b.append(chars.get(i));
        }
        return b.toString();
    }

    /** 保存左竖栏（空格/换行分隔；只取非空白字符，去重保序）。 */
    public void setNumSide(String text) {
        ArrayList<String> parsed = parseChars(text);
        if (parsed == null || parsed.isEmpty()) return;
        numSide = parsed;
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREFS_NUM_SIDE, numSideText()).apply();
        } catch (Throwable t) {
            // 存不上就只在本次会话生效
        }
    }

    /** 外部改完 / 重置后重读。 */
    public void reloadNumSide() { numSide = null; }

    // ============================================================ 网格 / 单元格

    /** 网格内容：rows of cols 的 LinearLayout（放进 ScrollView）。 */
    private View buildScrollContent(ArrayList<String> chars, int rowH) {
        int gap = Math.max(0, Math.round(dp(Theme.KEYGAP)));
        int half = gap / 2;
        boolean emoji = PAGE_EMOJI.equals(active);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int n = chars.size();
        int rows = (n + GRID_COLS - 1) / GRID_COLS;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                String t = idx < n ? chars.get(idx) : "";
                View v = t.length() == 0 ? emptyCell() : cellButton(t, emoji);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(half, 0, half, 0);
                row.addView(v, lp);
            }
            box.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH));
        }
        return box;
    }

    private View hintView(String text, int rowH) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Theme.MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.FONTSMALL + 1);
        t.setBackground(Theme.round(ctx, 0x0DFFFFFF, Theme.KEYRADIUS));
        return t;
    }

    private View emptyCell() {
        View v = new View(ctx);
        v.setBackground(Theme.round(ctx, 0x0DFFFFFF, Theme.KEYRADIUS));
        v.setClickable(false);
        return v;
    }

    private Button cellButton(final String text, boolean emoji) {
        final boolean isEmoji = emoji;
        final Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Theme.TEXT);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(Theme.press(ctx, glassBg(Theme.SURFACE, Theme.KEYRADIUS), 0x33FFFFFF));
        float fs;
        if (isEmoji) fs = 22f;
        else if (text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9') fs = 24f;
        else fs = 20f;
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, fs);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isEmoji) addRecent(text);
                host.altCommit(text);
            }
        });
        return b;
    }

    /** 右侧滚动位置指示：4 个小圆点（内容不足一屏不画），随滚动高亮。 */
    private View buildScrollDots(final ScrollView sc) {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setVisibility(View.INVISIBLE);
        final ArrayList<View> dots = new ArrayList<View>();
        for (int i = 0; i < GRID_ROWS; i++) {
            View dot = new View(ctx);
            dot.setBackground(dotBg(false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP));
            lp.setMargins(0, dp(DOT_GAP_DP) / 2, 0, dp(DOT_GAP_DP) / 2);
            box.addView(dot, lp);
            dots.add(dot);
        }
        final Runnable update = new Runnable() {
            @Override public void run() {
                View content = sc.getChildCount() > 0 ? sc.getChildAt(0) : null;
                int max = content == null ? 0 : content.getHeight() - sc.getHeight();
                if (max <= 0 || max <= dp(8)) {      // 内容不足一屏（或仅差几像素）
                    box.setVisibility(View.INVISIBLE);
                    return;
                }
                box.setVisibility(View.VISIBLE);
                int idx = Math.round((dots.size() - 1) * (float) sc.getScrollY() / (float) max);
                if (idx < 0) idx = 0;
                if (idx >= dots.size()) idx = dots.size() - 1;
                for (int i = 0; i < dots.size(); i++) {
                    dots.get(i).setBackground(dotBg(i == idx));
                }
            }
        };
        sc.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                update.run();
            }
        });
        sc.post(update);
        sc.postDelayed(update, 80L);
        return box;
    }

    private GradientDrawable dotBg(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? Theme.CYAN : Theme.mix(Theme.DIM, Theme.STRIP, 0.4f));
        return g;
    }

    /** 二级页键底：与字母页一致的玻璃底。 */
    private GradientDrawable glassBg(int base, float radiusDp) {
        if (Theme.NOFX) return Theme.round(ctx, base, radiusDp);
        int a = Math.round(Theme.FX_GLASSALPHA * 2.55f);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return Theme.round(ctx, (base & 0x00FFFFFF) | (a << 24), radiusDp, 0x4DFFFFFF, 1.0f);
    }

    // ============================================================ 页签栏

    /** 页签栏：左端固定「返回」（不随滚动）+ 右侧可横滑的页签。 */
    private View buildTabs(int railH) {
        int usable = ctx.getResources().getDisplayMetrics().widthPixels - dp(10);
        int tabW = Math.max(dp(64), usable / 6);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(2), dp(2), dp(2));

        Button back = makeTab("返回", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.altOpen(PAGE_LETTER); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                tabW, ViewGroup.LayoutParams.MATCH_PARENT);
        blp.setMargins(dp(2), 0, dp(2), 0);
        bar.addView(back, blp);

        HorizontalScrollView sc = new HorizontalScrollView(ctx);
        sc.setHorizontalScrollBarEnabled(false);
        sc.setFillViewport(false);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.HORIZONTAL);
        list.setGravity(Gravity.CENTER_VERTICAL);

        {
            JSONArray tabs = tabsOf(active);
            int sel = PAGE_SYM.equals(active) ? symTab : emojiTab;
            int n = tabs == null ? 0 : tabs.length();
            for (int i = 0; i < n; i++) {
                JSONObject t = tabs.optJSONObject(i);
                String label = t == null ? "" : t.optString("label", "");
                String id = t == null ? "" : t.optString("id", "");
                if (label.length() == 0) label = id;
                final int idx = i;
                final boolean isSym = PAGE_SYM.equals(active);
                final boolean isLib = isSym && "lib".equals(id);
                Button tab = makeTab(label, i == sel);
                tab.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (isSym) symTab = idx;
                        else emojiTab = idx;
                        host.altOpen(active);      // 重建 → 滚动回顶部
                    }
                });
                if (isLib) {   // 长按「导入」页签 → 编辑 files/char_lib.txt
                    tab.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override public boolean onLongClick(View v) {
                            host.altEditCharLib();
                            return true;
                        }
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        tabW, ViewGroup.LayoutParams.MATCH_PARENT);
                lp.setMargins(dp(2), 0, dp(2), 0);
                list.addView(tab, lp);
            }
            if (PAGE_SYM.equals(active)) addTab(list, "🙂", PAGE_EMOJI, false, tabW);
            else addTab(list, "符号", PAGE_SYM, false, tabW);
        }
        sc.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(sc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return bar;
    }

    private void addTab(LinearLayout list, String label, final String pageId,
                        boolean sel, int tabW) {
        Button tab = makeTab(label, sel);
        tab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.altOpen(pageId); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                tabW, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        list.addView(tab, lp);
    }

    private Button makeTab(String label, boolean sel) {
        Button tab = new Button(ctx);
        tab.setText(label);
        tab.setAllCaps(false);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.FONTTAB);
        tab.setTextColor(sel ? Theme.TEXT : Theme.MUTED);
        tab.setPadding(0, 0, 0, 0);
        GradientDrawable bg = Theme.round(ctx, sel ? Theme.SURFACE2 : 0x00000000, 12f);
        tab.setBackground(Theme.press(ctx, bg, 0x33FFFFFF));
        return tab;
    }

    // ============================================================ 数据

    private void ensureData() {
        if (data != null && dataGen == Theme.PAGES_GEN) return;
        dataGen = Theme.PAGES_GEN;
        data = parse(Theme.PAGES);
        if (data == null) data = parse(readAsset(ASSET));
        if (data == null) data = new JSONObject();
        numSide = null;   // 数据换代后重读左栏
    }

    private static JSONObject parse(String json) {
        if (json == null || json.trim().length() == 0) return null;
        try {
            return new JSONObject(json);
        } catch (Throwable t) {
            return null;
        }
    }

    private String readAsset(String name) {
        try {
            InputStream in = ctx.getAssets().open(name);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String normalize(String pageId) {
        if (PAGE_SYM.equals(pageId)) return PAGE_SYM;
        if (PAGE_EMOJI.equals(pageId)) return PAGE_EMOJI;
        if (PAGE_LETTER.equals(pageId)) return PAGE_LETTER;
        return PAGE_NUM;
    }

    private String tabId(int idx) {
        JSONArray tabs = tabsOf(active);
        if (tabs == null || idx < 0 || idx >= tabs.length()) {
            if (PAGE_SYM.equals(active)) return "cjk";
            if (PAGE_EMOJI.equals(active)) return "recent";
            return "1";
        }
        JSONObject t = tabs.optJSONObject(idx);
        String id = t == null ? null : t.optString("id", "");
        return id == null || id.length() == 0 ? String.valueOf(idx) : id;
    }

    private JSONArray tabsOf(String page) {
        try {
            JSONObject sec = data.optJSONObject(page);
            return sec == null ? null : sec.optJSONArray("tabs");
        } catch (Throwable t) {
            return null;
        }
    }

    private ArrayList<String> dataNumSideChars() {
        try {
            JSONObject num = data.optJSONObject(PAGE_NUM);
            JSONObject side = num == null ? null : num.optJSONObject("side");
            return toCharList(side == null ? null : side.optJSONArray("chars"));
        } catch (Throwable t) {
            return null;
        }
    }

    private ArrayList<String> toCharList(JSONArray arr) {
        if (arr == null) return null;
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i, "");
            if (v == null || v.length() == 0) continue;
            out.add(v);
        }
        return out;
    }

    /** 当前页网格应显示的字符列表（空列表 = 用提示页）。 */
    private ArrayList<String> gridChars() {
        if (PAGE_SYM.equals(active)) {
            if (isLibTab()) return charLib();
            return tabChars(symTab);
        }
        if (PAGE_EMOJI.equals(active)) {
            if (isRecentTab()) return recentList();
            return tabChars(emojiTab);
        }
        return new ArrayList<String>();
    }

    private ArrayList<String> tabChars(int idx) {
        try {
            JSONArray tabs = tabsOf(active);
            if (tabs == null || idx < 0 || idx >= tabs.length()) return new ArrayList<String>();
            JSONObject t = tabs.optJSONObject(idx);
            ArrayList<String> chars = t == null ? null : toCharList(t.optJSONArray("chars"));
            return chars == null ? new ArrayList<String>() : chars;
        } catch (Throwable t) {
            return new ArrayList<String>();
        }
    }

    private boolean isLibTab() {
        return PAGE_SYM.equals(active) && "lib".equals(tabId(symTab));
    }

    private boolean isRecentTab() {
        return PAGE_EMOJI.equals(active) && "recent".equals(tabId(emojiTab));
    }

    /** 空白/换行/逗号分隔的字符文本 → 字符列表（去重保序，连写也能拆）。 */
    private static ArrayList<String> parseChars(String raw) {
        if (raw == null || raw.trim().length() == 0) return null;
        ArrayList<String> out = new ArrayList<String>();
        String[] parts = raw.trim().split("[\\s,，]+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p == null || p.length() == 0) continue;
            for (int k = 0; k < p.length(); k++) {
                String ch = String.valueOf(p.charAt(k));
                if (!out.contains(ch)) out.add(ch);
            }
        }
        return out;
    }

    // ============================================================ 最近 emoji / 字符库

    private ArrayList<String> recentList() {
        if (recent == null) recent = loadList(file(EMOJI_RECENT));
        return recent;
    }

    private void addRecent(String emoji) {
        if (emoji == null || emoji.length() == 0) return;
        ArrayList<String> list = recentList();
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > RECENT_MAX) list.remove(list.size() - 1);
        if (privacyOn()) return;          // 隐匿模式：只读不写
        saveList(file(EMOJI_RECENT), list);
    }

    private ArrayList<String> charLib() {
        if (charLib == null) charLib = loadList(file(CHAR_LIB));
        return charLib;
    }

    /** 外部改完字符库后调用（长按导入页签保存后）。 */
    public void reloadCharLib() {
        charLib = null;
    }

    private File file(String name) {
        return new File(ctx.getFilesDir(), name);
    }

    private static ArrayList<String> loadList(File f) {
        ArrayList<String> out = new ArrayList<String>();
        try {
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String s = new String(bo.toByteArray(), "UTF-8");
            String[] parts = s.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i].trim();
                if (p.length() == 0 || out.contains(p)) continue;
                out.add(p);
            }
        } catch (Throwable t) {
            // 读不到 = 空
        }
        return out;
    }

    private static void saveList(File f, ArrayList<String> list) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) b.append('\n');
                b.append(list.get(i));
            }
            out.write(b.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable t) {
            // 写失败静默
        }
    }

    /** 隐匿模式：反射查 Privacy.on()，没有该类就视为关闭。 */
    private static boolean privacyOn() {
        try {
            Class<?> c = Class.forName("com.claw.ime.core.Privacy");
            Object r = c.getMethod("on").invoke(null);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================ 尺寸工具

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
