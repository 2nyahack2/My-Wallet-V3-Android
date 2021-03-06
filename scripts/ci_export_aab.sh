#!/usr/bin/env bash
# fail if any commands fails
set -e
# debug log
set -x

aab_path=`echo $1/*.aab | cut -d ' ' -f1`

if [ $2 = true ]; then
	jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $BITRISEIO_ANDROID_KEYSTORE_URL $aab_path "$BITRISEIO_ANDROID_KEYSTORE_ALIAS" -storepass $BITRISEIO_ANDROID_KEYSTORE_PASSWORD -keypass $BITRISEIO_ANDROID_KEYSTORE_PRIVATE_KEY_PASSWORD
fi

cp $aab_path /bitrise/deploy/

bitrise_aab_path=`echo /bitrise/deploy/*.aab | cut -d ' ' -f1`

envman add --key BITRISE_AAB_PATH --value $bitrise_aab_path

envman add --key BITRISE_AAB_PATH_LIST --value $bitrise_aab_path

envman add --key APPCENTER_DISTRIBUTE_FILE --value $bitrise_aab_path