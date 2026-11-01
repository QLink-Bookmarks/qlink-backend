#!/bin/bash
set -euo pipefail

BUCKETS="${AWS_S3_BUCKETS:-alink-profiles-local alink-posts-local alink-previews-local}"
REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"

for BUCKET in $BUCKETS; do
    awslocal s3api create-bucket \
        --bucket "$BUCKET" \
        --region "$REGION" \
        --create-bucket-configuration LocationConstraint="$REGION" 2>/dev/null || true

    echo "LocalStack S3 bucket '${BUCKET}' is ready."
done
