#!/usr/bin/env bash
printf 'Quickstarting the project - please wait...\n'

printf "Adding mock google-services json file\n"
pushd "app/src/env" > /dev/null

echo "{
  \"project_info\": {
    \"project_number\": \"623252783566\",
    \"firebase_url\": \"https://blockchaintest-ecd1c.firebaseio.com\",
    \"project_id\": \"blockchaintest-ecd1c\",
    \"storage_bucket\": \"blockchaintest-ecd1c.appspot.com\"
  },
  \"client\": [
    {
      \"client_info\": {
        \"mobilesdk_app_id\": \"1:623252783566:android:02baff6e6c46ed96232b9f\",
        \"android_client_info\": {
          \"package_name\": \"piuk.blockchain.android.dev\"
        }
      },
      \"oauth_client\": [
        {
          \"client_id\": \"623252783566-o6j47jlpan97fnibnr0vosvc4lh71sm1.apps.googleusercontent.com\",
          \"client_type\": 3
        }
      ],
      \"api_key\": [
        {
          \"current_key\": \"INSERT KEY HERE\"
        }
      ],
      \"services\": {
        \"appinvite_service\": {
          \"other_platform_oauth_client\": [
            {
              \"client_id\": \"623252783566-o6j47jlpan97fnibnr0vosvc4lh71sm1.apps.googleusercontent.com\",
              \"client_type\": 3
            }
          ]
        }
      }
    }
  ],
  \"configuration_version\": \"1\"
}" >> google-services.json

popd > /dev/null

printf "Google services json copy complete\n"

printf "Quickstart complete!\n"