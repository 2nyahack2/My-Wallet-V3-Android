fastlane documentation
================
# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```
xcode-select --install
```

Install _fastlane_ using
```
[sudo] gem install fastlane -NV
```
or alternatively using `brew install fastlane`

# Available Actions
## Android
### android build
```
fastlane android build
```
Build using the given environment (default: Staging) and build type (default: Debug).
### android staging_release
```
fastlane android staging_release
```
Build Staging Release
### android prod_debug
```
fastlane android prod_debug
```
Build Prod Debug
### android prod_release
```
fastlane android prod_release
```
Build Prod Release
### android test
```
fastlane android test
```
Run tests. Optional flags: environment (Staging), build_type (Debug), test_name (runs all by default).
### android upload_to_internal_track
```
fastlane android upload_to_internal_track
```
Submit a release build to the Play Store internal test track.
### android credentials
```
fastlane android credentials
```
Get the configuration files from the Android credentials repository.
### android ci_run_tests
```
fastlane android ci_run_tests
```
Bundle of build, perform checks and run tests on CI.
### android ci_credentials
```
fastlane android ci_credentials
```
Get the configuration files from the Android credentials repository on CI.
### android ci_upload_to_appcenter
```
fastlane android ci_upload_to_appcenter
```
Upload to AppCenter.
### android ci_export_build
```
fastlane android ci_export_build
```
Export the build path to environment variables for upload. Optional flags: export_bundle (APK is default), do_sign (False is default).
### android ci_test
```
fastlane android ci_test
```
Tests to run on CI
### android ci_build
```
fastlane android ci_build
```
Build to run on CI. Optional flags: copy_credentials, build_bundle (APK is default), export_build(False is default), do_sign (False is default).
### android ci_lint
```
fastlane android ci_lint
```
Checks to run on CI

----

This README.md is auto-generated and will be re-generated every time [fastlane](https://fastlane.tools) is run.
More information about fastlane can be found on [fastlane.tools](https://fastlane.tools).
The documentation of fastlane can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
