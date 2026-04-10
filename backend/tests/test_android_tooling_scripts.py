from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANDROID_ENV = ROOT / "scripts" / "android-env.ps1"
ADB_PREFLIGHT = ROOT / "scripts" / "check_adb_access.ps1"
RELEASE_SCRIPT = ROOT / "scripts" / "release.ps1"
README = ROOT / "README.md"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_android_env_script_uses_one_android_preferences_root() -> None:
    source = _read(ANDROID_ENV)
    assert "$env:ANDROID_USER_HOME = $legacyAndroidDotDir" in source
    assert "Remove-Item Env:ANDROID_PREFS_ROOT" in source
    assert "Remove-Item Env:ANDROID_SDK_HOME" in source
    assert '$env:JAVA_TOOL_OPTIONS = Add-OptionFlag -CurrentValue $env:JAVA_TOOL_OPTIONS -Flag "-Duser.home=$toolHome"' in source
    assert '$env:GRADLE_OPTS = Add-OptionFlag -CurrentValue $env:GRADLE_OPTS -Flag "-Duser.home=$toolHome"' in source
    assert 'New-Item -ItemType File -Force (Join-Path $legacyAndroidDotDir "analytics.settings")' in source


def test_adb_preflight_sources_android_env_and_mentions_repo_local_remediation() -> None:
    source = _read(ADB_PREFLIGHT)
    assert 'Join-Path $PSScriptRoot "android-env.ps1"' in source
    assert "repo-local writable homes" in source


def test_release_script_uses_android_env_and_explicit_local_signing_switches() -> None:
    source = _read(RELEASE_SCRIPT)
    assert 'Join-Path $PSScriptRoot "android-env.ps1"' in source
    assert "[switch]$AllowInsecureLocalSigning" in source
    assert '[switch]$SkipFirebaseDistribution' in source
    assert '$env:PCOSINA_ALLOW_INSECURE_RELEASE_SIGNING = "true"' in source
    assert '@(":app:assembleRelease", "--no-daemon")' in source


def test_readme_points_android_validation_to_repo_scripts() -> None:
    source = _read(README)
    assert ".\\scripts\\android-env.ps1" in source
    assert ".\\scripts\\run_connected_android_tests.ps1" in source
    assert ".\\scripts\\release.ps1 -AllowInsecureLocalSigning -SkipFirebaseDistribution" in source
