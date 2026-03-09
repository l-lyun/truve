#!/bin/bash
awslocal s3 mb s3://truve-media

awslocal s3api put-public-access-block \
    --bucket truve-media \
    --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

cd /tmp
for i in {1..5}; do
  echo "This is fake image $i" > "test_$i.png"
  awslocal s3 cp "test_$i.png" s3://truve-media/ --acl public-read
done