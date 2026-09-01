#!/usr/bin/env bash
# Run AFTER the dhun-deploy PRIVATE key is saved to ~/.ssh/dhun-deploy
set -euo pipefail
[ -f ~/.ssh/dhun-deploy ] || { echo "missing ~/.ssh/dhun-deploy (private key)"; exit 1; }
chmod 600 ~/.ssh/dhun-deploy
git -C /home/user/DHUN remote remove origin 2>/dev/null || true
git -C /home/user/DHUN remote add origin git@github.com:99ggprooo00-code/DHUN.git
ssh -o StrictHostKeyChecking=accept-new -T git@github.com || true
git -C /home/user/DHUN push -u origin main
