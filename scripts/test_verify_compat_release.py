import argparse
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from verify_compat_release import verify


class VerifyCompatibilityReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.args = argparse.Namespace(
            apk=root / "release.apk",
            build_tools=root,
            version="1.1.0-jf10.10-1.1",
            version_code=35,
            certificate_sha256="a" * 64,
            mapping=root / "mapping.txt",
        )
        self.write_apk()
        self.args.mapping.write_text("# compiler: R8\n# pg_map_id: 1234567\n")
        self.args.mapping.with_name("configuration.txt").write_text("-keepattributes SourceFile\n")
        self.badging = (
            "package: name='dev.jdtech.jellyfin.compat' versionCode='35' "
            "versionName='1.1.0-jf10.10-1.1'\n"
            "application-label:'Findroid'\nsdkVersion:'28'\ntargetSdkVersion:'36'\n"
        )
        self.manifest = "A: android:debuggable(0x0101000f)=(type 0x12)0x0\n"
        self.signatures = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            "Verified using v3 scheme (APK Signature Scheme v3): true\n"
            f"Signer #1 certificate SHA-256 digest: {'a' * 64}\n"
        )

    def write_apk(self, entries=("lib/arm64-v8a/libplayer.so",)):
        with zipfile.ZipFile(self.args.apk, "w") as archive:
            for entry in entries:
                archive.writestr(entry, b"fixture")
            archive.writestr(
                "classes.dex",
                b'~~R8{"backend":"dex","compilation-mode":"release","pg-map-id":"1234567"}\x00',
            )

    def verify(self):
        def output(tool, *arguments):
            if tool.name == "aapt":
                return self.badging if arguments[1] == "badging" else self.manifest
            if tool.name == "apksigner":
                return self.signatures
            return ""

        with patch("verify_compat_release.run", side_effect=output):
            return verify(self.args)

    def test_valid_release(self):
        result = self.verify()
        self.assertEqual("dev.jdtech.jellyfin.compat", result["application_id"])
        self.assertEqual(35, result["version_code"])
        self.assertFalse(result["debuggable"])
        self.assertEqual(["v2", "v3"], result["verified_signature_schemes"])

    def test_accepts_build_tools_37_certificate_format(self):
        self.signatures = self.signatures.replace("Signer #1 certificate", "V3.0 Signer: certificate")
        self.verify()

    def test_rejects_additional_signer(self):
        self.signatures += f"Signer #2 certificate SHA-256 digest: {'b' * 64}\n"
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_wrong_identity_version_label_or_sdk(self):
        original = self.badging
        for old, new in (
            ("dev.jdtech.jellyfin.compat", "dev.jdtech.jellyfin"),
            ("versionCode='35'", "versionCode='34'"),
            ("versionName='1.1.0-jf10.10-1.1'", "versionName='1.1.0'"),
            ("application-label:'Findroid'", "application-label:'Findroid Debug'"),
            ("sdkVersion:'28'", "sdkVersion:'29'"),
            ("targetSdkVersion:'36'", "targetSdkVersion:'35'"),
        ):
            with self.subTest(old=old):
                self.badging = original.replace(old, new)
                with self.assertRaises(ValueError):
                    self.verify()

    def test_rejects_debuggable_badging(self):
        self.badging += "application-debuggable\n"
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_manifest_debuggable_or_test_only(self):
        for flag in ("debuggable", "testOnly"):
            with self.subTest(flag=flag):
                self.manifest = f"A: android:{flag}(0x0101000f)=(type 0x12)0xffffffff\n"
                with self.assertRaises(ValueError):
                    self.verify()

    def test_rejects_wrong_certificate(self):
        self.args.certificate_sha256 = "b" * 64
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_missing_v2_or_v3(self):
        original = self.signatures
        for scheme in (2, 3):
            with self.subTest(scheme=scheme):
                self.signatures = original.replace(f"Scheme v{scheme}): true", f"Scheme v{scheme}): false")
                with self.assertRaises(ValueError):
                    self.verify()

    def test_rejects_debug_certificate(self):
        self.signatures += "Signer #1 certificate DN: CN=Android Debug\n"
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_non_arm64_apk(self):
        self.write_apk(("lib/x86_64/libplayer.so",))
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_embedded_keystore(self):
        self.write_apk(("lib/arm64-v8a/libplayer.so", "assets/secret.jks"))
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_mapping_from_another_apk(self):
        self.args.mapping.write_text("# compiler: R8\n# pg_map_id: 7654321\n")
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_missing_r8_mapping(self):
        self.args.mapping.write_text("other compiler\n")
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_disabled_optimization_or_shrinking(self):
        for flag in ("-dontoptimize", "-dontshrink"):
            with self.subTest(flag=flag):
                self.args.mapping.with_name("configuration.txt").write_text(flag)
                with self.assertRaises(ValueError):
                    self.verify()


if __name__ == "__main__":
    unittest.main()
