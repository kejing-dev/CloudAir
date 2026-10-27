# 飞机 / 骰子 多角度参考图（给 Tripo Studio）

由 `tools/render_views.py` 用 Blender 5.2 从 App 里正在用的两个 GLB 渲染：正交相机、同一比例、模型居中占画面约 80%。
每个视角两份：浅灰底 `*-<视角>.png`（1024×1024）和透明底 `*-<视角>-transparent.png`。

| 文件夹 | 内容 | 用途 |
| --- | --- | --- |
| `plane-white/` | 飞机，中性白机身、蓝色座舱、暖白机头 | **建议拿这套去生成**：机身保持浅色，App 才能按四支机队上色 |
| `plane-mint/` | 同一架飞机，薄荷机队色 `#5B9F8B` | 看配色效果用 |
| `die/` | 骰子，暖白瓷面、深绿实体圆点 | 直接生成 |

视角：front 正面（飞机机头朝镜头）、back 背面、left 左侧、right 右侧、top 顶视、hero 3/4 视角。

## 在 Tripo 里怎么用

- **多视图生成**：上传 front / left / back / right 四张（浅灰底版本）。飞机的正面和背面几乎只看得到机翼的侧边，信息少，如果效果不好，改用 hero 单图生成。
- **单图生成**：用 `*-hero.png`。
- 骰子的点数排布：1 对 6、2 对 5、3 对 4（相对面之和为 7）。front=1、back=6、left=5、right=2、top=3、底面=4。生成后请核对，点数错了骰子会作弊。

## 参考提示词

飞机：

> A cute low-poly toy airplane for a board game piece, chunky rounded fuselage, short straight wings, small tail fins, glossy blue-teal bubble canopy, cream nose cap, soft matte plastic, clean simple shapes, no text, no decals, light neutral body color.

骰子：

> A six-sided game die, warm white porcelain with softly rounded edges and corners, dark forest-green round recessed pips, standard layout with opposite faces summing to seven, clean, no text.

## 放回 App 时

- 尺寸无所谓，App 按包围盒统一缩放；但**飞机机头朝向、上方向**需要和现在一致，或者告诉我新模型的朝向，我改 `PlaneView.MODEL_PITCH`。
- 飞机机身最好是**单独一个材质**（名字以 `Airframe` 开头最省事），App 靠它给四支机队换色；如果 Tripo 导出的是一整张贴图，我会改成乘色方式上色，四色仍然能区分。
- 骰子边长按 0.6 单位归一化，点数面的朝向需与上表一致；不一致时把新模型的六个面告诉我，我改 `DiceSolver.FACE_NORMALS`。
- 面数建议：飞机 ≤ 5k 三角面、骰子 ≤ 5k 三角面（16 架飞机同屏）。

## 骰子 v6（2026-10-26，Blender 程序化，已换进 App）

`tools/build_die.py` 生成：边长 0.6 圆角立方体（倒角 0.075、6 段），21 个球形布尔凹陷圆点，1 点大一号；暖白瓷釉身（清漆层）+ 深绿珐琅点；2,774 三角面，61 KB。
朝向与 `DiceSolver.FACE_NORMALS` 一致（glTF：1 −Z、6 +Z、2 +X、5 −X、3 +Y、4 −Y），六视图见 `die-v6/` 和 `die-v6-sheet.png`。
旧模型备份在 `die/die-v5-original.glb`。重新生成：
`blender --background --factory-startup --python tools/build_die.py -- app/src/main/assets/models/die.glb`
