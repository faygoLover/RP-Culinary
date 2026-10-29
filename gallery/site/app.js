// Галерея «Кулинария — разделка и формы»: вопросы с вариантами, роли мясных предметов, блюда с верстака.
// Ответы — в сторе хаба 'review' заплатками {patch: {id: {mark, comment} | null}}, правки разных людей не затирают друг друга.
const KINDS = ['group', 'tech', 'q', 'role', 'craft', 'analog', 'norecipe'];
const WIDE = ['q', 'analog', 'group', 'tech'];
let cards = [], decided = [], review = { items: {} }, tab = 'q';
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const HINT = {
  q: 'Спорные моменты. Пунктирная рамка ★ — что предлагаю я. Нажми вариант (повторное нажатие снимает), 💬 — комментарий. Названия предметов — из переводов и могут врать: смотри на id и спрайт.',
  role: 'Каждый сырой мясной предмет: какую роль он получает. Порция — 250 г, стак 16; мелкое — стак 32; стейк — жарить целиком или резать на порции; цельное — запекать или резать; большой кусок — только резать на доске; убрать — дубль, скрыть. Готовые (жареные) версии идут за ролью сырого.',
  analog: 'Блюда с верстака, у которых уже есть такой же или похожий рецепт на станции (кастрюля, печь, доска, миска, пресс). Решаем, что оставить.',
  norecipe: 'Готовые блюда, которые сейчас никак не делаются (только лут). Мои варианты рецептов — в тексте карточки; «только лут» — оставить как есть.',
  group: 'Новые группы экзотики (29.10), собраны заново. В карточке: что это, эффект на того, кто ест, нужно ли знание; ниже две колонки — ингредиенты и еда, которая сама не ингредиент. «так» — группа годится; «поправить» + 💬 — что убрать, добавить, перенести. Внизу — вопросы по группам.',
  tech: 'Отдельные техники (консервы, самогон, вино…) — только с перком. Без перка: «нельзя» — станция не работает; «брак» — выходит, но с настоящей опасностью; «без перка» — оставить всем. Ниже — какие перки и как работают сладости.',
  craft: 'Блюда, которые сейчас собираются на верстаке. «В готовку» — перенести в кастрюлю/печь/сковороду (куда — можно в комментарий). Отсеяны ингредиенты и не-еда.',
};

async function load() {
  const d = await (await fetch('data/cards.json', { cache: 'no-store' })).json();
  cards = d.cards; decided = d.decided;
  try { review = await (await fetch('api/review', { cache: 'no-store' })).json(); } catch (e) { review = { items: {} }; }
  review.items ??= {};
  try { tab = localStorage.getItem('culinary_tab') || 'group'; } catch (e) { /* без хранилища */ }
  for (const k of KINDS) $('#n-' + k).textContent = cards.filter(c => c.kind === k).length;
  setTab(tab);
}

// ------------------------------------------------------------------ сохранение
let saveTimer = null;
const dirty = new Set();
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    const patch = {};
    for (const id of dirty) patch[id] = review.items[id] || null;
    dirty.clear();
    $('#saved').textContent = 'сохраняю…';
    try {
      const r = await fetch('api/review', { method: 'POST', body: JSON.stringify({ patch }) });
      const j = await r.json();
      $('#saved').textContent = r.ok ? 'сохранено ' + j.saved_at : 'ошибка сохранения';
      if (r.ok) merge(j.items || {});
    } catch (e) {
      for (const id of Object.keys(patch)) dirty.add(id);
      $('#saved').textContent = 'нет связи — повторю';
      setTimeout(save, 3000);
    }
  }, 400);
}
function merge(server) {
  let changed = false;
  for (const id of new Set([...Object.keys(server), ...Object.keys(review.items)])) {
    if (dirty.has(id)) continue;
    if (JSON.stringify(review.items[id] || null) === JSON.stringify(server[id] || null)) continue;
    if (server[id]) review.items[id] = server[id]; else delete review.items[id];
    changed = true;
  }
  if (changed && KINDS.includes(tab)) refreshCards();
}
setInterval(async () => {
  if (dirty.size) return;
  try {
    const r = await fetch('api/review', { cache: 'no-store' });
    if (r.ok) merge((await r.json()).items || {});
  } catch (e) { /* нет связи — позже */ }
}, 15000);

function setVal(id, key, val) {
  const r = review.items[id] ??= {};
  if (val === undefined || val === '') delete r[key]; else r[key] = val;
  if (!Object.keys(r).length) delete review.items[id];
  dirty.add(id);
}

// ------------------------------------------------------------------ вкладки
function setTab(t) {
  tab = t;
  try { localStorage.setItem('culinary_tab', t); } catch (e) { /* без хранилища */ }
  document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('on', b.dataset.tab === t));
  const grid = KINDS.includes(t);
  $('#grid').hidden = !grid;
  $('#text').hidden = grid;
  $('#filters').style.display = grid ? '' : 'none';
  $('#hint').textContent = HINT[t] || '';
  $('#hint').hidden = !grid;
  if (t === 'done') return renderDone();
  if (t === 'out') return renderOut();
  $('#grid').classList.toggle('wide', WIDE.includes(t));
  render();
}

