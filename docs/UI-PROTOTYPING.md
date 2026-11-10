# UI 迭代流程 · HTML 线框优先（2026-11-10 起）

> 用户定：**以后 UI 都先用 HTML 调**（能点、在手机 Edge 里看），顺了再进 App。
> 不再用 SVG 出稿。

## 流程

1. 线框文件放 `design/mockups/*.html`（自包含：CSS/JS 内联，不用外链/CDN）。
2. 起预览服务（在仓库根目录）：
   ```bash
   nohup bash tools/serve_mockups.sh &      # 监听 127.0.0.1:8899
   ```
3. 手机上用 Edge 打开：`http://127.0.0.1:8899/<文件名>.html`
   - 用户可直接点按钮看反馈，比截图快
   - 刷新页面即可看到改动，不用重装/重启
4. 用户点头后，才动 `src/`（AltPages / ClawImeService / 设计台）。
5. 视觉 token 与真机一致（见 `design/SCHEMA.md` / `Theme.java`）：
   - BG `#0E1426` / SURFACE `#1B2440` / LINE `#2A3556`
   - CYAN `#22D3EE` / PURPLE `#C084FC`
   - 候选栏 32dp、键高 46dp、键距 4dp、圆角 12dp
   - 线与真机 1:1（CSS px ≈ dp，手机视口宽 411 左右）

## 已有线框

| 文件 | 内容 |
|---|---|
| `design/mockups/num-page.html` | 数字页 v5：左滚动符号列（对称 1:3:1）+ 九宫格 + Back/0/. + 右列 ⌫/空格/x÷y/⏎ + 计算候选演示 |
| `design/mockups/index.html` | 早期 P3 SVG 总览（历史，不用了） |

## 约定

- 线框里的键尽量都能点（demo 行为：上屏/计算/提示），用户要看「手感」
- 每个线框底部写清「已定 / 待定」清单
- 改完线框 `send_message`/回复里给用户 URL，别让用户自己找路径
