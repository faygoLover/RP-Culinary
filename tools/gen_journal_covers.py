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

# ------------------------------------------------------------------ поваренные книги (D3, 03.11) и личная книга рецептов
BOOK_ICONS = {
    'soups': ['.+.+.+.', '..+.+..', '#######', '.#####.', '.#####.', '.#####.', '..###..'],      # кастрюля с паром
    'baking': ['.......', '..###..', '.#+#+#.', '#######', '#######', '.#####.', '.......'],     # каравай
    'meat': ['...##..', '..####.', '..####.', '...###.', '..#....', '.#.....', '+.+....'],       # окорочок
    'fish': ['.......', '..##..#', '.####.#', '#+#####', '.####.#', '..##..#', '.......'],       # рыба
    'salads': ['....###', '...####', '..####.', '.####..', '.#+#...', '#.#....', '.......'],     # лист
    'drinks': ['..+.+..', '.......', '#####..', '#####+#', '#####.#', '#####+.', '.###...'],     # кружка
    'butchery': ['####...', '#####..', '######.', '####+##', '....##+', '.....##', '......#'],  # тесак
    'preserves': ['.+++++.', '.#####.', '#.....#', '#+###.#', '#+###.#', '#.....#', '.#####.'],  # банка
    'vintner': ['...+...', '...#...', '..###..', '.#####.', '.#+##.#', '.#####.', '.#####.'],    # бутылка
}
BOOK_PAL = {  # переплёт, тень, рисунок, блик
    'soups': ((150, 96, 44), (104, 64, 28), (70, 40, 20), (240, 236, 226)),
    'baking': ((176, 128, 60), (128, 88, 38), (110, 64, 24), (236, 196, 120)),
    'meat': ((140, 46, 40), (96, 28, 24), (240, 210, 190), (255, 248, 240)),
    'fish': ((44, 96, 120), (28, 64, 84), (220, 236, 240), (255, 255, 255)),
    'salads': ((70, 118, 52), (46, 82, 32), (190, 230, 120), (240, 252, 210)),
    'drinks': ((120, 70, 110), (84, 46, 78), (236, 222, 200), (255, 250, 240)),
    'butchery': ((70, 66, 64), (44, 42, 40), (200, 204, 210), (250, 250, 252)),
    'preserves': ((96, 112, 60), (64, 76, 38), (210, 120, 60), (230, 236, 240)),
    'vintner': ((98, 36, 54), (66, 22, 36), (60, 120, 70), (220, 240, 200)),
}
LABEL = (232, 222, 196)


def cookbook(c, sh, ink, hl, icon):
    im = journal(c, sh, ink, hl, ['.......'] * 7)
    px = im.load()
    for x in range(5, 12):  # ярлык с названием
        px[x, 2] = LABEL
        px[x, 3] = LABEL
    for x in (6, 8, 10):
        px[x, 3] = sh
    draw_icon(px, icon, 5, 6, ink, hl)
    return im


books = {k: cookbook(*BOOK_PAL[k], BOOK_ICONS[k]) for k in BOOK_ICONS}
for k, im in books.items():
    im.save(os.path.join(OUT, f'cookbook_{k}.png'))
# личная книга рецептов: тёмная тетрадь с резинкой и карандашом
nb = journal((58, 44, 36), (38, 28, 22), (200, 60, 50), (240, 200, 90), ['.......'] * 7)
px = nb.load()
for y in range(1, 15):
    px[10, y] = (180, 40, 36)  # резинка
for y in range(4, 13):
    px[6, y] = (232, 196, 80)  # карандаш
px[6, 12] = (60, 50, 44)
px[6, 3] = (230, 150, 150)
nb.save(os.path.join(OUT, 'recipe_notebook.png'))
order2 = list(BOOK_ICONS)
sheet = Image.new('RGBA', ((len(order2) + 1) * (16 * S + 16) + 16, 16 * S + 32), (60, 60, 64, 255))
for n, k in enumerate(order2):
    sheet.alpha_composite(books[k].resize((16 * S, 16 * S), Image.NEAREST), (16 + n * (16 * S + 16), 16))
sheet.alpha_composite(nb.resize((16 * S, 16 * S), Image.NEAREST), (16 + len(order2) * (16 * S + 16), 16))
sheet.save(os.path.join(ROOT, 'docs', 'cookbooks_preview.png'))
print('cookbooks', len(books) + 1)
