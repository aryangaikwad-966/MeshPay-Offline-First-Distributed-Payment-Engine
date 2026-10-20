#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
key_dir="${repo_root}/.local-keys"

for key_file in \
  recipient-private.pkcs8.b64 \
  recipient-public.spki.b64 \
  signer-public.spki.b64; do
  if [[ ! -s "${key_dir}/${key_file}" ]]; then
    printf 'Missing %s. Follow the local demo key-generation instructions in README.md.\n' "${key_dir}/${key_file}" >&2
    exit 2
  fi
done

export MESHPAY_PACKET_RSA_PRIVATE_KEY="$(tr -d '\n' < "${key_dir}/recipient-private.pkcs8.b64")"
export MESHPAY_PACKET_RSA_PUBLIC_KEY="$(tr -d '\n' < "${key_dir}/recipient-public.spki.b64")"
export MESHPAY_PACKET_TRUSTED_SIGNERS="alice@meshpay.local=$(tr -d '\n' < "${key_dir}/signer-public.spki.b64")"

cd "${repo_root}"
exec docker compose up -d "$@"
