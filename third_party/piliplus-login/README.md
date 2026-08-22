# PiliPlus login reference

This directory records the source and license reference for the login behavior reviewed from `bggRGjQaUbCoE/PiliPlus`.

The upstream implementation is Flutter/Dart and is not copied into the Kotlin/Compose application as compilable source. Aulune uses a native adapter (`BilibiliLoginAdapter`) and the existing restricted WebView flow so that password, SMS verification, CAPTCHA, and session creation remain on Bilibili's official HTTPS pages.

Upstream: https://github.com/bggRGjQaUbCoE/PiliPlus
License: GNU GPL v3.0; see `LICENSE`.

The Aulune port intentionally does not implement Cookie paste/import/export or direct storage of raw credentials. The existing `BilibiliSession` keeps only an in-memory session after explicit user consent.
