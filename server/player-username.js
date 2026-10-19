// Usernames are public cricket handles; emails remain private lookup identifiers.
export function normalizeUsername(value) {
  if (typeof value !== 'string') return null;
  const name = value.trim().replace(/^@/, '').toLowerCase();
  return /^[a-z][a-z0-9_]{2,23}$/.test(name) && !name.startsWith('user_') ? name : null;
}
