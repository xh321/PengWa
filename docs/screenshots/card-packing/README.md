# 自我介绍宽度与空位回填

自我介绍过去使用一格，后面的两格链接会截断这一行，后续小块无法利用旁边的空位。
现在同时调整文字默认占格和瀑布流放置：

- 按当前字体和实际单格正文宽度测量文字。正文达到三行或标题达到两行时默认跨两格，短文字保持一格；链接两格，社交与图片一格。
- 后续小块可回填前面跨列块上方放得下的空位。跨列块与其他块之间保留 12 dp 间距，互不重叠；放不下时继续向下排。
- 窄屏／大字体仍回退为一列。图片比例与透明轮廓不变。编辑操作和保存／传输顺序不变，视觉位置允许回填。

## 环境和复现

Android Emulator `sdk_gphone64_x86_64` / AVD `PengWaIcon`，Android 16 / API 36。普通屏 1080×2400，density 420，字体 1.0；窄屏 840×1680，字体 1.5；宽屏 1600×2560，字体 1.0。全部为运行中的 Debug APK，通过 adb screencap 截图。姓名、UUID、账号、链接与图片均为合成测试数据。

```sh
adb -s emulator-5554 shell am force-stop app.pengwa.debug
adb -s emulator-5554 shell am start -n app.pengwa.debug/app.pengwa.ui.UiPreviewActivity --es screen card
# 收到的卡片：card-received；编辑预览：card-editor，点击“预览”
# 混合内容：card-masonry；详情：card-masonry-received；编辑预览：card-masonry-editor
adb -s emulator-5554 shell cmd uimode night yes # no 恢复浅色
# 窄屏：wm size 840x1680；settings put system font_scale 1.5
# 宽屏：wm size 1600x2560；settings put system font_scale 1.0
# 最后恢复：wm size reset；settings put system font_scale 1.0
```

## 同场景前后截图

| 页面／状态 | 变更前 | 变更后 |
| --- | --- | --- |
| 卡片／浅色 | ![](card-before-light.png) | ![](card-after-light.png) |
| 卡片／深色 | ![](card-before-dark.png) | ![](card-after-dark.png) |
| 收到的卡片／浅色 | ![](received-before-light.png) | ![](received-after-light.png) |
| 收到的卡片／深色 | ![](received-before-dark.png) | ![](received-after-dark.png) |
| 编辑预览／浅色 | ![](editor-preview-before-light.png) | ![](editor-preview-after-light.png) |
| 编辑预览／深色 | ![](editor-preview-before-dark.png) | ![](editor-preview-after-dark.png) |
| 混合内容／浅色 | ![](mixed-before-light.png) | ![](mixed-after-light.png) |
| 混合内容／深色 | ![](mixed-before-dark.png) | ![](mixed-after-dark.png) |
| 混合详情／浅色 | ![](mixed-received-before-light.png) | ![](mixed-received-after-light.png) |
| 混合详情／深色 | ![](mixed-received-before-dark.png) | ![](mixed-received-after-dark.png) |
| 混合编辑预览／浅色 | ![](mixed-editor-preview-before-light.png) | ![](mixed-editor-preview-after-light.png) |
| 混合编辑预览／深色 | ![](mixed-editor-preview-before-dark.png) | ![](mixed-editor-preview-after-dark.png) |

窄屏大字体：[卡片](card-narrow-large-font.png)、[混合内容](mixed-narrow-large-font.png)。
宽屏三列：[卡片](card-wide.png)、[混合内容](mixed-wide.png)。

## 验证

- 在仅包含本 PR 改动的独立目录执行 `testDebugUnitTest assembleDebug lintDebug` 成功，186 项单元测试通过，Lint 无 error。
- 新增三个布局测试覆盖：一格文字旁空位可被后续小块回填、1–3 列的混合跨度不重叠且保留间距、单列跨度回退和空卡片。
- 模拟器核对卡片、详情与编辑预览中的自我介绍横跨两格，深浅主题、窄屏大字体和宽屏布局。
- 蓝牙、NFC 和扫码双真机链路仍未验证；本次不改变保存与传输格式。
