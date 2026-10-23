#!/bin/bash
# Правит minSdk 24 -> 21 в распакованном (transformed) AAR play-services-mlkit-subject-segmentation.
# Запускать один раз после очистки кэша gradle до сборки приложения.
find ~/.gradle/caches -path "*transformed/jetified-play-services-mlkit-subject-segmentation-*" -name "AndroidManifest.xml" 2>/dev/null | while read f; do
  sed -i 's/android:minSdkVersion="24"/android:minSdkVersion="21"/' "$f"
  echo "patched: $f"
done
