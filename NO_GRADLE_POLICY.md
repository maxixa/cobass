# No-Gradle Architecture Policy

## Strict Rules
1. **No Gradle**: `build.gradle`, `settings.gradle`, `gradlew`, or `.gradle/` folders are prohibited.
2. **Deterministic Build Pipeline**:
   - `deps.toml` -> `tools/lib_resolver.py` -> `resolved.lock.json`
   - `tools/lib_downloader.py` -> `libs/downloaded/`
   - `tools/aar_expander.py` -> `libs/exploded/`
   - `tools/native_build.py` -> `app/lib/<abi>/`
   - `tools/build_apk.py` -> `aapt2` -> `javac` -> `d8` -> `zipalign` -> `apksigner`
3. **Architecture Boundary Enforcement**: Modules must adhere to strict uni-directional dependencies checked by `tools/module_check.py`.
