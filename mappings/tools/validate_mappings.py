#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
映射数据全局校验工具（只用 Python 标准库）。

按引擎 MappingRepository / MappingResolver / MetadataPass 的语义，把每个（loader, 版本或别名）的
有效数据重建出来，然后做结构、完整性与跨版本可达性检查。

用法：
    python3 mappings/tools/validate_mappings.py            # 全部 loader
    python3 mappings/tools/validate_mappings.py --loader fabric
    python3 mappings/tools/validate_mappings.py --verbose  # 打印每条问题
    python3 mappings/tools/validate_mappings.py --json out.json

退出码：存在 ERROR 级问题时为 1，否则为 0。

检查项：
  E-json        JSON 不合法
  E-basedOn     basedOn 断链或循环
  E-alias       别名与目录重名、同一 loader 内别名重复声明
  E-remove      !remove 的目标在基版本中不存在
  E-forgeIr     NeoForge 版本未映射 forge.Mod / forge.SubscribeEvent
  E-version     version.json 必要字段缺失、javaVersion 找不到平台文件
  E-template    templateDirs 链上找不到必需模板
  E-placeholder 模板中存在引擎无法替换的 ${...}
  E-cross       同 loader 内某两个版本之间，源版本的类 IR 在目标版本既无映射也无 guidance
  E-dupFqcn     同一数据集内多个 IR id 指向同一 FQCN（引擎反查依赖 HashMap 顺序）
  E-idConflict  同一官方名 FQCN 在不同数据集里对应不同 IR id
  E-guidance    （非 Forge）guidance 未覆盖「全部 concept ∪ 本 loader 内本数据集缺失的类 IR」
  E-fabricIr    Fabric 数据集使用了 FABRIC-IR-CONTRACT 第 2 节清单以外的 fabric.* / mixin.* id
  E-neoforgeIr  NeoForge 或 Forge 数据集使用了 NEOFORGE-IR-CONTRACT 第 2.1 节清单以外的 neoforge.* id
  W-mcId        新 mc.* id 不符合 DATA-RULES 第 1 条（可能是历史 id，仅提示）
  W-deadRemoved removed.json 中的类在同一数据集 classes.json 中已有映射（该条目不会生效）
  W-concept     removed 条目的 concept 在某目标版本既不 supported 也无 guidance（将回退到 message）
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # mappings/
VERSIONS = os.path.join(ROOT, "versions")
JAVA_DIR = os.path.join(ROOT, "java")

# MetadataPass.renderTemplate 固定替换的占位符
ENGINE_PLACEHOLDERS = {
    "entrypointsJson", "mixinsJson", "modid", "name", "version", "description", "authors",
    "url", "logoFile", "credits", "group", "mcVersion", "forgeVersion", "loaderVersionRange",
    "mappingsChannel", "gradleVersion", "javaVersion",
}
# 模板中允许保留给 Gradle/Groovy 运行期插值的表达式（不是引擎占位符）
GROOVY_ALLOWED = {"it"}



class Issue:
    def __init__(self, level, code, where, msg):
        self.level, self.code, self.where, self.msg = level, code, where, msg

    def __str__(self):
        return f"[{self.level}] {self.code} {self.where}: {self.msg}"


issues = []


def add(level, code, where, msg):
    issues.append(Issue(level, code, where, msg))


def read_json(path, where=None):
    """读 JSON；失败记 E-json 并返回 None。"""
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        add("ERROR", "E-json", where or os.path.relpath(path, ROOT), str(e))
        return None


# ---------------------------------------------------------------------------
# 有效数据的重建（与 MappingRepository 一致）
# ---------------------------------------------------------------------------

class Dataset:
    """一个 (loader, 版本) 合并后的有效数据。"""

    def __init__(self):
        self.loader = None
        self.name = None          # 版本名或别名
        self.dir = None           # 数据目录名（别名指向被引用目录）
        self.is_alias = False
        self.kind = None          # full / overlay / alias
        self.info = {}
        self.classes = {}         # ir -> {"name":..., "note":...}
        self.members = {}         # ir -> {member: {...}}
        self.removed_classes = {}
        self.removed_members = {}
        self.forms = {}
        self.guidance = {}
        self.supported = set()
        self.annotations = {}
        self.template_dirs = []
        self.chain = []           # 从自身到根的目录链

    def ir_by_fqcn(self):
        rev = defaultdict(list)
        for ir, e in self.classes.items():
            rev[e["name"]].append(ir)
        return rev


