# 云间航线 · 用 Tripo 做的 3D 资产清单

统一导出要求：**GLB、1K 贴图、≤ 5,000 三角面**（在 Tripo 里先做 Low Poly / 设面数上限再导出；现在这个骰子导出了 187 万面、64 MB，装不进 App）。
风格统一写进每条提示词：soft matte plastic toy, rounded chunky shapes, pastel, clean, no text, no logo。
朝向：模型正面朝 −Z（或在交付时告诉我正面朝哪），+Y 向上，原点在底部中心。

| # | 资产 | 用在哪 | 实际尺寸 | 是否用 Tripo |
| --- | --- | --- | --- | --- |
| 1 | 飞机 | 16 架棋子 | 翼展约 5.8 cm | **是**。用 `plane-white/` 的四视图多视图生成；机身保持浅色，App 按机队上色 |
| 2 | 中央塔台 | 棋盘中心归航终点 | 高约 4 cm，底径 4 cm | **是** |
| 3 | 云朵 | 棋台四角上方漂浮装饰（"云间"） | 宽 8–12 cm | **是**，做 2–3 个不同形状 |
| 4 | 机库小棚 | 四个机库外角的装饰小屋（不挡停机位） | 宽约 6 cm | 可选 |
| 5 | 风向袋 | 起飞格旁的小装饰 | 高约 3 cm | 可选 |
| 6 | 奖杯 | 胜利时在塔台上升起 | 高约 5 cm | 可选 |
| — | 骰子 | 骰盘 | 4.2 cm | **否**，见下 |
| — | 棋盘格、棋毯、骰盘 | | | **否**，必须和规则网格、骰子物理精确对齐，继续用代码生成 |

## 提示词

**2 · 塔台**
> A small cute toy airport control tower, round ivory stem on a short round base, a wide glass cab on top with a pale teal roof and a small beacon light, soft matte plastic toy, rounded chunky shapes, pastel, clean, no text, no logo.

**3 · 云朵**
> A soft puffy stylized cloud made of 4–5 overlapping round lobes, flat-ish bottom, pure white with a faint blue-grey underside, smooth matte toy, no face, no text.

**4 · 机库小棚**
> A tiny toy airplane hangar shed with a rounded half-cylinder roof and a wide open front door, ivory walls, pastel mint roof, soft matte plastic toy, rounded chunky shapes, clean, no text, no logo.

**5 · 风向袋**
> A tiny toy windsock on a thin pole, striped pastel apricot and white cone, soft matte plastic toy, clean, no text.

**6 · 奖杯**
> A small cute toy trophy cup with two round handles on a short stepped base, warm brass gold, soft satin finish, rounded chunky shapes, no text, no engraving.

## 为什么骰子不交给 Tripo

Tripo 版骰子的点数是错的：六个面是 3、1、2、4、3、3（正面、背面、左、右、上、下），三个面都是 3，没有 5 和 6，相对面之和也不是 7。骰子是要求绝对准确的几何件，AI 生成很难保证点数排布。要更好看的话，我在 Blender 里改程序化模型（凹陷圆点、更圆的倒角、瓷釉质感），点数可以保证正确。
