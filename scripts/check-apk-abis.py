"""Check that each installable APK contains native speech libraries for the advertised ABIs."""
import sys
from pathlib import Path
from zipfile import ZipFile

# ELF class (32/64 bit) and e_machine for each Android ABI.
ARCH = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86_64": (2, 62)}
variant = sys.argv[1]
directory = Path("app/build/outputs/apk") / variant
for name in [*ARCH, "universal"]:
    candidates = [
        directory / f"app-{name}-{variant}.apk",
        directory / f"app-{name}-{variant}-unsigned.apk",
    ]
    apk = next((candidate for candidate in candidates if candidate.is_file()), candidates[0])
    expected = set(ARCH) if name == "universal" else {name}
    with ZipFile(apk) as package:
        libraries = [p for p in package.namelist() if p.startswith("lib/") and p.endswith(".so")]
        packaged = {p.split("/")[1] for p in libraries}
        if packaged != expected:
            sys.exit(f"{apk}: expected {expected}, found {packaged}")
        for abi in expected:
            required = ["libsherpa-onnx-jni.so", "libonnxruntime.so"]
            required.append("libllama_server.so")
            for library in required:
                with package.open(f"lib/{abi}/{library}") as binary:
                    header = binary.read(20)
                if header[:4] != b"\x7fELF" or (header[4], int.from_bytes(header[18:20], "little")) != ARCH[abi]:
                    sys.exit(f"{apk}: incorrect binary for {abi}/{library}")
    print(f"{apk.name}: {', '.join(sorted(packaged))}")
