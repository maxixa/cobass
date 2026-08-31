#!/usr/bin/env python3
"""
Cobass Deterministic Library Resolver
Resolves Maven POM dependencies, normalizes version ranges/brackets,
and automatically handles Kotlin 1.8+ module consolidation.
"""
import argparse
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Optional, Tuple

REPOSITORIES = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo.maven.apache.org/maven2"
]

def clean_version(raw_ver: str) -> str:
    if not raw_ver:
        return ""
    v = raw_ver.strip()
    if "," in v:
        v = v.split(",")[0]
    v = re.sub(r"[\[\]\(\)\s]", "", v)
    return v

def parse_toml_dependencies(toml_path: Path) -> List[Dict[str, str]]:
    if not toml_path.exists():
        return []
    
    deps = []
    try:
        import tomllib
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)
            for d in data.get("dependency", []):
                deps.append({
                    "group": d["group"].strip(),
                    "artifact": d["artifact"].strip(),
                    "version": clean_version(str(d["version"]))
                })
    except ImportError:
        current_dep = {}
        with open(toml_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if line == "[[dependency]]":
                    if current_dep and "group" in current_dep and "artifact" in current_dep and "version" in current_dep:
                        deps.append(current_dep)
                    current_dep = {}
                elif "=" in line:
                    k, v = [x.strip() for x in line.split("=", 1)]
                    v = clean_version(v.strip('"').strip("'"))
                    if k in ["group", "artifact", "version"]:
                        current_dep[k] = v
            if current_dep and "group" in current_dep and "artifact" in current_dep and "version" in current_dep:
                deps.append(current_dep)
    return deps

def fetch_url(url: str) -> Optional[bytes]:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Cobass-LibResolver/1.0"}
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status == 200:
                return resp.read()
    except Exception:
        return None
    return None

def check_artifact_packaging(group: str, artifact: str, version: str) -> Tuple[str, str]:
    group_path = group.replace(".", "/")
    base_name = f"{artifact}-{version}"
    
    for repo in REPOSITORIES:
        aar_url = f"{repo}/{group_path}/{artifact}/{version}/{base_name}.aar"
        req = urllib.request.Request(aar_url, method="HEAD", headers={"User-Agent": "Cobass-LibResolver/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                if resp.status == 200:
                    return "aar", aar_url
        except Exception:
            pass

        jar_url = f"{repo}/{group_path}/{artifact}/{version}/{base_name}.jar"
        req = urllib.request.Request(jar_url, method="HEAD", headers={"User-Agent": "Cobass-LibResolver/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                if resp.status == 200:
                    return "jar", jar_url
        except Exception:
            pass

    return "aar", f"{REPOSITORIES[0]}/{group_path}/{artifact}/{version}/{base_name}.aar"

def fetch_pom(group: str, artifact: str, version: str) -> Optional[str]:
    group_path = group.replace(".", "/")
    pom_file = f"{artifact}-{version}.pom"
    for repo in REPOSITORIES:
        url = f"{repo}/{group_path}/{artifact}/{version}/{pom_file}"
        data = fetch_url(url)
        if data:
            return data.decode("utf-8", errors="replace")
    return None

def parse_pom_dependencies(pom_xml: str, current_group: str, current_version: str) -> Tuple[Dict[str, str], List[Dict[str, str]]]:
    properties = {
        "project.groupId": current_group,
        "project.version": current_version,
        "version": current_version
    }
    dependencies = []
    
    try:
        xml_clean = re.sub(r'\sxmlns="[^"]+"', '', pom_xml, count=1)
        root = ET.fromstring(xml_clean)

        props_elem = root.find("properties")
        if props_elem is not None:
            for child in props_elem:
                tag = child.tag
                properties[tag] = (child.text or "").strip()

        deps_elem = root.find("dependencies")
        if deps_elem is not None:
            for d in deps_elem.findall("dependency"):
                g = d.findtext("groupId", "").strip()
                a = d.findtext("artifactId", "").strip()
                v = d.findtext("version", "").strip()
                scope = d.findtext("scope", "compile").strip()
                optional = d.findtext("optional", "false").strip().lower()

                if scope in ["test", "provided"] or optional == "true":
                    continue

                for prop_name, prop_val in properties.items():
                    placeholder = f"${{{prop_name}}}"
                    if placeholder in v:
                        v = v.replace(placeholder, prop_val)
                    if placeholder in g:
                        g = g.replace(placeholder, prop_val)

                v = clean_version(v)
                if not v or v.startswith("$"):
                    continue

                # Consolidate legacy kotlin-stdlib-jdk7/8 to modern kotlin-stdlib
                if g == "org.jetbrains.kotlin" and a in ["kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8"]:
                    a = "kotlin-stdlib"

                dependencies.append({"group": g, "artifact": a, "version": v})
    except Exception:
        pass

    return properties, dependencies

def version_tuple(ver_str: str) -> tuple:
    clean_ver = re.split(r'[-+]', ver_str)[0]
    parts = []
    for part in clean_ver.split("."):
        try:
            parts.append(int(part))
        except ValueError:
            parts.append(0)
    return tuple(parts)

def resolve_graph(root_deps: List[Dict[str, str]]) -> Dict[str, Dict[str, any]]:
    resolved: Dict[str, Dict[str, any]] = {}
    queue = list(root_deps)
    direct_keys = {f"{d['group']}:{d['artifact']}" for d in root_deps}

    print(f"[*] Resolving {len(root_deps)} direct dependencies...")

    while queue:
        dep = queue.pop(0)
        group = dep["group"]
        artifact = dep["artifact"]
        version = clean_version(dep["version"])

        # Consolidate legacy kotlin-stdlib-jdk7/8
        if group == "org.jetbrains.kotlin" and artifact in ["kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8"]:
            artifact = "kotlin-stdlib"

        key = f"{group}:{artifact}"

        if not version:
            continue

        if key in resolved:
            existing_ver = resolved[key]["version"]
            if version_tuple(version) <= version_tuple(existing_ver):
                continue

        packaging, url = check_artifact_packaging(group, artifact, version)
        
        resolved[key] = {
            "group": group,
            "artifact": artifact,
            "version": version,
            "packaging": packaging,
            "url": url,
            "isDirect": key in direct_keys,
            "sha256": ""
        }
        sys.stdout.write(f"\r    [+] Resolved: {key}:{version} [{packaging.upper()}]".ljust(75))
        sys.stdout.flush()

        pom_xml = fetch_pom(group, artifact, version)
        if pom_xml:
            _, child_deps = parse_pom_dependencies(pom_xml, group, version)
            for child in child_deps:
                queue.append(child)

    print("\n[*] Dependency graph resolution complete.")
    return resolved

def main():
    parser = argparse.ArgumentParser(description="Cobass Library Resolver")
    parser.add_argument("--spec", default="config/deps.toml", help="Path to deps.toml")
    parser.add_argument("--addons", default="config/addons.toml", help="Path to addons.toml")
    parser.add_argument("--lock", default="libs/resolved.lock.json", help="Path to output lockfile")
    parser.add_argument("--out", default="libs/resolved.json", help="Path to resolved metadata")
    args = parser.parse_args()

    spec_path = Path(args.spec)
    if not spec_path.exists():
        print(f"Error: {spec_path} does not exist.")
        sys.exit(1)

    deps = parse_toml_dependencies(spec_path)
    
    addons_path = Path(args.addons)
    if addons_path.exists():
        deps.extend(parse_toml_dependencies(addons_path))

    resolved_map = resolve_graph(deps)
    
    lock_data = {
        "generator": "Cobass LibResolver",
        "totalArtifacts": len(resolved_map),
        "artifacts": sorted(list(resolved_map.values()), key=lambda x: f"{x['group']}:{x['artifact']}")
    }

    lock_file = Path(args.lock)
    lock_file.parent.mkdir(parents=True, exist_ok=True)
    with open(lock_file, "w", encoding="utf-8") as f:
        json.dump(lock_data, f, indent=2)

    with open(Path(args.out), "w", encoding="utf-8") as f:
        json.dump(lock_data, f, indent=2)

    print(f"\033[92m[OK] Resolved {len(resolved_map)} artifacts without duplicate kotlin modules.\033[0m")

if __name__ == "__main__":
    main()
