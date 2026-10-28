# Продолжение content.py (29.10): праздничные блоки FD и блюда без рецепта.
from content import ANALOG

ANALOG.append(
    ('Похожее блюдо другого мода', 'a:feast', 'Праздничные блюда-блоки FD',
     ['Окорок в медовой глазури, пастуший пирог, запечённая курица — на верстаке из готовых частей; потом их ставят и раскладывают по тарелкам.',
      'Предлагаю: собрать на верстаке или в миске «сырую» версию → запечь в печи. Салатный блок и ассорти роллов оставить сборкой.',
      '⚠ Рецепт окорока в глазури ссылается на butchery:smoked_ham — такого предмета нет (копчёный окорок — FD smoked_ham).'],
     ['farmersdelight:honey_glazed_ham_block', 'farmersdelight:shepherds_pie_block', 'farmersdelight:roast_chicken_block', 'farmersdelight:gleaming_salad_block', 'farmersdelight:rice_roll_medley_block'], 'station'))

FEAST = ['farmersdelight:honey_glazed_ham_block', 'farmersdelight:shepherds_pie_block', 'farmersdelight:roast_chicken_block',
         'farmersdelight:gleaming_salad_block', 'farmersdelight:rice_roll_medley_block']

NORECIPE_OPTS = [['mine', 'мой рецепт'], ['loot', 'только лут'], ['drop', 'убрать'], ['edit', 'иначе']]
# (группа, id предмета, предложение, rec)
NORECIPE = [
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_margherita', 'Доска Refurbished: тесто + томатный соус + моцарелла → сырая → духовка', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_neopolitan', 'тесто + томат (свежий) + моцарелла', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_cheese', 'тесто + соус + сыр ×2', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_bianca', 'тесто + сыр + молоко (без соуса)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_pepperoni', 'тесто + соус + сыр + жареные сосиски', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_hawaiian', 'тесто + соус + сыр + окорок + ананас (консервы SA)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_chicken', 'тесто + соус + сыр + жареное куриное филе + кукуруза', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_taco', 'тесто + соус + жареный фарш + кукуруза + томат', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_olive_onion', 'тесто + соус + сыр + лук (оливок в сборке нет)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_greenpepper', 'нужен перец — в сборке нет (🟩 новый овощ); пока только лут', 'loot'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_swedish', 'тесто + соус + сыр + банан + курица («шведская банановая»)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_deepdish', 'тесто ×2 + соус + сыр ×2 + фарш (толстая)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_square', 'тесто ×2 + соус + сыр (на противне)', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_colzone', 'тесто + сыр + окорок, сложенная', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_biscuit', 'сладкая: тесто + сахар + ягоды', 'mine'),
    ('Пицца SCP:O → духовка Refurbished', 'scpo:pizza_fleshy', 'аномальная (плоть) — только лут SCP', 'loot'),
    ('Пицца SCP:O → духовка Refurbished', 'sa_combat:pizza', 'кусок пиццы SA — дубль куска Refurbished; только лут', 'loot'),
    ('Фастфуд и выпечка', 'sa_combat:hot_dog', 'Доска: булка или ломтик хлеба + 1 жареная сосиска (¼ связки) + лук', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:taco', 'Сковорода: тесто (лепёшка) + жареный фарш + салат + томат + сыр', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:burrito', 'Сковорода: тесто + жареный фарш + кукуруза + сыр (+ фасоль, когда появится)', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:donut', 'Сковорода (фритюр): тесто + сахар + жир', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:waffles', 'Плита F&C: тесто + яйцо + молоко + сахар', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:biscuit', 'Плита / духовка: мука + масло + молоко', 'mine'),
    ('Фастфуд и выпечка', 'scpo:cupcake', 'Духовка: тесто + яйцо + сахар + молоко', 'mine'),
    ('Фастфуд и выпечка', 'survival_instinct:rice_cookie', 'Духовка: рис + сахар + яйцо', 'mine'),
    ('Фастфуд и выпечка', 'sa_combat:chips', 'Сковорода: картофель + жир + соль (чипсы SA в пакетах — лут)', 'mine'),
    ('Фастфуд и выпечка', 'survival_instinct:cheese_chips', 'как чипсы + сыр, или только лут', 'loot'),
    ('Фастфуд и выпечка', 'sa_combat:cereal_fruit_loops', 'фабричные хлопья — только лут', 'loot'),
    ('Консервы', 'zombie_extreme:canned_stew', 'Консервирование: тушёная говядина FD + пустая банка (кастрюля)', 'mine'),
    ('Консервы', 'zombie_extreme:canned_soup', 'Консервирование: любой суп + банка', 'mine'),
    ('Консервы', 'zombie_extreme:canned_food', 'консервы «вообще» — только лут', 'loot'),
    ('Консервы', 'sa_combat:canned_corn', 'Консервирование: кукуруза + вода + соль + банка', 'mine'),
    ('Консервы', 'sa_combat:canned_spaghetti_bolognese', 'Консервирование: спагетти болоньезе Candlelight + банка', 'mine'),
    ('Консервы', 'sa_combat:canned_fruit_beverage', 'Консервирование: сок + сахар + банка', 'mine'),
    ('Консервы', 'survival_instinct:military_can', 'армейские консервы — только лут', 'loot'),
    ('Консервы', 'survival_instinct:mre', 'армейский паёк (ИРП) — только лут', 'loot'),
    ('Консервы', 'sa_combat:canned_dog_food', 'корм — лут (или консервирование обрезков)', 'loot'),
    ('Консервы', 'zombie_extreme:dog_food', 'корм — лут', 'loot'),
    ('Консервы', 'zombie_extreme:cat_food', 'корм — лут', 'loot'),
    ('Напитки', 'sa_combat:vodka', 'Brewery: картофель или пшеница + дрожжи → брага → перегонка (🟩 самогонный аппарат); или лут', 'mine'),
    ('Напитки', 'zombie_extreme:vodka', 'дубль водки — свести тегом к одной', 'loot'),
    ('Напитки', 'survival_instinct:tequila', 'Brewery: кактус + дрожжи; или лут', 'loot'),
    ('Напитки', 'sa_combat:beer', 'дубль пива Brewery — только лут', 'loot'),
    ('Напитки', 'survival_instinct:beer', 'дубль пива — только лут', 'loot'),
    ('Напитки', 'sa_combat:wine', 'дубль вина Vinery — только лут', 'loot'),
    ('Напитки', 'survival_instinct:wine', 'дубль вина — только лут', 'loot'),
    ('Напитки', 'survival_instinct:whiskey', 'дубль виски Brewery — только лут', 'loot'),
    ('Напитки', 'sa_combat:coffee', 'дубль кофе HerbalBrews — лут', 'loot'),
    ('Напитки', 'scpo:papercup_coffee', 'кофемашина SCP:O (блок) — оставить', 'loot'),
    ('Напитки', 'scpo:papercup_water', 'кулер SCP:O — оставить', 'loot'),
    ('Напитки', 'scpo:coke_can', 'газировки — автомат / лут', 'loot'),
    ('Напитки', 'survival_instinct:energy_can', 'энергетики — лут', 'loot'),
    ('Напитки', 'survival_instinct:bottle_of_water', 'вода в бутылке — очистка воды LSO или лут', 'loot'),
    ('Напитки', 'alexscaves:purple_soda_bottle', 'фиолетовая газировка — из озёр Конфетных пещер', 'loot'),
    ('Сладости', 'scpo:candyblue', 'сахарная вата — 🟩 аппарат или лут', 'loot'),
    ('Сладости', 'scpo:candy_pink', 'аномальные конфеты SCP — только лут', 'loot'),
    ('Сладости', 'scpo:zombie_candy', 'аномальные — только лут', 'loot'),
    ('Сладости', 'alexsmobs:rainbow_jelly', 'дроп гребневика — оставить', 'loot'),
    ('Прочее', 'alexsmobs:shrimp_fried_rice', 'механика Alex\'s Mobs: рак-богомол жарит рис — оставить', 'loot'),
    ('Прочее', 'farmersdelight:shepherds_pie', 'порция с блока «пастуший пирог» — так и задумано', 'loot'),
    ('Прочее', 'farmersdelight:honey_glazed_ham', 'порция с блока «окорок в глазури» — так и задумано', 'loot'),
    ('Прочее', 'alexsmobs:maggot', 'личинки — дроп, сырой «ингредиент» выживания', 'loot'),
    ('Прочее', 'alexsmobs:leafcutter_ant_pupa', 'дроп муравейника — оставить', 'loot'),
]


