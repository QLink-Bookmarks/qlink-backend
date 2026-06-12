#!/bin/bash
set -euo pipefail

BUCKET="${AWS_S3_BUCKET:-qlink-images-local}"
REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"

awslocal s3api create-bucket \
    --bucket "$BUCKET" \
    --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION" 2>/dev/null || true

awslocal s3api put-bucket-policy --bucket "$BUCKET" --policy "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [
        {
            \"Sid\": \"PublicRead\",
            \"Effect\": \"Allow\",
            \"Principal\": \"*\",
            \"Action\": \"s3:GetObject\",
            \"Resource\": \"arn:aws:s3:::${BUCKET}/*\"
        }
    ]
}"

echo "LocalStack S3 bucket '${BUCKET}' is ready."
