// Shared helpers for home.html.

// The app passes the TMDB key as ?k=…; keep a copy so pages opened without it still work.
const KEY = (() => {
  const k = new URLSearchParams(location.search).get('k');
  try { if (k) localStorage.tmdbKey = k; return k || localStorage.tmdbKey || ''; } catch { return k || ''; }
})();

async function tmdb(path, params = {}) {
  const url = new URL('https://api.themoviedb.org/3' + path);
  const headers = {};
  // Short keys are v3 API keys; the long JWT-looking ones are v4 read tokens
  if (KEY.length > 40) headers.Authorization = 'Bearer ' + KEY;
  else url.searchParams.set('api_key', KEY);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const r = await fetch(url, { headers });
  if (!r.ok) throw new Error(`TMDB ${r.status}`);
  return r.json();
}

const img = (path, size = 'w342') => path ? `https://image.tmdb.org/t/p/${size}${path}` : '';

// Normalise a TMDB movie/tv result into what the app stores and renders
function toItem(m, type) {
  type = type || m.media_type;
  return {
    id: m.id, type,
    title: m.title || m.name,
    year: (m.release_date || m.first_air_date || '').slice(0, 4),
    poster: img(m.poster_path),
    vote: m.vote_average || 0,
  };
}

// Each source gets the TMDB id, or the IMDb id for sources that prefer it (imdb: true)
const SOURCES = [
  { id: 'vidsrc', name: 'VidSrc', imdb: true,
    movie: id => `https://vidsrc.to/embed/movie/${id}`,
    tv: (id, s, e) => `https://vidsrc.to/embed/tv/${id}/${s}/${e}` },
  { id: 'vidlink', name: 'VidLink',
    movie: id => `https://vidlink.pro/movie/${id}`,
    tv: (id, s, e) => `https://vidlink.pro/tv/${id}/${s}/${e}` },
  { id: 'embedwave', name: 'EmbedWave',
    movie: id => `https://embedwave.cc/embed/movie/${id}`,
    tv: (id, s, e) => `https://embedwave.cc/embed/tv/${id}/${s}/${e}` },
];

const store = {
  read(k, d) { try { return JSON.parse(localStorage[k] ?? 'null') ?? d; } catch { return d; } },
  write(k, v) { try { localStorage[k] = JSON.stringify(v); } catch {} },
  get recents() { return this.read('recents', []); },
  set recents(r) { this.write('recents', r.slice(0, 30)); },
  // Moves the item to the front of Continue watching
  touch(item) {
    const { id, type, title, year, poster, s, e } = item;
    this.recents = [{ id, type, title, year, poster, s, e }, ...this.recents.filter(x => !(x.id === id && x.type === type))];
  },
  get watchlist() { return this.read('watchlist', []); },
  set watchlist(w) { this.write('watchlist', w); },
  inWatchlist(it) { return this.watchlist.some(x => x.id === it.id && x.type === it.type); },
  toggleWatchlist(it) {
    const { id, type, title, year, poster } = it;
    this.watchlist = this.inWatchlist(it)
      ? this.watchlist.filter(x => !(x.id === id && x.type === type))
      : [{ id, type, title, year, poster }, ...this.watchlist];
  },
  get source() { return this.read('source', 'vidsrc'); },
  set source(v) { this.write('source', v); },
};

// VidSrc is IMDb-first, so it gets the IMDb id when TMDB knows one. Cached per title.
async function imdbId(item) {
  const cache = store.read('imdbIds', {}), k = `${item.type}:${item.id}`;
  if (k in cache) return cache[k];
  try {
    const ids = await tmdb(`/${item.type}/${item.id}/external_ids`);
    cache[k] = ids.imdb_id || null;
    store.write('imdbIds', cache);
    return cache[k];
  } catch { return null; }
}

const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
