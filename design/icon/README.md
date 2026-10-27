# 应用图标（PICO OS 6 分层图标）

`python tools/build_icon.py --preview design/icon/icon-preview.png` 生成，Pillow 绘制，改颜色/构图改脚本顶部常量。

| 层 | 文件 | 内容 |
| --- | --- | --- |
| 背景 bg | `drawable/icon_3d_layer_0.png` | 天蓝→米白渐变 + 白色虚线航迹 |
| 中层 fg2 | `drawable/icon_3d_layer_1.png` | 两朵白云 |
| 顶层 fg3 | `drawable/icon_3d_layer_2.png` | 薄荷机队玩具飞机（俯视，向右上爬升） |

- 每层 1024×1024，内容在内切圆内；前景层边缘不透明、无手绘阴影高光（系统会加）。
- SDF：`icon_3d_sdf_N.png`，向内距离 ×8 封顶 255，与模板格式一致。
- 拍平图：`mipmap-xxxhdpi/ic_spatial_launcher.png`（圆形）和 `drawable/ic_launcher_foreground.png`（自适应图标方形，内容在安全区）。
- `values/drawables_3d.xml` 的 `icon_3d_list` / `icon_sdf_list` 已改成三层，顶层在前。
- 模板原图备份在 `template-original/`。