INFO_KEYS_STR = ["mcVersion", "loader", "metadataFormat", "metadataPath", "langFormat",
                 "modAnnotationStyle", "lifecycleStyle", "forgeVersion", "loaderVersionRange",
                 "mappingsChannel", "gradleVersion"]


def apply_info(info, o):
    """applyInfoOverrides：只覆盖出现的字段；langKeys / texturePrefixes / extras 为逐键合并。"""
    for k in INFO_KEYS_STR:
        if k in o:
            info[k] = o[k] if isinstance(o[k], str) else None
    for k in ("javaVersion", "packFormat"):
        if k in o:
            info[k] = o[k]
    if "packFormat" in o:
        for k in ("resourcePackFormat", "dataPackFormat", "packMetadataStyle"):
            if k not in o:
                info.pop(k, None)
        if "itemModelDefinitions" not in o:
            info["itemModelDefinitions"] = False
    for k in ("resourcePackFormat", "dataPackFormat", "packMetadataStyle", "itemModelDefinitions"):
        if k in o:
            info[k] = o[k]
    for k in ("langKeys", "texturePrefixes", "extras"):
        if k in o and isinstance(o[k], dict):
            d = dict(info.get(k, {}))
            for kk, vv in o[k].items():
                d[kk] = str(vv)
            info[k] = d


