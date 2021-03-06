# My-Wallet-V3-Android

[![CircleCI](https://circleci.com/gh/blockchain/My-Wallet-V3-Android/tree/master.svg?style=svg)](https://circleci.com/gh/blockchain/My-Wallet-V3-Android/tree/master)

[![Coverage Status](https://coveralls.io/repos/github/blockchain/My-Wallet-V3-Android/badge.svg?branch=master)](https://coveralls.io/github/blockchain/My-Wallet-V3-Android?branch=master)

Next-generation HD (BIP32, BIP39, BIP44) bitcoin, ethereum and bitcoin cash wallet. 

# Getting started

## Install Android Studio

Download from [Android Studio](https://developer.android.com/studio). Make sure to install the command line tools as well.
After installing AS, open it and install API 28 (current `compileSdkVersion`) and 29 (current `targetSdkVersion`)
from `Preferences -> Appearance & Behavior -> System Settings -> Android SDK`.
Install [Oracle JDK](https://www.oracle.com/java/technologies/javase-downloads.html) for Gradle command line tools.

**Required: Run the quickstart script from a bash terminal at the base of the project; `./scripts/quick_start.sh` this will install the necessary
dependencies for the project to compile successfully.**

Optional: Run the bootstrap script from terminal via `scripts/bootstrap.sh`. This will install the Google Java code style as well
as the official Android Kotlin code style and remove any file header templates. The script may indicate that you need
to restart Android Studio for it's changes to take effect.

## Install `homebrew`

https://brew.sh/

## Setting up SSH for GitHub

Follow [this](https://docs.github.com/en/free-pro-team@latest/github/authenticating-to-github/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent) for generating your SSH key
and adding it to your GitHub account.

## Automatic Git Commit Signing

Install System Packages
    
    # Install GPG
    brew install gpg

    # Install Pinentry for storing the passphrase in the keychain
    brew install pinentry-mac

Generate the key via `gpg --full-generate-key`
When asked:
* press enter to choose RSA / DSA (default)
* Key size 4096
* Key valid for 0 (indefinitely)
* Add your name on GitHub
* Add your blockchain email address

### Configuring Git to sign automatically

Take the second line below “pub”, i.e. long string with hex string (this is the `key_id`)

    git config --global user.signingKey key_id
    git config --global commit.gpgSign true
    git config --global gpg.program gpg
    
    # Setting up the gpg agent to use the keychain
    echo "pinentry-program /usr/local/bin/pinentry-mac" >> ~/.gnupg/gpg-agent.conf
    
    # You may need to restart the agent
    gpgconf --kill gpg-agent
    
### Configuring Github to recognize the key

Go to `GitHub -> Profile -> Settings -> SSH and GPG keys -> new GPG key`
Get your public key by running

    # where key_id as before
    gpg --armor --export key_id | pbcopy
    
## Install XCode command line tools

This is required in order to be able to use fastlane bundle commands

    xcode-select --install
    
## Setting up fastlane

We use fastlane for automating building, signing, testing and uploading to Appcenter and to the Play Store.
The settings are stored in the `fastlane` directory. The preferred way of installing fastlane is via the `Gemfile`

    [sudo] bundle install
    
To update fastlane use

    [sudo] bundle update fastlane

after which you need to commit the changes (typically the `Gemfile`, `Gemfile.lock` and the configuration files
in the `fastlane` directory).    
    
Alternatively you can install it via Homebrew

    brew install fastlane
    
    fastlane init
    
Once fastlane is setup, you can find the available lane commands and their description in `./fastlane/README.md` file.

### Using fastlane

You can run any of the lane tasks defined in the `./fastlane/FastFile` (summary in the fastlane `README.md`)

    # The <options> are given in the format of key1:value1 key2:value2 and so on
    bundle exec fastlane <lane> <options>
    
    # Alternative, the bundle is preferred
    fastlane <lane> <options>
    
Runing the unit tests

    bundle exec fastlane execute_tests
    
### Troubleshooting

Sometimes the XCode developer tools doesn't find the relevant headers to build and install the necessary
gems. In this case use the following command to compile the required gem:

    sudo xcrun gem install <gem_name> -v <gem_version> --source 'https://rubygems.org/'

Example:

    sudo xcrun gem install unf_ext -v '0.0.7.7' --source 'https://rubygems.org/'

## Building

Clone the [Android Repository](https://github.com/blockchain/wallet-android-private). Make sure your repository
is on `develop`. Import it as an Android Studio project (`File -> Open`).

## Configuration files

The following files can be found in [Android Configuration Files](https://github.com/blockchain/wallet-android-credentials):
* Secrets properties file - Contains API keys and the various combinations of production/staging/testnet/dev URLs.
* Environment files - root `env` folder and total of 3 `google-services.json` files.

You can get the latest configuration files by running 

     bundle exec fastlane credentials
     
Or manually cloning the [Android Configuration Files](https://github.com/blockchain/wallet-android-credentials) repository
and then moving the  `secrets.properties` (file extension should be `properties` only) into `./app` as well as
unzipping the `env.zip` file, deleting `./app/src/env` and then moving the unzipped `env` folder to `./app/src/`.

Select the `envStagingDebug` variant from `Build Variants` (bottom left corner in AS) since dev is quite unstable,
then hit `Build -> Make Project`. For Dev and Staging access you'll need [VPN access](https://blockchain.atlassian.net/wiki/spaces/SKB/pages/501350537/Algo+VPN+Client+side+setup#How-can-setup-it-up%3F).

## Common Errors

Execution failed for task `:app:processEnv...GoogleServices`
* Make sure to delete `./app/src/env` and then move the unzipped `env` folder to `./app/src/.`
* Make sure `./app/src/env/` only contains directories (there shouldn't be any `json` file there)

### Contributions and Code Style

All new code must be in Kotlin. We are using the official Kotlin style guide, which can be applied in Android Studio via 
`Preferences -> Editor -> Code Style -> Kotlin -> Set from -> Predefined style -> Kotlin Style Guide`. It should be 
noted that this is not currently the default in Android Studio, so please configure this if you have recently 
reinstalled AS. Alternatively, simply run the bootstrap script and ktlint will configure your IDE for you.

All code must be tested if possible, and must pass CI. Therefore it must introduce no new Lint errors, and must pass 
Ktlint. Before committing any new Kotlin code it is recommended formatting your files in Android Studio with 
`CMD + ALT + L` and running `./gradlew ktlint` locally. You can if you so wish run `./gradlew ktlintFormat` which 
will fix any style violations. Be aware that this may need to be run twice to apply all fixes as of 0.20.

## Commit message style

Use git change log style.

Where you have access to Jira, you should apply the git hooks with `./gradlew installGitHooks`. This enforces the
git change log style with Jira references.

### Security

Security issues can be reported to us in the following venues:
* Email: security@blockchain.info
* Bug Bounty: https://hackerone.com/blockchain
