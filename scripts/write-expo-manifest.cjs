// Run from the repository root after the final local build.
const fs = require('fs'), cp = require('child_process'), hash = require('crypto');
const git = args => cp.execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim().split(/\r?\n/).filter(Boolean);
const files = [...new Set([...git(['diff', '--name-only', 'HEAD']), ...git(['ls-files', '--others', '--exclude-standard'])])]
  .filter(p => fs.existsSync(p) && fs.statSync(p).isFile() && !p.startsWith('backend/build'));
const sha = p => hash.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const manifest = {
  baseline: git(['rev-parse', 'HEAD'])[0],
  backendJarSha256: sha('backend/build/libs/blackbox-0.0.1-SNAPSHOT.jar'),
  nextBuildId: fs.readFileSync('frontend/.next/BUILD_ID', 'utf8').trim(),
  files: files.map(p => ({ path: p, sha256: sha(p) })),
};
fs.writeFileSync('../outputs/expo-20261016/release-manifest.json', JSON.stringify(manifest, null, 2));
console.log(`Local source/artifact manifest saved; files=${files.length}`);
