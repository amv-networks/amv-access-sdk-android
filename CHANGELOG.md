# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Support for initializing SDK with existing identity

### Changed
- **BREAKING**: Use `SerialNumber` instead of plain strings
- **BREAKING**: Return `AccessSdk` instance from method `AccessSdk#initialize`

## [0.0.4] - 2018-02-23
### Fixed
- Fix timeout mechanism when sending commands

## [0.0.3] - 2018-02-20
### Added
- Support for 64bit architectures
- License information
- Javadoc

## [0.0.2] - 2018-02-07
### Changed
- Define minimum Android API level (minSdkVersion = 23)

## 0.0.1 - 2018-01-16
### Added
- Initial version

[Unreleased]: https://github.com/amv-networks/amv-access-sdk-android/compare/v0.0.4...HEAD
[0.0.4]: https://github.com/amv-networks/amv-access-sdk-android/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/amv-networks/amv-access-sdk-android/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/amv-networks/amv-access-sdk-android/compare/v0.0.1...v0.0.2