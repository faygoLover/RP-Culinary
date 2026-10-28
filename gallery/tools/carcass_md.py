import json, collections, re
C = json.load(open('carcass.json', encoding='utf-8'))
RU = [('cow', 'Корова'), ('brownmooshroom', 'Грибная корова (обе)'), ('pig', 'Свинья'), ('sheep', 'Овца'), ('goat', 'Коза'), ('chicken', 'Курица'),
      ('rabbit', 'Кролик'), ('horse', 'Лошадь (все масти)'), ('donkey', 'Осёл'), ('mule', 'Мул'), ('llama', 'Лама (все)'), ('camel', 'Верблюд'),
      ('wolf', 'Волк'), ('fox', 'Лиса (и снежная)'), ('polar_bear_carcass', 'Белый медведь'), ('panda', 'Панда'), ('ocelot', 'Оцелот'),
      ('skinned_cat', 'Кошка (все)'), ('hoglin', 'Хоглин'), ('strider', 'Страйдер'), ('sniffer', 'Нюхач'), ('turtle', 'Черепаха'), ('dolphin', 'Дельфин'),
      ('bat', 'Летучая мышь'), ('bee', 'Пчела'), ('blue', 'Аксолотль (любой)'), ('green_frog', 'Лягушка (любая)'), ('squid', 'Спрут (и светящийся)'),
      ('cod', 'Треска'), ('salmon', 'Лосось'), ('pufferfish', 'Иглобрюх'), ('creeper', 'Крипер'), ('spider', 'Паук'), ('cave_spider', 'Пещерный паук'),
      ('silverfish', 'Чешуйница'), ('endermite', 'Эндермит'), ('enderman', 'Эндермен'), ('shulker', 'Шалкер'), ('guardian', 'Страж'),
      ('warden', 'Хранитель'), ('ravager', 'Опустошитель'), ('villager', 'Житель'), ('pillager', 'Разбойник (и др. иллагеры)'), ('witch', 'Ведьма'),
      ('piglin', 'Пиглин (и брут)'), ('player', 'Игрок / человек'), ('zombie', 'Зомби (и все нежить-туши)')]
MEAT = re.compile(r'raw_|rawhuman|tentacles|chicken_cuts|^minecraft:(chicken|rabbit)$|raw_salmon')
def rng(lo, hi):
    return ('%g' % lo) if abs(lo - hi) < 1e-9 else '%g–%g' % (lo, hi)
NAMES = {'raw_chuck_steak': 'лопатка', 'raw_ribeye_steak': 'рибай', 'raw_rump_steak': 'огузок', 'raw_sirloin_steak': 'филе', 'raw_tbone_steak': 'T-bone',
         'raw_pork_shoulder': 'лопатка', 'raw_pork_loin': 'корейка', 'raw_pork_leg': 'нога', 'raw_pork_belly': 'грудинка', 'raw_ham': 'окорок',
         'raw_leg_of_lamb': 'нога', 'raw_lamb_shoulder': 'лопатка', 'raw_lamb_rib': 'рёбра', 'raw_lamb_sirloin': 'филе', 'raw_lamb_loin': 'корейка',
         'raw_chicken_leg': 'ножка', 'raw_chicken_wing': 'крыло', 'chicken': 'тушка (ваниль)', 'rabbit': 'крольчатина (ваниль)', 'rotten_flesh': 'гнилая плоть',
         'rawhumanmeat': 'мясо', 'tentacles': 'щупальца'}
print('| Туша | Этапы | Мясо, шт (что именно) | ≈ кг мяса сейчас* | Жир | Кости | Прочее съедобное | Несъедобное |')
print('|---|---|---|---|---|---|---|---|')
for key, ru in RU:
    st = C[key]
    tot = collections.defaultdict(lambda: [0.0, 0.0])
    for s, items in st:
        if s.endswith('fd_compat'):
            continue
        for i, lo, hi, f in items:
            tot[i][0] += lo; tot[i][1] += hi
    meat = {i: v for i, v in tot.items() if MEAT.search(i)}
    mlo = sum(v[0] for v in meat.values()); mhi = sum(v[1] for v in meat.values())
    parts = ', '.join('%s %s' % (NAMES.get(i.split(':')[1], i.split(':')[1].replace('raw_', '')), rng(*v)) for i, v in meat.items())
    fat = tot.get('butchery:animal_fat', [0, 0]); bone = tot.get('minecraft:bone', [0, 0])
    ed = [i for i in tot if i not in meat and i.split(':')[1] in ('honey_stomach', 'enderman_intestines', 'enderman_heart', 'brown_mushroom', 'red_mushroom', 'spider_eye', 'brain', 'heart', 'kidney', 'magma_chunks', 'slime_chunks')]
    other = [i for i in tot if i not in meat and i not in ed and i.split(':')[1] not in ('animal_fat', 'bone', 'air')]
    stages = ', '.join(sorted(set(re.sub(r'_drop.*|_\d$', '', s).replace('cut_', 'разрез ').replace('cut', 'разрез').replace('head', 'голова').replace('skin', 'шкура') for s, _ in st)))
    print('| %s | %s | **%s** (%s) | %s | %s | %s | %s | %s |' % (ru, stages, rng(mlo, mhi), parts, rng(mlo * 0.2, mhi * 0.2),
          rng(*fat) if fat[1] else '—', rng(*bone) if bone[1] else '—', ', '.join('%s %s' % (i.split(':')[1], rng(*tot[i])) for i in ed) or '—',
          ', '.join(i.split(':')[1] for i in other) or '—'))
