const matchId = new URLSearchParams(location.search).get('match');
const nodes = Object.fromEntries(['team', 'total', 'overs', 'situation', 'status', 'top']
  .map(id => [id, document.getElementById(id)]));
let fetching = false;
let lastRevision = null;
let lastPublication = null;

function publicationAge() {
  if (lastPublication === null) return '';
  const seconds = Math.max(0, Math.floor((Date.now() - lastPublication) / 1000));
  return ` · Scorer published ${seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m`} ago`;
}

async function refresh() {
  if (fetching || !matchId) return;
  fetching = true;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`/api/matches?id=${encodeURIComponent(matchId)}`, {
      cache: 'no-store', signal: controller.signal,
    });
    if (!response.ok) throw new Error('Score service unavailable');
    const match = await response.json();
    if (match === null) {
      nodes.team.textContent = 'Boundary cricket';
      nodes.total.textContent = '— / —';
      nodes.overs.textContent = 'Waiting for score';
      nodes.situation.textContent = 'No public score available for this match.';
      nodes.top.classList.remove('changed');
      lastRevision = null;
      lastPublication = null;
      nodes.status.textContent = 'Match is not published. Waiting for the scorer.';
      return;
    }
    if (match.id !== matchId || !Number.isInteger(match.runs) || match.runs < 0
        || !Number.isInteger(match.wickets) || match.wickets < 0
        || typeof match.battingTeam !== 'string' || typeof match.overs !== 'string') {
      throw new Error('Invalid scoreboard data');
    }
    nodes.team.textContent = match.battingTeam;
    nodes.total.textContent = `${match.runs} / ${match.wickets}`;
    nodes.overs.textContent = `${match.overs} overs`;
    const target = Number.isInteger(match.target) && match.target > 0 ? match.target : 0;
    const remaining = Number.isInteger(match.remaining) && match.remaining >= 0 ? match.remaining : null;
    nodes.situation.textContent = typeof match.result === 'string' && match.result !== 'Live'
      ? match.result
      : target
        ? `Target ${target} · Need ${Math.max(0, target - match.runs)}${remaining === null ? '' : ` from ${remaining} balls`}`
        : `${typeof match.name === 'string' ? match.name : 'Cricket'} · First innings`;
    lastPublication = Number.isFinite(match.updated) && match.updated > 0 ? match.updated : null;
    nodes.status.textContent = `Score service connected${publicationAge()}`;
    if (Number.isInteger(match.revision)) {
      if (lastRevision !== null && lastRevision !== match.revision) {
        nodes.top.classList.remove('changed');
        void nodes.top.offsetWidth;
        nodes.top.classList.add('changed');
      }
      lastRevision = match.revision;
    }
  } catch (error) {
    nodes.status.textContent = `Updates interrupted · Showing last received score${publicationAge()} · Retrying`;
    if (lastRevision === null) nodes.status.textContent = 'Cannot load score yet · Retrying';
  } finally {
    clearTimeout(timeout);
    fetching = false;
  }
}

if (matchId && /^[a-f0-9-]{36}$/i.test(matchId)) {
  refresh();
  // OBS browser sources may report hidden while still being composited into a stream.
  setInterval(refresh, 3000);
} else if (matchId) {
  nodes.status.textContent = 'Invalid match ID. Use the overlay link shared from Boundary.';
}
