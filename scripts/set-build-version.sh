#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:?project directory is required}"
version_code="${2:?version code is required}"
version_name="${3:?version name is required}"
gradle_file="$project_dir/app/build.gradle.kts"

[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid versionCode: $version_code" >&2; exit 1; }
[[ "$version_name" != *'"'* && "$version_name" != *$'\n'* ]] || { echo "Invalid versionName" >&2; exit 1; }
[[ -f "$gradle_file" ]] || { echo "Missing $gradle_file" >&2; exit 1; }

sed -E -i "0,/versionCode[[:space:]]*=[[:space:]]*[0-9]+/s//versionCode = ${version_code}/" "$gradle_file"
sed -E -i "0,/versionName[[:space:]]*=[[:space:]]*\"[^\"]+\"/s//versionName = \"${version_name}\"/" "$gradle_file"

grep -q "versionCode = ${version_code}" "$gradle_file" || { echo "versionCode injection failed" >&2; exit 1; }
grep -q "versionName = \"${version_name}\"" "$gradle_file" || { echo "versionName injection failed" >&2; exit 1; }
printf 'versionCode=%s\nversionName=%s\n' "$version_code" "$version_name"
