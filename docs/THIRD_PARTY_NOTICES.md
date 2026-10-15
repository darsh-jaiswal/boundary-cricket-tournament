# Third-party models and libraries

Application source is Java. Gradle uses the Android Kotlin DSL. Third-party native/runtime components retain their original licensing.

- **sherpa-onnx 1.13.8 Android AAR**: [official release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8), [Apache 2.0 license](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE). Bundled as `app/libs/sherpa-onnx-1.13.8.aar`. Includes ONNX Runtime/native dependencies; see upstream licenses.
- **3D-Speaker ERes2Net English VoxCeleb 16 kHz**: [official model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models), [3D-Speaker source and license](https://github.com/modelscope/3D-Speaker). Original filename `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx`, stored as `speaker.onnx`.
- **Vosk Android**: [API source and Apache 2.0 license](https://github.com/alphacep/vosk-api). Maven dependency `com.alphacephei:vosk-android:0.3.47`.
- **Vosk small Indian English model 0.4**: [official model catalog](https://alphacephei.com/vosk/models), Apache 2.0 listed by its publisher. Bundled original archive as `speech.zip`, extracted into private app storage on first voice setup.
- **JNA**: [upstream licensing](https://github.com/java-native-access/jna/blob/master/LICENSE), LGPL 2.1 or Apache 2.0.
- **ML Kit text recognition**: [official integration and terms](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Google ML Kit bundled Latin OCR; review Google SDK terms before redistribution.
- **AndroidX CameraX / AppCompat / Activity and Material Components**: official Android/Google artifacts resolved by Gradle.
- **Neon serverless driver**: [upstream](https://github.com/neondatabase/serverless), MIT license. Server credentials remain in Vercel environment variables.

Official implementation references: [speaker identification](https://k2-fsa.github.io/sherpa/onnx/speaker-identification/), [CameraX recording](https://developer.android.com/media/camera/camerax/video-capture), [Vosk offline recognition](https://alphacephei.com/vosk/), [MCC scoring runs](https://www.lords.org/MCC/The-Laws-of-Cricket/Scoring-runs). Local match-rule overrides are explicit in each match; the app does not claim official competition certification.
