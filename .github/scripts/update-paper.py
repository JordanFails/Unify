import argparse
import json
import re
import shutil
from pathlib import Path
from urllib.request import Request, urlopen
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
NMS_MODULES = ROOT / "unify-nms"
SERVER_VERSION = ROOT / "unify-core/src/main/kotlin/me/jordanfails/unify/nms/ServerVersion.kt"
SETTINGS = ROOT / "settings.gradle.kts"
DEV_BUNDLE_METADATA = (
    "https://repo.papermc.io/repository/maven-public/io/papermc/paper/dev-bundle/maven-metadata.xml"
)
STABLE_BUNDLE = re.compile(r"(\d{2}\.\d+(?:\.\d+)?)\.build\.(\d+)-stable")
PAPER_HANDLERS = re.compile(r'^    (v\w+)\("(\d{2}\.\d+(?:\.\d+)?)"\)([,;])$', re.MULTILINE)


def version_key(version):
    return tuple(map(int, version.split(".")))


def discover():
    supported = {version for _, version, _ in PAPER_HANDLERS.findall(SERVER_VERSION.read_text())}
    first = min(map(version_key, supported))
    request = Request(DEV_BUNDLE_METADATA, headers={"User-Agent": "Unify CI (https://github.com/JordanFails/Unify)"})
    with urlopen(request, timeout=30) as response:
        metadata = ElementTree.parse(response)

    entries = metadata.findall("./versioning/versions/version")
    if not entries:
        raise ValueError("Paper dev bundle metadata contained no versions")

    builds = {}
    for entry in entries:
        match = STABLE_BUNDLE.fullmatch(entry.text or "")
        if match:
            version, build = match.groups()
            if version not in supported and version_key(version) > first:
                builds[version] = max(builds.get(version, 0), int(build))

    return [
        {"version": version, "bundle": f"{version}.build.{builds[version]}-stable"}
        for version in sorted(builds, key=version_key)
    ]


def scaffold(bundle):
    match = STABLE_BUNDLE.fullmatch(bundle)
    if not match:
        raise ValueError(f"Expected a pinned stable Paper dev bundle, got {bundle}")

    version = match[1]
    enum_source = SERVER_VERSION.read_text()
    adapters = PAPER_HANDLERS.findall(enum_source)
    older = [adapter for adapter in adapters if version_key(adapter[1]) < version_key(version)]
    if not older or any(existing == version for _, existing, _ in adapters):
        raise ValueError(f"Paper {version} must be unregistered and have an older adapter to copy")
    previous, previous_version, delimiter = max(older, key=lambda adapter: version_key(adapter[1]))

    handler = "v" + version.replace(".", "_")
    template = NMS_MODULES / f"unify-nms-{previous}"
    target = NMS_MODULES / f"unify-nms-{handler}"
    if target.exists():
        raise ValueError(f"{target.name} already exists")

    enum_entry = f'    {previous}("{previous_version}")'
    previous_include = f'include("unify-nms:{template.name}")'
    settings = SETTINGS.read_text()
    build = (template / "build.gradle.kts").read_text()
    build, replacements = re.subn(
        r'paperweight\.paperDevBundle\("[^"]+"\)', f'paperweight.paperDevBundle("{bundle}")', build
    )
    if settings.count(previous_include) != 1 or replacements != 1:
        raise ValueError(f"Could not find the Gradle and runtime entries for {template.name}")

    sources = sorted(path for path in (template / "src/main").rglob("*") if path.is_file())
    if not any(path.name == f"NMSHandler_{previous}.kt" for path in sources):
        raise ValueError(f"{template.name} has no handler source")

    target.mkdir()
    (target / "build.gradle.kts").write_text(build)
    properties = template / "gradle.properties"
    if properties.exists():
        shutil.copyfile(properties, target / properties.name)
    for path in sources:
        destination = target / str(path.relative_to(template)).replace(previous, handler)
        destination.parent.mkdir(parents=True, exist_ok=True)
        if path.suffix in {".kt", ".java"}:
            destination.write_text(path.read_text().replace(previous, handler))
        else:
            shutil.copyfile(path, destination)

    SERVER_VERSION.write_text(enum_source.replace(
        f"{enum_entry}{delimiter}", f'{enum_entry},\n    {handler}("{version}"){delimiter}'
    ))
    SETTINGS.write_text(settings.replace(previous_include, f'{previous_include}\ninclude("unify-nms:{target.name}")'))
    print(f"Prepared {target.name} against Paper {bundle}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("discover")
    commands.add_parser("scaffold").add_argument("bundle")
    args = parser.parse_args()
    if args.command == "discover":
        print(json.dumps(discover()))
    else:
        scaffold(args.bundle)
