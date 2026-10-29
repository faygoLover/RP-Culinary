"""Документ docs/groups_2026-10-29.md из tools/groups_spec.py и gallery/cache/cands.json (данные сборки DEP_S5_rev)."""
import json, os, sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
from groups_spec import GROUPS, RECIPES

cands = json.load(open(os.path.join(ROOT, 'gallery', 'cache', 'cands.json'), encoding='utf-8'))
by_id = {c['id']: c for c in cands}
MOD = {'minecraft': 'ваниль', 'farmersdelight': 'FD', 'farm_and_charm': 'F&C', 'butchery': 'Butchery', 'alexscaves': 'AC',
       'alexsmobs': 'AM', 'sa_combat': 'SA', 'survival_instinct': 'SI', 'zombie_extreme': 'ZE', 'scpo': 'SCP', 'vinery': 'Vinery',
       'brewery': 'Brewery', 'candlelight': 'Candlelight', 'csfoundation': 'CS', 'csaugmentations': 'CS', 'biomesoplenty': 'BoP',
       'blocksyouneed_luna': 'BYN', 'supplementaries': 'Suppl.', 'camping': 'Camping', 'quark': 'Quark', 'stardew_fishing': 'Stardew',
       'fl_zone_arts': 'Зона', 'legendarysurvivaloverhaul': 'LSO', 'refurbished_furniture': 'Refurb.'}

def resolve(entry):
    if ':' in entry:
        return [entry] if entry in by_id else []
    return [i for i in by_id if i.split(':', 1)[1] == entry]

def label(i):
    c = by_id[i]
    ns = i.split(':')[0]
    return f"{c['name'] or i.split(':')[1]} <sub>{MOD.get(ns, ns)}</sub>"

out, missing, seen = [], [], {}
out.append('# Новые группы экзотики и группы рецептов — предложение (29.10.2026)\n')
out.append('Старые группы брата (12 тегов `rpculinary:exotic/*`) собраны криво. Здесь группы собраны заново по предметам сборки `DEP_S5_rev` '
           '(889 съедобных предметов, `gallery/cache/cands.json`). **Ингредиент** — предмет входит хотя бы в один рецепт еды, '
           '**еда** — ест только как есть. Сгенерировано `tools/gen_groups_doc.py` из `tools/groups_spec.py`.\n')
out.append('Как это работает (как было у брата, механику оставляем): у группы есть **эффект на того, кто ест** (от продукта, не от повара) '
           'и, где сказано, **знание**: без него повар готовит хуже, а иногда это опасно. Знание даёт книга «Записи исследований» или перк. '
           'Куру и телепорт Края убраны.\n')
out.append('## Сводка\n')
out.append('| # | Группа | Ингредиентов | Еды | Эффект коротко |')
out.append('|---|---|---|---|---|')
bodies = []
for n, g in enumerate(GROUPS, 1):
    ids = []
    for e in g['items']:
        r = resolve(e)
        if not r: missing.append((g['key'], e))
        for i in r:
            if i in ids: continue
            ids.append(i)
            seen.setdefault(i, []).append(g['key'])
    ing = [i for i in ids if by_id[i]['nin'] > 0]
    food = [i for i in ids if by_id[i]['nin'] == 0]
    short = g['effect'].split('**')[1] if '**' in g['effect'] else g['effect'][:40]
    out.append(f"| {n} | [{g['title']}](#{n}-{g['key']}) | {len(ing)} | {len(food)} | {short} |")
    b = [f"\n## {n}. {g['title']} <a id=\"{n}-{g['key']}\"></a>\n", f"**Что это.** {g['why']}\n",
         f"**Эффект на едока.** {g['effect']}\n", f"**Знание.** {g['knowledge']}\n",
         '| Ингредиенты | Еда (не ингредиент) |', '|---|---|']
    for k in range(max(len(ing), len(food))):
        a = label(ing[k]) if k < len(ing) else ''
        f = label(food[k]) if k < len(food) else ''
        b.append(f'| {a} | {f} |')
    if g.get('ask'):
        b.append('\n**Вопросы:**')
        b += [f'- {q}' for q in g['ask']]
    bodies.append('\n'.join(b))
out += bodies
out.append('\n## Группы рецептов: техника по перку\n')
out.append('Без нужного перка у техники два варианта: **нельзя** — станция не работает, рецепт не выходит, с сообщением '
           '«Вы не умеете…»; или **брак** — продукт выходит, но с настоящей опасностью. Моё предложение — в конце раздела.\n')
out.append('| Техника | Перк | Что входит | Без перка |')
out.append('|---|---|---|---|')
for r in RECIPES:
    out.append(f"| **{r['title']}** | {r['perk']} | {r['what']} | {r['without']} |")
out.append('''
**Что думаю про «просто отключить».** Отключить по перку можно, и это проще всего для станций, которые нужны только для одной техники
(перегонный куб и бочки Brewery, бочка и пресс Vinery, сушилка F&C): у игрока без перка станция просто не открывается, с сообщением.
JEI у игроков и так нет, рецептов они не видят. Сложнее с общими станциями (кастрюля FD варит и суп, и консервы): там можно только
отменить готовый результат.

Реалистичнее **брак** там, где опасность настоящая: самогон без умения — сивуха и метанол, домашние консервы — ботулизм,
вино — уксус. Игрок может рискнуть и пострадать, это даёт РП. Где брак не имеет смысла (сыр без закваски), лучше «нельзя».

Предлагаю так: **«нельзя» по умолчанию для всех техник**, а «брак» включать в конфиге для самогона, консервов и вина. Перков три:
«Винокур» (самогон, пиво, вино), «Заготовщик» (консервы, копчение, засолка, вяление), «Сыровар» (сыр, масло). Колбасы и кондитерка —
по уровню навыка «Кулинария», без перков.
''')
out.append('## Что вне групп\n')
out.append('- Лекарства, шприцы, таблетки и бинты (ZE, SI, Cybernetic) попали в список съедобного, но к кулинарии не относятся. В группы их не беру.\n'
           '- Обычная скотина (корова, свинья, овца, курица, кролик), обычная рыба (треска, лосось, тропическая), овощи, фрукты, зерно, '
           'молоко, яйца кур — без групп.\n'
           '- Алкоголь как еда — без групп. Его делание — «Группы рецептов».\n')
open(os.path.join(ROOT, 'docs', 'groups_2026-10-29.md'), 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
dup = {i: g for i, g in seen.items() if len(g) > 1}
print('missing:', missing)
print('in several groups:', dup)
