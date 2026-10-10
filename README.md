构建 Piko NewX 和 Instagram 简体中文补丁。

Morphe 中同时添加以下两个远程 JSON 源，停用原来的 Piko 正式源：

- 12.28–12.29：https://raw.githubusercontent.com/Dojz/piko-newx-zh/caption-test-12.28/patches-bundle.json
- 12.30：https://raw.githubusercontent.com/Dojz/piko-newx-zh/caption-test/patches-bundle.json

使用未修补的原版 APK/APKM，或 Morphe 保存的原版安装包。Morphe 按完整版本号筛选已加载的兼容补丁，不会自动回溯 GitHub 历史发布。源列表中显示仓库名称，不代表 JSON 文件地址发生重定向。

12.28–12.29 源基于 Piko v3.42.2，保留 12.28.0-prod.01、12.28.0-alpha.01、12.28.0-alpha.04、12.29.0-alpha.04、12.29.1-prod.01 和 12.30.0-alpha.05 的上游兼容声明。12.30 源基于 Piko v3.54.0，正式目标为 12.30.0-prod.01。其他具体版本需要相应 APK/APKM 验证。

文件名模板变量：`{text}`（原文）、`{translatedText}`（按系统语言翻译）。使用任一变量即可保存模板。翻译失败回退原文，文件名按 UTF-8 字节截断，重名追加数字。
