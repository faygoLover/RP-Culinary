"""Обложки журналов и записей исследований (D2, 02.11): 16×16 текстуры по группам знаний + лист предпросмотра.
Журнал — кожаная книга с тиснёным рисунком по теме; записи исследований — служебная папка с грифом.
Запуск: python -I tools/gen_journal_covers.py  →  src/main/resources/assets/rpculinary/textures/item/notes_<группа>.png,
docs/covers_preview.png (×12)."""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'textures', 'item')
os.makedirs(OUT, exist_ok=True)

# Рисунки 7×7: '#' — основной цвет, '+' — светлый, '.' — пусто
ICONS = {
    'game': ['#.....#', '##...##', '.#.#.#.', '..###..', '..+#+..', '...#...', '...#...'],      # рога
    'seafood': ['..###..', '.#+#+#.', '#+#+#+#', '#######', '.#####.', '..###..', '...#...'],   # ракушка-гребешок
    'fugu': ['.#.#.#.', '#######', '#+##+##', '#######', '.#####.', '.#.#.#.', '.......'],      # иглобрюх
    'deep_sea': ['.+.....', '..##..#', '.####.#', '#+#####', '.####.#', '..##..#', '.......'], # рыба с огоньком
    'insects': ['#.....#', '.#...#.', '..###..', '.#+#+#.', '..###..', '.#...#.', '#.....#'],  # жук
    'offal': ['.##.##.', '#######', '#+#####', '#######', '.#####.', '..###..', '...#...'],     # сердце
    'mushrooms': ['..###..', '.#+#+#.', '#######', '...#...', '...#...', '..###..', '.......'],  # гриб
    'other_worlds': ['..###..', '.#####.', '##+++##', '##+#+##', '##+++##', '.#####.', '..###..'],  # око
    'monsters': ['.#####.', '#######', '#+##+##', '#######', '.#.#.#.', '.#####.', '.......'],  # череп
}
# Журналы: (кожа, тень кожи, тиснение-основа, тиснение-блик)
JOURNAL = {
    'game': ((112, 74, 44), (78, 50, 30), (198, 160, 92), (232, 206, 140)),
    'seafood': ((52, 86, 110), (34, 58, 78), (210, 190, 150), (240, 226, 196)),
    'fugu': ((128, 44, 40), (88, 28, 26), (224, 188, 98), (248, 222, 150)),
    'deep_sea': ((30, 52, 84), (18, 32, 56), (110, 190, 210), (200, 240, 240)),
    'insects': ((70, 92, 46), (46, 62, 28), (200, 184, 90), (236, 222, 140)),
    'offal': ((96, 34, 40), (64, 20, 26), (210, 120, 110), (240, 170, 160)),
    'mushrooms': ((98, 80, 56), (66, 52, 36), (200, 70, 52), (240, 232, 214)),
}
# Записи исследований: (картон, тень, рисунок, блик) — серо-зелёная папка, красный гриф
RESEARCH = {
    'other_worlds': ((92, 104, 88), (62, 72, 60), (90, 40, 110), (190, 120, 220)),
    'monsters': ((92, 104, 88), (62, 72, 60), (70, 110, 60), (170, 220, 140)),
}
PAGES, PAGES_SH, OUTLINE, STAMP = (236, 228, 204), (196, 186, 160), (36, 24, 18), (176, 34, 30)


def journal(c, sh, ink, hl, icon):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(1, 15):  # переплёт с обрезом страниц справа
        for x in range(2, 14):
            px[x, y] = c
        px[2, y] = sh
        px[3, y] = sh
        px[13, y] = PAGES if 2 <= y <= 13 else sh
        px[12, y] = PAGES_SH if 2 <= y <= 13 else c
    for x in range(2, 14):
        px[x, 1] = sh
        px[x, 14] = sh
    for y in range(1, 15):  # контур
        px[1, y] = OUTLINE
        px[14, y] = OUTLINE
    for x in range(1, 15):
        px[x, 0] = OUTLINE
        px[x, 15] = OUTLINE
    for y in (3, 12):  # ремешки на корешке
        px[2, y] = hl
        px[3, y] = ink
    draw_icon(px, icon, 5, 4, ink, hl)
    return im


def research(c, sh, ink, hl, icon):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(1, 15):  # торчащий лист
        for x in range(4, 14):
            if y <= 3:
                px[x, y] = PAGES if x < 13 else PAGES_SH
    for y in range(3, 15):  # папка
        for x in range(2, 14):
            px[x, y] = c
        px[2, y] = sh
    for x in range(2, 14):
        px[x, 14] = sh
        px[x, 3] = sh
    for y in range(1, 16):
        for x in range(1, 15):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] and px[x + dx, y + dy] != OUTLINE
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE
    draw_icon(px, icon, 5, 5, ink, hl)
    for x in range(9, 14):  # красный гриф в углу
        px[x, 13] = STAMP
    px[13, 12] = STAMP
    return im


def draw_icon(px, icon, ox, oy, ink, hl):
    for j, row in enumerate(icon):
        for i, ch in enumerate(row):
            if ch == '#':
                px[ox + i, oy + j] = ink
            elif ch == '+':
                px[ox + i, oy + j] = hl


covers = {}
for k, pal in JOURNAL.items():
    covers[k] = journal(*pal, ICONS[k])
for k, pal in RESEARCH.items():
    covers[k] = research(*pal, ICONS[k])
for k, im in covers.items():
    im.save(os.path.join(OUT, f'notes_{k}.png'))

# предпросмотр: обложки ×12 в ряд, с подписью-номером по порядку
order = ['game', 'seafood', 'fugu', 'deep_sea', 'insects', 'offal', 'mushrooms', 'other_worlds', 'monsters']
S = 12
sheet = Image.new('RGBA', (len(order) * (16 * S + 16) + 16, 16 * S + 32), (60, 60, 64, 255))
for n, k in enumerate(order):
    sheet.alpha_composite(covers[k].resize((16 * S, 16 * S), Image.NEAREST), (16 + n * (16 * S + 16), 16))
sheet.save(os.path.join(ROOT, 'docs', 'covers_preview.png'))
print('covers', len(covers))