# ---------------------------------------------------------------- 29.10: новые вопросы
from content import q
G5 = 'Новое (29.10)'
q(G5, 'mince_any', 'Фарш из любого мяса',
  ['Да, в мясорубку можно пустить любое сырое мясо: порцию, стейк, отруб, обрезки, субпродукты (сердце, печень — в «ливерный» фарш).',
   '• порция 250 г → фарш 250 г × (1 − потери)',
   '• обрезки: 2 шт. (500 г) → 1 фарш — в обрезках жилы и хрящи',
   '• стейк в мясорубку можно, но расточительно — без запретов',
   'Потери мясорубки: 5–15 %, меньше с навыком; потерянное уходит в обрезки (костная крошка, жилы), а не пропадает совсем.'],
  ['butchery:meat_scraps', 'farm_and_charm:minced_beef', 'butchery:meat_grinder', 'farm_and_charm:mincer'],
  [['ok', 'так'], ['nolos', 'без потерь'], ['edit', 'иначе']], 'ok')
q(G5, 'mince_mix', 'Смешанный фарш',
  ['Положили в мясорубку свинину и говядину → «Фарш (говядина, свинина)»: состав в NBT, калории и свойства — по долям, цвет — средний.',
   'Котлеты/пельмени из смешанного фарша — нормальная домашняя кухня. Экзотика в смеси видна в подсказке (чтобы «тайный фарш» из волка был ролевым ходом, а не багом).'],
  ['farm_and_charm:minced_beef', 'butchery:raw_lamb_mince'],
  [['yes', 'да, смешанный'], ['no', 'только один вид']], 'yes')