function visible() {
  const q = $('#q').value.trim().toLowerCase(), mk = $('#mark').value;
  return cards.filter(c => {
    if (c.kind !== tab) return false;
    const r = review.items[c.id] || {};
    if (mk === 'none' && r.mark) return false;
    if (mk === 'ans' && !r.mark) return false;
    if (mk === 'cm' && !r.comment) return false;
    if (q && !(c.title + ' ' + c.text.join(' ') + ' ' + c.items.concat(...(c.cols || []).map(x => x[1])).map(i => i.id + ' ' + i.name).join(' ')).toLowerCase().includes(q)) return false;
    return true;
  });
}

function render() {
  const list = visible();
  $('#shown').textContent = 'показано ' + list.length;
  const html = [];
  let g = null;
  for (const c of list) {
    if (c.group !== g) {
      g = c.group;
      html.push(`<div class="grp">${esc(g)} <span>${list.filter(x => x.group === g).length}</span></div>`);
    }
    html.push(card(c));
  }
  $('#grid').innerHTML = html.join('') || '<p style="color:var(--dim)">ничего не найдено</p>';
}

function itemHtml(i) {
  return `<div class="it">${i.img ? `<img loading="lazy" src="${esc(i.img)}" alt="">` : ''}<div>
    <div class="id">${esc(i.id)}</div><div class="nm">${esc(i.name || '—')}</div>
    ${i.nin ? `<div class="nin">в рецептах: ${i.nin}</div>` : ''}</div></div>`;
}

function card(c) {
  const r = review.items[c.id] || {};
  const one = !WIDE.includes(c.kind);
  const cols = c.cols ? `<div class="cols">${c.cols.map(([h, its]) => `<div class="col"><div class="ch">${esc(h)} <span>${its.length}</span></div><div class="items">${its.map(itemHtml).join('') || '<div class="nin">—</div>'}</div></div>`).join('')}</div>` : '';
  const txt = c.text.map(l => l.startsWith('•') ? `<div class="li">${esc(l)}</div>` : `<div>${esc(l)}</div>`).join('');
  return `<div class="card ${one ? 'one' : ''} ${r.mark ? 'ans' : ''}" data-id="${esc(c.id)}">
    ${!one ? `<div class="ttl">${esc(c.title)}</div>` : ''}
    ${one ? `<div class="items">${c.items.map(itemHtml).join('')}</div>` : ''}
    <div class="txt">${txt}</div>
    ${one ? '' : `<div class="items">${c.items.map(itemHtml).join('')}</div>`}
    ${cols}
    ${r.comment ? `<div class="cm">💬 ${esc(r.comment)}</div>` : ''}
    <div class="opts">${c.options.map(([k, lbl]) =>
      `<button class="${r.mark === k ? 'on' : ''} ${c.rec === k ? 'rec' : ''}" data-m="${esc(k)}">${c.rec === k ? '★ ' : ''}${esc(lbl)}</button>`).join('')}
      <button class="c" title="комментарий">💬</button></div></div>`;
}

function refreshCards() {
  const byId = new Map(cards.map(c => [c.id, c]));
  document.querySelectorAll('.card').forEach(el => {
    const t = document.createElement('div');
    t.innerHTML = card(byId.get(el.dataset.id));
    el.replaceWith(t.firstElementChild);
  });
}

$('#grid').addEventListener('click', e => {
  const btn = e.target.closest('button'), el = e.target.closest('.card');
  if (!btn || !el) return;
  const id = el.dataset.id, r = review.items[id] || {};
  if (btn.classList.contains('c')) {
    const c = prompt('Комментарий (пусто — убрать):', r.comment || '');
    if (c === null) return;
    setVal(id, 'comment', c.trim());
  } else {
    setVal(id, 'mark', r.mark === btn.dataset.m ? undefined : btn.dataset.m);
  }
  const t = document.createElement('div');
  t.innerHTML = card(cards.find(x => x.id === id));
  el.replaceWith(t.firstElementChild);
  save();
});

$('#bulk-rec').onclick = () => {
  const list = visible().filter(c => !(review.items[c.id] || {}).mark && c.rec);
  if (!list.length || !confirm(`Принять мои предложения ★ для ${list.length} показанных карточек без ответа?`)) return;
  list.forEach(c => setVal(c.id, 'mark', c.rec));
  render();
  save();
};

// ------------------------------------------------------------------ решено / итог
function renderDone() {
  $('#text').innerHTML = '<h3>Уже решено (28.10)</h3><ul>' + decided.map(d => `<li>${esc(d)}</li>`).join('') + '</ul>';
}
function renderOut() {
  const lines = [];
  let ans = 0;
  for (const c of cards) {
    const r = review.items[c.id] || {};
    if (!r.mark && !r.comment) continue;
    ans++;
    const opt = (c.options.find(o => o[0] === r.mark) || [, '—'])[1];
    lines.push(`${c.kind === 'q' ? c.title : c.title} → ${opt}${r.comment ? '  // ' + r.comment : ''}`);
  }
  $('#text').innerHTML = `<p>Отвечено: ${ans} из ${cards.length}.</p><textarea readonly></textarea>`;
  $('#text textarea').value = lines.join('\n');
}

document.querySelectorAll('#tabs button').forEach(b => b.onclick = () => setTab(b.dataset.tab));
['#q', '#mark'].forEach(s => $(s).addEventListener('input', render));
load();
