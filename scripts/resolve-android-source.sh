#!/usr/bin/env bash
set -euo pipefail

workspace="${1:-$PWD}"
incoming_dir="$workspace/incoming-source"
project_dir="$workspace"
source_kind="repository"

write_output() {
  local key="$1"
  local value="$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
  else
    printf '%s=%s\n' "$key" "$value"
  fi
}

validate_listing() {
  local listing_file="$1"
  local file_count
  file_count="$(wc -l < "$listing_file" | tr -d ' ')"
  if (( file_count == 0 || file_count > 5000 )); then
    echo "Source archive must contain 1 to 5000 files." >&2
    exit 1
  fi
  if grep -Eq '(^/|(^|/)\.\.(/|$)|\\)' "$listing_file"; then
    echo "Source archive contains an unsafe path." >&2
    exit 1
  fi
}

shopt -s nullglob
archives=("$incoming_dir"/*.zip "$incoming_dir"/*.tar.gz "$incoming_dir"/*.tgz)
shopt -u nullglob

if (( ${#archives[@]} > 1 )); then
  echo "Only one source archive is allowed in incoming-source/ per build." >&2
  exit 1
fi

if (( ${#archives[@]} == 1 )); then
  archive="${archives[0]}"
  listing_file="$(mktemp)"
  trap 'rm -f "$listing_file"' EXIT
  extract_dir="$workspace/.automation-source"
  rm -rf "$extract_dir"
  mkdir -p "$extract_dir"

  case "$archive" in
    *.zip)
      unzip -Z1 "$archive" > "$listing_file"
      validate_listing "$listing_file"
      unzip -q "$archive" -d "$extract_dir"
      ;;
    *.tar.gz|*.tgz)
      tar -tzf "$archive" > "$listing_file"
      validate_listing "$listing_file"
      tar -xzf "$archive" -C "$extract_dir" --no-same-owner --no-same-permissions
      ;;
    *)
      echo "Unsupported archive type." >&2
      exit 1
      ;;
  esac

  if find "$extract_dir" -type l -print -quit | grep -q .; then
    echo "Source archive may not contain symbolic links." >&2
    exit 1
  fi

  wrapper="$(find "$extract_dir" -type f -name gradlew -print -quit)"
  if [[ -z "$wrapper" ]]; then
    echo "No Gradle wrapper found in source archive." >&2
    exit 1
  fi

  project_dir="$(dirname "$wrapper")"
  source_kind="archive:$(basename "$archive")"
fi

if [[ ! -f "$project_dir/gradlew" || ! -f "$project_dir/app/build.gradle.kts" ]]; then
  echo "Android project must contain gradlew and app/build.gradle.kts." >&2
  exit 1
fi

version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$project_dir/app/build.gradle.kts" | head -n 1)"
if [[ -z "$version_name" ]]; then
  echo "Unable to read versionName from app/build.gradle.kts." >&2
  exit 1
fi

write_output "project_dir" "$project_dir"
write_output "version" "$version_name"
write_output "source_kind" "$source_kind"
