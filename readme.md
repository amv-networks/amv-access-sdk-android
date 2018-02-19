[![Build Status](https://travis-ci.org/amv-networks/amv-access-sdk-android.svg?branch=master)](https://travis-ci.org/amv-networks/amv-access-sdk-android)
[![Jitpack](https://jitpack.io/v/amv-networks/amv-access-sdk-android.svg)](https://jitpack.io/#amv-networks/amv-access-sdk-android)
[![License](https://img.shields.io/github/license/amv-networks/amv-access-sdk-android.svg?maxAge=2592000)](https://github.com/amv-networks/amv-access-sdk-android/blob/master/LICENSE)


amv-access-sdk-android
======================

## getting started
### setup
```bash
git clone https://github.com/amv-networks/amv-access-sdk-android.git
```

### build
```bash
./gradlew clean build
```

### ide
As this project uses [Project Lombok](https://projectlombok.org/) make sure you have the
[IntelliJ Lombok Plugin](https://github.com/mplushnikov/lombok-intellij-plugin) installed and
annotation processing enabled.

* minSdkVersion for android is 23
* gradle version 4.3.1

## modules
This repository currently consists of two libraries.

### amv-access-sdk-spi
A module consisting interfaces and simple base classes which represent the basic requirements
and concepts needed in concrete implementations.

### amv-hm-access-sdk
The first (and currently only) implementation of `amv-access-sdk-spi`.

## example app
The repository [amv-access-sdk-android-reference-app](https://github.com/amv-networks/amv-access-sdk-android-reference-app) 
contains a working example app and demo implementation which uses `amv-hm-access-sdk`. It can be used
to get started quickly with your own app.

## integration
In order to integrate the sdk into your own app you need two things:
- api credentials to successfully register a device with a backend service
- an implementation of the `amv-access-sdk-spi` module

Currently there is only one such implementation which is `amv-hm-access-sdk`.
You can always refer to the example application or contact a developer if you need further information.

**Step 1.** Add the following lines to your root `build.gradle` at the end of repositories:
```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the `libs/` folder to your apps repositories:
```groovy
    repositories {
        ...
        flatDir {
            dirs 'libs'
        }
    }
```

**Step 3.** Copy the `hmkit-android-*.aar` file from `amv-hm-access-sdk/libs/` to your own `libs/` directory.

**Step 4.** Add the dependency:

```groovy
dependencies {
    ...
    implementation(name: 'hmkit-android', version: "${hmkitVersion}", ext: 'aar')
    implementation "com.github.amv-networks.amv-access-sdk-android:amv-hm-access-sdk:${amvAccessSdkVersion}"
}
```

# license
The project is licensed under the Apache License. See [LICENSE](LICENSE) for details.
    
