# Shared S3 backend settings for every dev stack. The key is per stack, so pass it on init:
#   terraform -chdir=infra/stacks/<stack> init \
#     -backend-config=../../envs/dev.backend.hcl -backend-config=key=devcool/dev/<stack>.tfstate
bucket       = "devcool-tfstate-725069913301"
region       = "us-east-1"
encrypt      = true
use_lockfile = true
