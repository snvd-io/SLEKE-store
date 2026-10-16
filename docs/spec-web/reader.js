const contents = document.querySelector('.contents');
const narrowScreen = window.matchMedia('(max-width: 800px)');
if (narrowScreen.matches) contents.open = false;
narrowScreen.addEventListener('change', event => { contents.open = !event.matches; });

const progress = document.querySelector('#reading-progress');
function updateProgress() {
  const range = document.documentElement.scrollHeight - window.innerHeight;
  progress.style.width = `${range > 0 ? window.scrollY / range * 100 : 0}%`;
}
window.addEventListener('scroll', updateProgress, { passive: true });
updateProgress();

const navLinks = [...document.querySelectorAll('nav a')];
const observer = new IntersectionObserver(entries => {
  for (const entry of entries) {
    if (!entry.isIntersecting) continue;
    navLinks.forEach(link => {
      if (link.hash === `#${entry.target.id}`) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }
}, { rootMargin: '-90px 0px -65% 0px', threshold: 0 });
document.querySelectorAll('main section').forEach(section => observer.observe(section));
navLinks.forEach(link => link.addEventListener('click', () => {
  if (narrowScreen.matches) contents.open = false;
}));

const search = document.querySelector('#requirement-search');
const groups = [...document.querySelectorAll('.capability')];
const cards = [...document.querySelectorAll('.requirement')];
const status = document.querySelector('#search-status');
const empty = document.querySelector('#no-results');
search?.addEventListener('input', () => {
  const query = search.value.trim().toLocaleLowerCase();
  let visible = 0;
  let groupCount = 0;
  cards.forEach(card => {
    card.hidden = query !== '' && !card.dataset.search.includes(query);
    if (!card.hidden) visible++;
  });
  groups.forEach((group, index) => {
    group.hidden = ![...group.querySelectorAll('.requirement')].some(card => !card.hidden);
    if (!group.hidden) groupCount++;
    if (query) group.open = !group.hidden;
    else group.open = index === 0;
  });
  status.textContent = `${visible} ${visible === 1 ? 'requirement' : 'requirements'} across ${groupCount} ${groupCount === 1 ? 'capability' : 'capabilities'}`;
  empty.hidden = visible > 0;
});
document.addEventListener('keydown', event => {
  const editing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || '') || document.activeElement?.isContentEditable;
  if (search && event.key === '/' && !editing) { event.preventDefault(); search.focus(); }
  if (search && event.key === 'Escape' && document.activeElement === search) {
    search.value = '';
    search.dispatchEvent(new Event('input'));
    search.blur();
  }
});
document.querySelector('#print-spec').addEventListener('click', () => window.print());
