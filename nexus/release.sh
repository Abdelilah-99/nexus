#!/usr/bin/env bash
# Cut a Buy02 release: publish an immutable version to buy02-releases, tag it in git,
# then move main to the next development snapshot.
#
#   nexus/release.sh 1.0.0            # next snapshot defaults to 1.0.1-SNAPSHOT
#   nexus/release.sh 1.1.0 1.2.0-SNAPSHOT
#
# Needs NEXUS_URL, NEXUS_USERNAME and NEXUS_PASSWORD (see nexus/maven/settings.xml).
set -euo pipefail

cd "$(dirname "$0")/.."

release=${1:?Usage: nexus/release.sh <release-version> [next-snapshot-version]}
if [[ ! $release =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Release version must look like 1.2.3" >&2
    exit 1
fi
IFS=. read -r major minor patch <<< "$release"
next=${2:-$major.$minor.$((patch + 1))-SNAPSHOT}
: "${NEXUS_URL:?Set NEXUS_URL}" "${NEXUS_USERNAME:?Set NEXUS_USERNAME}" "${NEXUS_PASSWORD:?Set NEXUS_PASSWORD}"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Commit or stash your changes first: a release must match a commit exactly." >&2
    exit 1
fi
if git rev-parse -q --verify "refs/tags/v$release" > /dev/null; then
    echo "Tag v$release already exists: released versions are never rebuilt." >&2
    exit 1
fi

mvn() { command mvn -B -s nexus/maven/settings.xml "$@"; }

# 1. Release commit + tag, so the artifacts record exactly this commit and tag
mvn -q versions:set -DnewVersion="$release"
git commit -qam "release: $release"
git tag -a "v$release" -m "Release $release"

# 2. Build, test and publish; buy02-releases refuses to overwrite an existing version
mvn clean deploy

# 3. Back to development
mvn -q versions:set -DnewVersion="$next"
git commit -qam "build: start $next"

echo
echo "Released $release to $NEXUS_URL/repository/buy02-releases/ and tagged v$release."
echo "Publish the tag and the version bump with:  git push origin main v$release"
