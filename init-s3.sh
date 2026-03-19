#!/bin/bash
awslocal s3 mb s3://truve-media

awslocal s3api put-public-access-block \
    --bucket truve-media \
    --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

cd /tmp
awslocal s3 cp "poster_temp.png" s3://truve-media/ --acl public-read
awslocal s3 cp "notice_temp_1.png" s3://truve-media/ --acl public-read
awslocal s3 cp "notice_temp_2.png" s3://truve-media/ --acl public-read
awslocal s3 cp "detail_temp_1.png" s3://truve-media/ --acl public-read
awslocal s3 cp "detail_temp_2.png" s3://truve-media/ --acl public-read
awslocal s3 cp "profile_temp.png" s3://truve-media/ --acl public-read