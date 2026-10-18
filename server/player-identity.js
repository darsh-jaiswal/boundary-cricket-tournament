// Accept only verified email identities backed by a verified Google connection.
export function googleEmail(user) {
  const verified = new Set((user?.emailAddresses || [])
    .filter(email => email.verification?.status === 'verified')
    .map(email => email.emailAddress.trim().toLowerCase()));
  const google = (user?.externalAccounts || []).find(account =>
    ['google', 'oauth_google'].includes(account.provider)
    && account.verification?.status === 'verified'
    && verified.has(account.emailAddress?.trim().toLowerCase()));
  return google?.emailAddress.trim().toLowerCase() || '';
}

export function normalizeEmail(value) {
  if (typeof value !== 'string') return '';
  const email = value.trim().toLowerCase();
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : '';
}
