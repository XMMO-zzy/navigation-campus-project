# -*- coding: utf-8 -*-
"""
生成小程序所需的最小 PNG 资源：
  1. static/tabbar/{home,feed,message,me}[-active].png —— 81x81 透明底图标
  2. static/default-avatar.png —— 200x200 默认头像
  3. static/placeholder.png —— 400x300 图片占位
纯标准库实现（zlib + struct），不依赖 Pillow。
"""
import os
import zlib
import struct

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static')
TAB = os.path.join(BASE, 'tabbar')
os.makedirs(TAB, exist_ok=True)


def write_png(path, width, height, pixel_fn):
    """pixel_fn(x, y) -> (r, g, b, a)"""
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0
        for x in range(width):
            r, g, b, a = pixel_fn(x, y)
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        c += struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', ihdr)
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)
    return path


def blank(w, h):
    return lambda x, y: (0, 0, 0, 0)


def rounded_rect(w, h, radius, color, fill_alpha=255):
    """圆角矩形 + 可选描边"""
    r, g, b = color

    def fn(x, y):
        # 计算到最近圆角的距离
        cx = min(max(x, radius), w - 1 - radius)
        cy = min(max(y, radius), h - 1 - radius)
        dx = x - cx
        dy = y - cy
        d = (dx * dx + dy * dy) ** 0.5
        if d <= radius - 1.5:
            return (r, g, b, fill_alpha)
        if d <= radius + 0.5:
            return (r, g, b, fill_alpha)
        return (0, 0, 0, 0)
    return fn


def circle(cx, cy, radius, color, alpha=255):
    r, g, b = color

    def fn(x, y):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if d <= radius - 1:
            return (r, g, b, alpha)
        if d <= radius + 0.5:
            return (r, g, b, alpha)
        return (0, 0, 0, 0)
    return fn


def ring(cx, cy, radius, thickness, color, alpha=255):
    r, g, b = color
    inner = radius - thickness

    def fn(x, y):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if inner - 1 <= d <= radius + 0.5:
            return (r, g, b, alpha)
        return (0, 0, 0, 0)
    return fn


def compose(*fns):
    def fn(x, y):
        for f in fns:
            px = f(x, y)
            if px[3] > 0:
                return px
        return (0, 0, 0, 0)
    return fn


def bar(x0, y0, x1, y1, color, alpha=255, radius=0):
    """实心矩形/圆角小条"""
    r, g, b = color

    def fn(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y1):
            return (0, 0, 0, 0)
        if radius:
            cx = min(max(x, x0 + radius), x1 - radius)
            cy = min(max(y, y0 + radius), y1 - radius)
            if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 > radius + 0.5:
                return (0, 0, 0, 0)
        return (r, g, b, alpha)
    return fn


S = 81  # tabbar 图标尺寸

# 颜色：未选中灰 / 选中蓝（与 app.json tabBar 配置一致）
GRAY = (140, 150, 165)
BLUE = (30, 111, 232)


# ---------------- 1. 首页（房子） ----------------
def icon_home(color):
    return compose(
        # 屋顶：三角（用多段矩形逼近）
        *[bar(S // 2 - i, 20 + i, S // 2 + i, 22 + i, color, radius=1) for i in range(18)],
        # 墙体
        bar(20, 38, 61, 62, color, radius=2),
        # 门（挖空：用透明覆盖不行，改为留白——这里简化不挖）
    )


# ---------------- 2. 动态（气泡/卡片） ----------------
def icon_feed(color):
    return compose(
        bar(16, 22, 65, 54, color, radius=6),
        bar(28, 58, 38, 68, color, radius=1),  # 小尾巴
    )


# ---------------- 3. 消息（对话气泡） ----------------
def icon_message(color):
    return compose(
        bar(14, 20, 67, 52, color, radius=8),
        bar(30, 54, 42, 66, color, radius=2),
    )


# ---------------- 4. 我的（人形） ----------------
def icon_me(color):
    return compose(
        circle(S // 2, 30, 13, color),
        # 肩膀：圆角矩形
        bar(18, 48, 63, 64, color, radius=7),
    )


ICONS = {
    'home': icon_home,
    'feed': icon_feed,
    'message': icon_message,
    'me': icon_me,
}

# 纯色版本（矩形兜底，确保任何解析器都能显示）
for name, fn in ICONS.items():
    write_png(os.path.join(TAB, f'{name}.png'), S, S, fn(GRAY))
    write_png(os.path.join(TAB, f'{name}-active.png'), S, S, fn(BLUE))

# ---------------- 5. 默认头像 ----------------
AV = 200
write_png(
    os.path.join(BASE, 'default-avatar.png'), AV, AV,
    compose(
        rounded_rect(AV, AV, 100, (232, 241, 254), 255),
        circle(AV // 2, 76, 30, (30, 111, 232), 200),
        bar(46, 118, 154, 152, (30, 111, 232), 200, radius=17),
    )
)

# ---------------- 6. 图片占位 ----------------
PH_W, PH_H = 400, 300
write_png(
    os.path.join(BASE, 'placeholder.png'), PH_W, PH_H,
    compose(
        rounded_rect(PH_W, PH_H, 0, (242, 244, 248), 255),
        bar(140, 110, 260, 190, (227, 232, 240), 255, radius=8),
    )
)

print('生成完成：')
for f in sorted(os.listdir(TAB)):
    print('  tabbar/' + f, os.path.getsize(os.path.join(TAB, f)), 'bytes')
for f in ['default-avatar.png', 'placeholder.png']:
    print('  ' + f, os.path.getsize(os.path.join(BASE, f)), 'bytes')
