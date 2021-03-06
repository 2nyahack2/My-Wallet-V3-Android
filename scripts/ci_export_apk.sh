#!/usr/bin/env bash
# fail if any commands fails
set -e
# debug log
set -x

apk_path=`echo $1/*.apk | cut -d ' ' -f1`

if [ $2 = true ]; then
	jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $BITRISEIO_ANDROID_KEYSTORE_URL $apk_path "$BITRISEIO_ANDROID_KEYSTORE_ALIAS" -storepass $BITRISEIO_ANDROID_KEYSTORE_PASSWORD -keypass $BITRISEIO_ANDROID_KEYSTORE_PRIVATE_KEY_PASSWORD
fi

cp $apk_path /bitrise/deploy/

bitrise_apk_path=`echo /bitrise/deploy/*.apk | cut -d ' ' -f1`

envman add --key BITRISE_APK_PATH --value $bitrise_apk_path

envman add --key BITRISE_APK_PATH_LIST --value $bitrise_apk_path

envman add --key APPCENTER_DISTRIBUTE_FILE --value $bitrise_apk_path