class Repo:
    def __init__(self):
        self.cache = {}
        self.raw = {}  # (loader, dir) -> {file: json}
        self.dirs = defaultdict(list)       # loader -> [dir]
        self.alias_owner = defaultdict(dict)  # loader -> alias -> [dir...]
        self._scan()

    def _scan(self):
        for loader in sorted(os.listdir(VERSIONS)):
            ld = os.path.join(VERSIONS, loader)
            if not os.path.isdir(ld):
                continue
            for d in sorted(os.listdir(ld)):
                vd = os.path.join(ld, d)
                if not os.path.isfile(os.path.join(vd, "version.json")):
                    continue
                files = {}
                for fn in ("version.json", "classes.json", "members.json", "removed.json", "idioms.json", "annotations.json"):
                    p = os.path.join(vd, fn)
                    if os.path.isfile(p):
                        files[fn] = read_json(p, f"{loader}/{d}/{fn}")
                # 其它 json（防止漏检）
                for fn in os.listdir(vd):
                    if fn.endswith(".json") and fn not in files:
                        read_json(os.path.join(vd, fn), f"{loader}/{d}/{fn}")
                self.raw[(loader, d)] = files
                self.dirs[loader].append(d)
                v = files.get("version.json") or {}
                for a in (v.get("aliases") or {}):
                    self.alias_owner[loader].setdefault(a, []).append(d)

    def names(self, loader):
        """listVersions：目录 + 别名（去重）。"""
        out = list(self.dirs[loader])
        for a in self.alias_owner[loader]:
            if a not in out:
                out.append(a)
        return out

    def load(self, loader, name, visiting=None):
        key = (loader, name)
        if key in self.cache:
            return self.cache[key]
        visiting = visiting if visiting is not None else []
        if key in visiting:
            add("ERROR", "E-basedOn", f"{loader}/{name}", "basedOn 循环: " + " -> ".join(v[1] for v in visiting + [key]))
            return None
        visiting = visiting + [key]
        if name in self.dirs[loader]:
            ds = self._load_dir(loader, name, visiting)
        else:
            owners = self.alias_owner[loader].get(name)
            if not owners:
                add("ERROR", "E-basedOn", f"{loader}/{name}", "找不到版本（既无目录也无别名）")
                return None
            base = self.load(loader, owners[0], visiting)
            if base is None:
                return None
            ds = Dataset()
            ds.__dict__.update({k: v for k, v in base.__dict__.items()})
            ds.info = json.loads(json.dumps(base.info))
            ds.info["mcVersion"] = name
            ov = self.raw[(loader, owners[0])]["version.json"]["aliases"][name]
            if isinstance(ov, dict):
                apply_info(ds.info, ov)
            ds.name = name
            ds.is_alias = True
            ds.kind = "alias"
        self.cache[key] = ds
        return ds

    def _load_dir(self, loader, d, visiting):
        files = self.raw[(loader, d)]
        v = files.get("version.json")
        if v is None:
            return None
        ds = Dataset()
        ds.loader, ds.name, ds.dir = loader, d, d
        tdir = os.path.join(VERSIONS, loader, d, "templates")
        based = v.get("basedOn")
        if based:
            if based not in self.dirs[loader] and based not in self.alias_owner[loader]:
                add("ERROR", "E-basedOn", f"{loader}/{d}", f"basedOn 指向不存在的版本 {based}")
                return None
            base = self.load(loader, based, visiting)
            if base is None:
                return None
            ds.kind = "overlay"
            ds.info = json.loads(json.dumps(base.info))
            ds.info["mcVersion"] = d
            apply_info(ds.info, v)
            ds.classes = {k: dict(x) for k, x in base.classes.items()}
            ds.members = {k: dict(x) for k, x in base.members.items()}
            ds.removed_classes = dict(base.removed_classes)
            ds.removed_members = dict(base.removed_members)
            ds.forms = dict(base.forms)
            ds.guidance = dict(base.guidance)
            ds.supported = set(base.supported)
            ds.annotations = json.loads(json.dumps(base.annotations))
            ds.template_dirs = [tdir] + list(base.template_dirs)
            ds.chain = [d] + list(base.chain)
        else:
            ds.kind = "full"
            ds.info = {"javaVersion": 17}
            apply_info(ds.info, v)
            ds.template_dirs = [tdir]
            ds.chain = [d]
        where = f"{loader}/{d}"
        self._merge_classes(ds, files.get("classes.json"), where, bool(based))
        self._merge_members(ds, files.get("members.json"), where, bool(based))
        self._merge_removed(ds, files.get("removed.json"), where, bool(based))
        self._merge_idioms(ds, files.get("idioms.json"), where, bool(based))
        self._merge_annotations(ds, files.get("annotations.json"), where, bool(based))
        return ds

    # ---- 合并规则（与 Java 实现一一对应） ----

    @staticmethod
    def _merge_classes(ds, o, where, has_base):
        if not o:
            return
        for r in o.get("!remove", []):
            if r not in ds.classes:
                add("ERROR", "E-remove", where + "/classes.json", f"!remove 目标不存在: {r}")
            ds.classes.pop(r, None)
        for k, val in o.items():
            if k == "!remove":
                continue
            if isinstance(val, dict):
                if "name" not in val:
                    add("ERROR", "E-json", where + "/classes.json", f"{k} 缺少 name")
                    continue
                if set(val.keys()) - {"name", "note", "primary"}:
                    add("ERROR", "E-json", where + "/classes.json", f"{k} 存在未知字段")
                    continue
                if "primary" in val and not isinstance(val["primary"], bool):
                    add("ERROR", "E-json", where + "/classes.json", f"{k}.primary 必须是布尔值")
                    continue
                ds.classes[k] = {"name": val["name"], "note": val.get("note"), "primary": val.get("primary", False)}
            else:
                ds.classes[k] = {"name": val, "note": None, "primary": False}

    @staticmethod
    def _merge_members(ds, o, where, has_base):
        if not o:
            return
        for spec in o.get("!remove", []):
            if "#" in spec:
                c, m = spec.split("#", 1)
                if c not in ds.members or m not in ds.members[c]:
                    add("ERROR", "E-remove", where + "/members.json", f"!remove 目标不存在: {spec}")
                else:
                    ds.members[c].pop(m)
            else:
                if spec not in ds.members:
                    add("ERROR", "E-remove", where + "/members.json", f"!remove 目标不存在: {spec}")
                ds.members.pop(spec, None)
        for c, ms in o.items():
            if c == "!remove":
                continue
            mm = ds.members.setdefault(c, {})
            for m, val in ms.items():
                if isinstance(val, dict):
                    if set(val.keys()) - {"name", "kind", "note", "receiver"}:
                        add("ERROR", "E-json", where + "/members.json", f"{c}.{m} 存在未知字段")
                        continue
                    if val.get("kind") not in ("method", "field"):
                        add("ERROR", "E-json", where + "/members.json", f"{c}.{m}.kind 必须是 method/field")
                        continue
                    receiver = val.get("receiver")
                    if receiver is not None:
                        if not isinstance(receiver, dict) or set(receiver.keys()) - {"kind", "owner", "path"}:
                            add("ERROR", "E-receiver", where + "/members.json", f"{c}.{m}.receiver 结构非法")
                            continue
                        rkind = receiver.get("kind")
                        owner = receiver.get("owner")
                        path = receiver.get("path", [])
                        if rkind == "static":
                            if not isinstance(owner, str) or "." not in owner or path:
                                add("ERROR", "E-receiver", where + "/members.json", f"{c}.{m}.receiver static 需要 owner FQCN 且无 path")
                                continue
                        elif rkind == "chain":
                            if owner is not None or not isinstance(path, list) or not path \
                                    or not all(isinstance(x, str) and re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*(\(\))?", x) for x in path):
                                add("ERROR", "E-receiver", where + "/members.json", f"{c}.{m}.receiver chain 需要 path")
                                continue
                        else:
                            add("ERROR", "E-receiver", where + "/members.json", f"{c}.{m}.receiver.kind 必须是 static/chain")
                            continue
                    mm[m] = {"name": val.get("name", m), "kind": val.get("kind", "method"),
                             "note": val.get("note"), "receiver": receiver}
                else:
                    mm[m] = {"name": val, "kind": "method", "note": None, "receiver": None}

    @staticmethod
    def _merge_removed(ds, o, where, has_base):
        if not o:
            return
        rem = o.get("!remove")
        if isinstance(rem, dict):
            for c in rem.get("classes", []):
                if c not in ds.removed_classes:
                    add("ERROR", "E-remove", where + "/removed.json", f"!remove.classes 目标不存在: {c}")
                ds.removed_classes.pop(c, None)
            for m in rem.get("members", []):
                if m not in ds.removed_members:
                    add("ERROR", "E-remove", where + "/removed.json", f"!remove.members 目标不存在: {m}")
                ds.removed_members.pop(m, None)
        elif rem is not None:
            add("ERROR", "E-remove", where + "/removed.json", "!remove 必须是对象 {classes:[], members:[]}")
        for k, val in (o.get("classes") or {}).items():
            ds.removed_classes[k] = {"concept": val.get("concept"), "message": val.get("message")}
        for k, val in (o.get("members") or {}).items():
            ds.removed_members[k] = {"concept": val.get("concept"), "message": val.get("message")}

    @staticmethod
    def _merge_idioms(ds, o, where, has_base):
        if not o:
            return
        BASIC_TYPES = {"boolean", "byte", "char", "short", "int", "long", "float", "double", "void"}
        for k, val in (o.get("forms") or {}).items():
            if "type" not in val or "class" not in val:
                add("ERROR", "E-json", where + "/idioms.json", f"form {k} 缺少 type/class")
                continue
            unknown = set(val.keys()) - {"type", "class", "method", "arity", "argTypes"}
            if unknown:
                add("ERROR", "E-json", where + "/idioms.json", f"form {k} 存在未知字段: {sorted(unknown)}")
                continue
            arg_types = val.get("argTypes")
            if arg_types is not None:
                if not isinstance(arg_types, list) or not arg_types:
                    add("ERROR", "E-json", where + "/idioms.json", f"form {k}.argTypes 必须是非空数组")
                    continue
                bad = [t for t in arg_types
                       if not isinstance(t, str)
                       or (t not in BASIC_TYPES and not re.fullmatch(r"[a-z][a-zA-Z0-9$]*(\.[a-zA-Z0-9_$]+)+", t))]
                if bad:
                    add("ERROR", "E-json", where + "/idioms.json", f"form {k}.argTypes 含非法条目: {bad}")
                    continue
                arity = val.get("arity")
                if not isinstance(arity, int) or arity != len(arg_types):
                    add("ERROR", "E-json", where + "/idioms.json", f"form {k}.argTypes 必须与 arity 一致")
                    continue
            ds.forms[k] = val
        for k, val in (o.get("guidance") or {}).items():
            ds.guidance[k] = val
        for k in o.get("supported", []):
            ds.supported.add(k)
        for k in o.get("!removeSupported", []):
            if k not in ds.supported:
                add("ERROR", "E-remove", where + "/idioms.json", f"!removeSupported 目标不存在: {k}")
            ds.supported.discard(k)

    @staticmethod
    def _merge_annotations(ds, o, where, has_base):
        if not o:
            return
        for spec in o.get("!remove", []):
            if not isinstance(spec, str):
                add("ERROR", "E-annotation", where + "/annotations.json", "!remove 条目必须是字符串")
                continue
            if "#" in spec:
                a, attr = spec.split("#", 1)
                if not a or not attr:
                    add("ERROR", "E-remove", where + "/annotations.json", f"!remove 目标格式非法: {spec}")
                elif a not in ds.annotations or attr not in ds.annotations[a]["attributes"]:
                    add("ERROR", "E-remove", where + "/annotations.json", f"!remove 目标不存在: {spec}")
                else:
                    ds.annotations[a]["attributes"].pop(attr)
            else:
                if spec not in ds.annotations:
                    add("ERROR", "E-remove", where + "/annotations.json", f"!remove 目标不存在: {spec}")
                else:
                    ds.annotations.pop(spec, None)
        for k, val in o.items():
            if k == "!remove":
                continue
            if k.startswith("!"):
                add("ERROR", "E-annotation", where + "/annotations.json", f"未知键: {k}")
                continue
            if not isinstance(val, dict):
                add("ERROR", "E-annotation", where + "/annotations.json", f"{k} 必须是对象")
                continue
            if set(val.keys()) - {"class", "attributes"}:
                add("ERROR", "E-annotation", where + "/annotations.json", f"{k} 存在未知字段")
            cls = val.get("class")
            if not isinstance(cls, str) or not cls:
                add("ERROR", "E-annotation", where + "/annotations.json", f"{k} 缺少字符串 class")
                continue
            attrs = {}
            raw_attrs = val.get("attributes", {})
            if not isinstance(raw_attrs, dict):
                add("ERROR", "E-annotation", where + "/annotations.json", f"{k}.attributes 必须是对象")
                continue
            for ak, av in raw_attrs.items():
                if not isinstance(av, dict):
                    add("ERROR", "E-annotation", where + "/annotations.json", f"{k}.{ak} 必须是对象")
                    continue
                if set(av.keys()) - {"name", "valueClass"}:
                    add("ERROR", "E-annotation", where + "/annotations.json", f"{k}.{ak} 存在未知字段")
                name = av.get("name", ak)
                if not isinstance(name, str) or not name:
                    add("ERROR", "E-annotation", where + "/annotations.json", f"{k}.{ak}.name 必须是字符串")
                    continue
                value_class = av.get("valueClass")
                if value_class is not None and not isinstance(value_class, str):
                    add("ERROR", "E-annotation", where + "/annotations.json", f"{k}.{ak}.valueClass 必须是字符串")
                    continue
                if value_class is not None and value_class not in ds.classes:
                    add("ERROR", "E-annotation", where + "/annotations.json",
                        f"{k}.{ak}.valueClass 未在 classes IR 中定义: {value_class}")
                    continue
                attrs[ak] = {"name": name, "valueClass": value_class}
            ds.annotations[k] = {"class": cls, "attributes": attrs}


# ---------------------------------------------------------------------------
# 契约数据
# ---------------------------------------------------------------------------

def fabric_closed_set():
    """从 FABRIC-IR-CONTRACT.md 第 2 节的代码块里读出 fabric.* / mixin.* 清单。"""
    path = os.path.join(ROOT, "FABRIC-IR-CONTRACT.md")
    text = open(path, encoding="utf-8").read()
    m = re.search(r"## 2\.(.*?)\n## 3\.", text, re.S)
    sec = m.group(1) if m else ""
    ids = set()
    for block in re.findall(r"```(.*?)```", sec, re.S):
        for line in block.splitlines():
            tok = line.strip().split()
            if tok and (tok[0].startswith("fabric.") or tok[0].startswith("mixin.")):
                ids.add(tok[0])
    return ids


def neoforge_closed_set():
    """从 NEOFORGE-IR-CONTRACT.md 第 2.1 节的代码块里读出 neoforge.* 清单。"""
    path = os.path.join(ROOT, "NEOFORGE-IR-CONTRACT.md")
    text = open(path, encoding="utf-8").read()
    m = re.search(r"### 2\.1(.*?)\n## 3\.", text, re.S)
    ids = set()
    for block in re.findall(r"```(.*?)```", m.group(1) if m else "", re.S):
        for line in block.splitlines():
            tok = line.strip().split()
            if tok and tok[0].startswith("neoforge."):
                ids.add(tok[0])
    return ids


def java_platform_versions():
    vs = set()
    for fn in os.listdir(JAVA_DIR):
        if not fn.endswith(".json") or fn == "features.json":
            continue
        o = read_json(os.path.join(JAVA_DIR, fn), "java/" + fn)
        if o is None:
            continue
        stem = fn[:-5]
        if stem.isdigit():
            vs.add(int(stem))
        for a in o.get("aliases", []) or []:
            vs.add(int(a))
    read_json(os.path.join(JAVA_DIR, "features.json"), "java/features.json")
    return vs


def is_official(ds):
    """是否使用 Mojang 官方名（以 mappingsChannel 为准；Forge 1.12–1.16 用 MCP 名，Fabric 1.15–1.21.11 用 Yarn 名）。"""
    return ds.info.get("mappingsChannel") == "official"


def required_templates(ds):
    fmt = ds.info.get("metadataFormat")
    meta = {"mcmod.info": "mcmod.info", "fabric.mod.json": "fabric.mod.json"}.get(fmt, "mods.toml")
    if ds.loader == "fabric":
        return ["build.gradle", "fabric.mod.json"]
    if ds.loader == "neoforge":
        return ["build.gradle", "mods.toml"]
    return ["build.gradle", meta]


def find_template(ds, name):
    for d in ds.template_dirs:
        p = os.path.join(d, name)
        if os.path.isfile(p):
            return p
    return None


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="映射数据全局校验")
    ap.add_argument("--loader", help="只检查某个 loader")
    ap.add_argument("--verbose", "-v", action="store_true", help="逐条打印问题")
    ap.add_argument("--json", help="把问题列表写入 JSON 文件")
    ap.add_argument("--limit", type=int, default=15, help="每类问题在非 verbose 模式下最多打印的条数")
    args = ap.parse_args()

    repo = Repo()
    java_vs = java_platform_versions()
    closed = fabric_closed_set()
    neo_closed = neoforge_closed_set()
    loaders = [args.loader] if args.loader else sorted(repo.dirs)

    # ---- 别名检查 ----
    for loader in sorted(repo.dirs):
        for a, owners in repo.alias_owner[loader].items():
            if a in repo.dirs[loader]:
                add("ERROR", "E-alias", f"{loader}/{a}", f"别名与目录重名（声明于 {owners}），别名永远不会生效")
            if len(owners) > 1:
                add("ERROR", "E-alias", f"{loader}/{a}", f"别名被多个目录声明: {owners}（引擎取目录遍历顺序的第一个）")

    # ---- 载入全部有效数据 ----
    all_ds = {}
    for loader in sorted(repo.dirs):
        for n in repo.names(loader):
            ds = repo.load(loader, n)
            if ds is not None:
                all_ds[(loader, n)] = ds

    # 全局 concept id 集合：removed 条目的 concept ∪ 各 guidance 键中不是类 IR 的
    all_class_irs = set()
    irs_by_loader = defaultdict(set)
    for (loader, n), ds in all_ds.items():
        all_class_irs |= set(ds.classes)
        irs_by_loader[loader] |= set(ds.classes)
    # 原始文件里的键也算（被 !remove 掉的 id 仍是合法 IR）
    for (loader, d), files in repo.raw.items():
        for k in (files.get("classes.json") or {}):
            if k != "!remove":
                all_class_irs.add(k)
                irs_by_loader[loader].add(k)
    concepts = set()
    for (loader, d), files in repo.raw.items():
        r = files.get("removed.json") or {}
        for sect in ("classes", "members"):
            for v in (r.get(sect) or {}).values():
                if v.get("concept"):
                    concepts.add(v["concept"])
        i = files.get("idioms.json") or {}
        for k in (i.get("guidance") or {}):
            if k not in all_class_irs:
                concepts.add(k)
        for k in i.get("supported", []) or []:
            concepts.add(k)

    for (loader, n), ds in sorted(all_ds.items()):
        if loader not in loaders:
            continue
        where = f"{loader}/{n}"
        info = ds.info
        # ---- version.json 字段 ----
        for k in ("mcVersion", "metadataFormat", "metadataPath", "lifecycleStyle"):
            if not info.get(k):
                add("ERROR", "E-version", where, f"缺少 {k}")
        if not isinstance(info.get("packFormat"), int) or info.get("packFormat", 0) <= 0:
            add("ERROR", "E-version", where, "缺少 packFormat")
        # ---- split resource/data formats (optional for legacy external datasets) ----
        for key in ("resourcePackFormat", "dataPackFormat"):
            value = info.get(key)
            if value is not None and not (isinstance(value, list) and len(value) == 2
                    and all(type(x) is int and 0 <= x <= 2147483647 for x in value)):
                add("ERROR", "E-packFormat", where, f"{key} 必须为两个非负整数 [major, minor]")
        style = info.get("packMetadataStyle")
        if style is not None and style not in ("legacy", "range"):
            add("ERROR", "E-packFormat", where, "packMetadataStyle 必须为 legacy 或 range")
        if "itemModelDefinitions" in info and type(info["itemModelDefinitions"]) is not bool:
            add("ERROR", "E-packFormat", where, "itemModelDefinitions 必须为 boolean")
        if style == "range" and (info.get("resourcePackFormat") is None or info.get("dataPackFormat") is None):
            add("ERROR", "E-packFormat", where, "range 数据集缺少完整资源/数据包格式")
        if style == "legacy":
            for key in ("resourcePackFormat", "dataPackFormat"):
                value = info.get(key)
                if isinstance(value, list) and len(value) == 2 and value[1] != 0:
                    add("ERROR", "E-packFormat", where, f"legacy {key} 不能有次版本号")
        if info.get("mcVersion") != n:
            add("ERROR", "E-version", where, f"mcVersion={info.get('mcVersion')} 与版本名不一致")
        if info.get("javaVersion") not in java_vs:
            add("ERROR", "E-version", where, f"javaVersion={info.get('javaVersion')} 在 mappings/java/ 中没有平台文件")
        if info.get("loader") and info.get("loader") != loader:
            add("ERROR", "E-version", where, f"loader 字段为 {info.get('loader')}")
        # ---- 模板 ----
        for t in required_templates(ds):
            if not find_template(ds, t):
                add("ERROR", "E-template", where, f"templateDirs 链上找不到 {t}")
        allowed = ENGINE_PLACEHOLDERS | set(info.get("extras", {}))
        seen_t = set()
        for td in ds.template_dirs:
            if not os.path.isdir(td):
                continue
            for fn in os.listdir(td):
                if fn in seen_t:
                    continue  # 被上层同名模板遮蔽
                seen_t.add(fn)
                text = open(os.path.join(td, fn), encoding="utf-8").read()
                for ph in re.findall(r"\$\{([^}]*)\}", text):
                    if ph in allowed or ph in GROOVY_ALLOWED:
                        continue
                    # 含 . 或 ( 的视为 Groovy 表达式（Gradle 运行期插值）
                    if fn.endswith(".gradle") and re.search(r"[.()]", ph):
                        continue
                    add("ERROR", "E-placeholder", where, f"{os.path.relpath(os.path.join(td, fn), VERSIONS)} 中的 ${{{ph}}} 引擎无法替换")
        # ---- NeoForge 必需 IR ----
        if loader == "neoforge":
            for ir in ("forge.Mod", "forge.SubscribeEvent"):
                if ir not in ds.classes:
                    add("ERROR", "E-forgeIr", where, f"未映射 {ir}")
        if ds.is_alias:
            continue  # 以下为数据层检查，别名与其目录共享数据
        # ---- 同一数据集内重复 FQCN ----
        for fq, irs in ds.ir_by_fqcn().items():
            if len(irs) > 1:
                primaries = [ir for ir in irs if ds.classes[ir].get("primary")]
                if len(primaries) == 1:
                    add("INFO", "I-primaryFqcn", where,
                        f"{fq} <- {sorted(irs)}，primary={primaries[0]}")
                else:
                    add("ERROR", "E-dupFqcn", where,
                        f"{fq} <- {sorted(irs)}，必须恰有一个 primary（当前 {len(primaries)} 个）")
        # ---- 死 removed 条目 ----
        mapped_fq = {e["name"] for e in ds.classes.values()}
        for fq in ds.removed_classes:
            if fq in mapped_fq:
                add("WARN", "W-deadRemoved", where, f"{fq} 已在 classes.json 中映射，removed 条目不生效")
        # ---- Fabric 封闭集合 ----
        if loader == "fabric":
            for ir in ds.classes:
                if (ir.startswith("fabric.") or ir.startswith("mixin.")) and ir not in closed:
                    add("ERROR", "E-fabricIr", where, f"{ir} 不在 FABRIC-IR-CONTRACT 第 2 节清单中")
        if loader in ("neoforge", "forge"):
            # Forge 数据集复用 neoforge.* id 同样受封闭清单约束（NEOFORGE-IR-CONTRACT 2.2/2.3）
            for ir in ds.classes:
                if ir.startswith("neoforge.") and ir not in neo_closed:
                    add("ERROR", "E-neoforgeIr", where, f"{ir} 不在 NEOFORGE-IR-CONTRACT 第 2.1 节清单中")
        # ---- guidance 覆盖（非 Forge） ----
        if loader != "forge":
            need = set(concepts) | (irs_by_loader[loader] - set(ds.classes))
            missing = sorted(k for k in need if k not in ds.guidance)
            for k in missing:
                add("ERROR", "E-guidance", where, f"缺少 guidance: {k}")

    # ---- 官方名 FQCN -> IR 跨数据集一致性 ----
    fq_ir = defaultdict(set)
    fq_where = defaultdict(set)
    for (loader, n), ds in all_ds.items():
        if ds.is_alias or not is_official(ds):
            continue
        for ir, e in ds.classes.items():
            if ir.startswith("mc."):
                fq_ir[e["name"]].add(ir)
                fq_where[(e["name"], ir)].add(f"{loader}/{n}")
    for fq, irs in sorted(fq_ir.items()):
        if len(irs) > 1:
            det = "; ".join(f"{ir}@{sorted(fq_where[(fq, ir)])[:4]}" for ir in sorted(irs))
            # 归属：只出现在某一 loader 的少数派 IR 记到该 loader 名下，便于按 loader 统计
            ir_loaders = {ir: {w.split("/")[0] for w in fq_where[(fq, ir)]} for ir in irs}
            owner = sorted({next(iter(ls)) for ls in ir_loaders.values() if len(ls) == 1}) or ["mixed"]
            if len(owner) > 1 and "forge" in owner:
                owner = ["forge"]
            if any(o in loaders or o == "mixed" for o in owner):
                add("ERROR", "E-idConflict", "+".join(owner) + "/" + fq, det)

    # ---- mc.* 命名规则（DATA-RULES 第 1 条） ----
    forge_irs = irs_by_loader.get("forge", set())
    ir_official = defaultdict(set)
    for (loader, n), ds in all_ds.items():
        if ds.is_alias or not is_official(ds):
            continue
        for ir, e in ds.classes.items():
            if ir.startswith("mc."):
                ir_official[ir].add(e["name"])
    for ir, fqs in sorted(ir_official.items()):
        expect = {"mc." + f[len("net.minecraft."):] for f in fqs if f.startswith("net.minecraft.")}
        if ir not in expect and ir not in forge_irs:
            add("WARN", "W-mcId", ir, f"不符合 mc.+官方名规则；官方名: {sorted(fqs)[:3]}")

    # ---- 跨版本可达性（同 loader 任意两个版本） ----
    for loader in loaders:
        names = [n for n in repo.names(loader) if (loader, n) in all_ds]
        # 别名与目录共享数据：只按目录计算，结论对别名同样成立
        dirs = [n for n in names if not all_ds[(loader, n)].is_alias]
        for s in dirs:
            S = all_ds[(loader, s)]
            s_fq = {e["name"] for e in S.classes.values()}
            for t in dirs:
                if s == t:
                    continue
                T = all_ds[(loader, t)]
                miss = sorted(ir for ir in S.classes if ir not in T.classes and ir not in T.guidance)
                for ir in miss:
                    add("ERROR", "E-cross", f"{loader} {s}->{t}", f"{ir} 既无映射也无 guidance")
                # removed 概念：supported / guidance / message 三者至少其一
                for fq, e in S.removed_classes.items():
                    if fq in s_fq:
                        continue
                    c = e.get("concept")
                    if c and (c in T.supported or c in T.guidance):
                        continue
                    if not e.get("message"):
                        add("ERROR", "E-cross", f"{loader} {s}->{t}", f"removed 类 {fq}（concept={c}）无 guidance 也无 message")
                    elif c:
                        add("WARN", "W-concept", f"{loader} {s}->{t}", f"concept {c} 无 guidance（回退 message）")

    # ---- 输出 ----
    by_code = defaultdict(list)
    for it in issues:
        by_code[(it.level, it.code)].append(it)
    ds_count = defaultdict(lambda: defaultdict(int))
    for (loader, n), ds in all_ds.items():
        ds_count[loader][ds.kind] += 1
    print("== 数据集统计 ==")
    for loader in sorted(ds_count):
        c = ds_count[loader]
        total = sum(c.values())
        print(f"  {loader:9s} 可选版本 {total:3d}（full {c['full']}, overlay {c['overlay']}, alias {c['alias']}），"
              f"同 loader 有向转换路径 {total * (total - 1)}")
    print(f"  全局 concept {len(concepts)} 个，类 IR {len(all_class_irs)} 个")
    print("== 问题汇总 ==")
    if not issues:
        print("  无问题")
    for (lv, code), lst in sorted(by_code.items()):
        loaders_hit = defaultdict(int)
        for it in lst:
            loaders_hit[it.where.split("/")[0].split(" ")[0]] += 1
        print(f"  {lv:5s} {code:14s} {len(lst):6d}  " + ", ".join(f"{k}:{v}" for k, v in sorted(loaders_hit.items())))
        shown = lst if args.verbose else lst[:args.limit]
        for it in shown:
            print("      " + f"{it.where}: {it.msg}")
        if not args.verbose and len(lst) > args.limit:
            print(f"      ……另有 {len(lst) - args.limit} 条（--verbose 查看全部）")
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump([it.__dict__ for it in issues], f, ensure_ascii=False, indent=1)
    errors = sum(1 for it in issues if it.level == "ERROR")
    warns = sum(1 for it in issues if it.level == "WARN")
    infos = sum(1 for it in issues if it.level == "INFO")
    print(f"== 合计：ERROR {errors}，WARN {warns}，INFO {infos} ==")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
