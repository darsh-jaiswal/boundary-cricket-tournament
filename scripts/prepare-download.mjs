import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readFile, rename, rm, stat } from 'node:fs/promises';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

// Publish the exact signed release as a static CDN asset, never through a Function.
const release = JSON.parse(await readFile(new URL('../web/android-release.json', import.meta.url)));
if (!/^https:\/\/github\.com\/darsh-jaiswal\/boundary-cricket-tournament\/releases\/download\/v[\d.]+\/Boundary-Android-arm64\.apk$/.test(release.source)
    || !/^[a-f0-9]{64}$/.test(release.sha256)
    || !Number.isSafeInteger(release.bytes) || release.bytes <= 0 || release.bytes > 100_000_000) {
  throw new Error('Invalid Android release manifest');
}
const folder = new URL('../web/downloads/', import.meta.url);
await mkdir(folder, { recursive: true });
const destination = new URL('Boundary-Android-arm64.apk', folder);
const temporary = new URL('Boundary-Android-arm64.apk.part', folder);
try {
  const response = await fetch(release.source, { signal: AbortSignal.timeout(240_000) });
  if (!response.ok || !response.body) throw new Error(`APK download returned ${response.status}`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(temporary));
  if ((await stat(temporary)).size !== release.bytes) throw new Error('APK byte count mismatch');
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(temporary)) hash.update(chunk);
  if (hash.digest('hex') !== release.sha256) throw new Error('APK SHA-256 mismatch');
  await rename(temporary, destination);
  console.log(`Verified signed Android ${release.version}: ${release.bytes} bytes`);
} finally {
  await rm(temporary, { force: true });
}
