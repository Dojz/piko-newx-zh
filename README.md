构建 Piko NewX 和 Instagram 简体中文补丁。

Morphe 使用同一个源：

https://github.com/Dojz/piko-newx-zh

也可直接添加 JSON 地址：

https://raw.githubusercontent.com/Dojz/piko-newx-zh/main/patches-bundle.json

更新源后，使用未修补的原版 APK/APKM，或 Morphe 保存的原版安装包进行修补。使用同一个 Morphe 签名密钥，可覆盖安装已有的修补版。停用此前单独添加的 caption-test、caption-test-12.28 测试源，避免重复选择补丁。

| X 完整版本 | 对应实现 | 上游标记 |
| --- | --- | --- |
| 12.28.0-alpha.01 | Piko 3.42.2 | 实验性 |
| 12.28.0-alpha.04 | Piko 3.42.2 | 实验性 |
| 12.28.0-prod.01 | Piko 3.42.2 | 正式 |
| 12.29.0-alpha.04 | Piko 3.42.2 | 实验性 |
| 12.29.1-prod.01 | Piko 3.42.2 | 实验性 |
| 12.30.0-alpha.05 | Piko 3.42.2 | 实验性 |
| 12.30.0-prod.01 | Piko 3.54.0 | 正式 |

实验性目标需要在 Morphe 中启用实验版本。以上保留上游的兼容声明；构建和补丁加载检查通过，具体 APK 的修补及运行仍需实机验证。

文件名模板变量：`{text}`（原文）、`{translatedText}`（按系统语言翻译）。使用任一变量即可保存模板。翻译失败回退原文，文件名按 UTF-8 字节截断，重名追加数字。

每次发布把新实现和仍有用途的旧实现放入同一个 `.mpp`。同一 X 版本只保留最新声明支持它的实现，Morphe 按 APK 的完整版本号筛选补丁；推荐版本仍由最新可用目标决定。以后新增版本时保留旧目标，无需切换源。

`compatibility-bundles.json` 记录原始补丁包、源码提交和 SHA-256。新版原始包另存为 `patches-current.mpp`，统一包为 `patches.mpp`。每次构建验证兼容目标、同名补丁冲突、扩展依赖和 DEX 完整性，通过后才更新源地址。Morphe 本身不负责回溯历史发布。