q(G5, 'fat', 'Жир → смалец / масло для жарки',
  ['Жир-сырец (butchery:animal_fat) в кастрюле → смалец (жир для жарки, долго хранится) + шкварки.',
   'Смалец — «масло» для сковороды/фритюра (чипсы, пончики, жареное) — пока нет растительного. Сало (бывший F&C bacon) — солится отдельно.'],
  ['butchery:animal_fat', 'butchery:crackling', 'farm_and_charm:bacon'],
  [['ok', 'так'], ['edit', 'иначе']], 'ok')
q(G5, 'salt', 'Соль: одна на всё',
  ['6 видов соли (Butchery, Mekanism, CS ×2, Refurbished морская, Blocks You Need каменная) — свести тегом forge:salt, во всех рецептах любая.',
   'Соль нужна для засолки сала, рыбы, капусты (когда будут) и сыра.'],
  ['butchery:salt', 'mekanism:salt', 'csaugmentations:salt', 'refurbished_furniture:sea_salt', 'blocksyouneed_luna:rock_salt'],
  [['ok', 'да, любая'], ['edit', 'иначе']], 'ok')
q(G5, 'cook_weight', 'Вес после готовки',
  ['Жарка −25–35 % массы, варка −15 %, копчение и вяление −40 %; калории куска почти те же. В подсказке показываем вес: «Стейк, 280 г».',
   'Нужно, чтобы стак и вес готового совпадали с ожиданием игрока (жареных порций в стаке столько же, сколько сырых).'],
  ['minecraft:cooked_beef', 'butchery:cooked_ribeye_steak'],
  [['ok', 'так'], ['no', 'не показывать вес']], 'ok')
q(G5, 'board_xp', 'Нарезка на доске — тоже навык',
  ['Каждая нарезка (отруб → порции, тушка → части) даёт немного опыта кулинарии; на низком навыке часть порций уходит в обрезки (как разделка).',
   'Порог доски — как у туши, с которой кусок (лопатка коровы — 5).'],
  ['farmersdelight:cutting_board', 'butchery:raw_chuck_steak'],
  [['ok', 'так'], ['lower', 'порог доски ниже, чем у туши'], ['edit', 'иначе']], 'ok')
q(G5, 'carcass_weight', 'Вес туши при переноске',
  ['При K = 0.25 туша коровы весит ~60–70 кг (с костями, шкурой). У Butchery включён «вес туш» (замедление). Оставить как есть, или замедление по реальному весу (корова — почти не утащить одному, свинья — тяжело, курица — легко)?'],
  ['butchery:raw_chuck_steak'],
  [['real', 'по весу туши'], ['keep', 'как сейчас в Butchery']], 'real')
