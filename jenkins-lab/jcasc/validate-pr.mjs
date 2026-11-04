// Controller-owned validator; never load this script from a pull request checkout.
import { writeFileSync } from 'node:fs';

const number = process.env.PR_NUMBER;
if (!/^[1-9][0-9]{0,8}$/.test(number ?? '')) {
    console.error('PR_NUMBER must be a positive pull request number.');
    process.exit(1);
}
const repository = 'AyyoubMh48/buy-02';
const response = await fetch(`https://api.github.com/repos/${repository}/pulls/${number}`, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'buy02-jenkins-pr-validation' },
    signal: AbortSignal.timeout(15000)
});
if (!response.ok) {
    console.error(`Cannot read pull request metadata: HTTP ${response.status}`);
    process.exit(1);
}
const pr = await response.json();
// Builds can access the lab Docker daemon: only manually selected team-owned PRs.
if (pr.head?.repo?.full_name !== repository || pr.base?.ref !== 'main'
    || !['OWNER', 'MEMBER', 'COLLABORATOR'].includes(pr.author_association)
    || !/^[a-f0-9]{40}$/.test(pr.head?.sha ?? '')) {
    console.error('Only trusted team PRs from this repository into main can run in this Jenkins lab.');
    process.exit(1);
}
writeFileSync('pr-head.sha', pr.head.sha + '\n');
console.log(`Validating PR #${number}, head ${pr.head.sha}, merged with current main.`);
