# 发布清单（仓库维护者用，公开前可删）

## 1. 建仓库
- 仓库名建议：`banzi`（或 `banzi-ime`）
- 描述建议：`纯 Java 自绘 Android 输入法：AI 联想 + 可玩 HTML 设计台 + 隐私模式（核心闭源，开源 AI/UI/设计）`
- 话题（Topics）：`android` `ime` `input-method` `keyboard` `canvas` `ai` `design-system` `chinese`
- 勾选：Add README / Add .gitignore（我们用自带的）/ License 选 Apache-2.0

## 2. 推送
```bash
cd banzi-public
git init -b main
git add .
git commit -m "半字 Banzi: 开源 AI/UI/设计资料 + 0.5.57 APK"
git remote add origin git@github.com:<你的用户名>/banzi.git
git push -u origin main
```

## 3. 打开 GitHub Pages
Settings → Pages → Source 选 **GitHub Actions**（.github/workflows/pages.yml 已就绪）→ 推送后自动部署
上线后把 `README.md` 和 `demo/index.html` 里的 `USER` 换成你的用户名。

## 4. 建 Release
Releases → Draft a new release：
- Tag：`v0.5.57`
- 标题：`0.5.57 · 数字页九宫格 + 设计台预览保真`
- 附件：`release/banzi-0.5.57.apk`（然后把仓库里的 APK 删掉，避免仓库越来越大）
- 说明：截图 + 3 行更新内容

## 5. 拿 star 的推广顺序（先做 1-2 个就好）
1. **V2EX** 创意/分享节点：主题「一个人用一台手机写的输入法，开源了 AI 与 UI 部分」，正文贴截图 + Pages demo 链接
2. **酷安**：发到「输入法」圈 + 自建应用页，回复里放 GitHub Pages demo
3. **掘金 / 少数派**：写一篇「离线联想算法 v4 的 held-out 评测」或「HTML 设计台怎么做键盘调参」
4. **小红书/B站** 短视频：键盘动画、光子彩蛋、计算键——视觉向最容易被搬运
5. 提交到 awesome 列表：`awesome-android` / `awesome-ime`（如果有）

## 6. 注意
- 不要发布：词库/引擎源码、API Key、个人数据、手机备份
- APK 里含闭源代码：Release 说明里写清楚「源码仅含 AI/UI 部分」
- 词库如来自第三方输入法，非自建请勿公开；已开源部分不涉及
