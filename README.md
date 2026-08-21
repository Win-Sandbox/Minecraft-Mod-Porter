Minecraft-Mod-Porter

A source-code cross-version porting tool for Minecraft mods.

Currently supports bidirectional conversion across multiple Forge and Fabric versions. The conversion engine itself is version-agnostic.

Features

* Fully data-driven — All version-specific knowledge is stored as JSON mapping data under mappings/. The conversion engine contains no hard-coded version knowledge.
* IR / Pivot architecture — Each version only maintains one mapping to the canonical IR. Every conversion follows Source → IR → Target, enabling direct conversion between any supported versions without chained intermediate conversions.
* Safe fallback — Code that cannot be converted automatically is preserved and annotated with // TODO [modporter] .... All such issues are included in the conversion report.
* Library-first architecture — The core API is exposed through io.modporter.core.PortEngine, allowing the CLI and future UIs to share the same backend.
* Extensible mapping system — New versions can be added through mapping data without modifying the conversion engine.
* Java version migration support — Java 8 / 16 / 17 / 21 compatibility is maintained separately from Minecraft version mappings.
* Native Windows frontends — WinUI 3 and native Win32 frontends share the same backend and configuration.

⸻

Build & Usage

Windows users: See BUILD-WINDOWS.md for the complete Windows build guide.

gradle jar

The resulting fat JAR is:

build/libs/mc-mod-porter-0.1.0.jar

List supported versions:

modporter versions

Convert a mod:

modporter port \
  --from 1.12.2 --to 1.19.2 \
  --input /path/to/old-mod-project \
  --output /path/to/new-mod-project \
  [--loader forge] [--mappings ./mappings] [--dry-run]

The mapping directory defaults to ./mappings. It can also be specified with MODPORTER_MAPPINGS or --mappings.

Conversion Reports

The output directory contains:

File	Description
MODPORTER-REPORT.md	Human-readable errors, warnings, manual-action items, and automatic rewrite details
modporter-report.json	Structured report for UIs and scripts
MODPORTER-TODOS.md	Consolidated TODO / FIXME list, including both generated and pre-existing comments

⸻

Conversion Coverage

Content	Handling
Java source	AST-level rewriting of imports, types, methods, fields, overridden methods, this / super calls, annotations, lifecycle APIs, and common API idioms
Metadata	mcmod.info ↔ mods.toml through IR and target templates
Language files	.lang ↔ .json, including localization-key migration
Blockstates / models	Variant conversion, texture path changes, and forge_marker TODOs
pack.mcmeta	Automatic pack_format update
build.gradle	Regenerated from the target-version template; custom logic is reported for manual migration
Third-party dependencies	Not handled by design

Examples of Java-level transformations include:

new TextComponentString(x)

→

Component.literal(x)

and overridden methods such as:

@Override
public void readFromNBT(...)

→

@Override
public void load(...)

with corresponding this / super calls updated as well.

Structural migrations such as:

GameRegistry → DeferredRegister
SimpleNetworkWrapper → SimpleChannel

are intentionally not blindly rewritten. They receive TODOs and migration guidance instead.

⸻

Architecture

Source Project
      │
      ▼
Source Version Mapping
      │
      ▼
     IR
      │
      ▼
Target Version Mapping
      │
      ▼
Target Project

The engine does not perform:

1.12 → 1.13 → 1.14 → ... → 1.19

Instead, every conversion is:

1.12 → IR → 1.19

This makes the system scale with the number of supported versions rather than the number of version pairs.

Supporting N versions requires N mapping datasets, rather than maintaining mappings for every possible version pair.

⸻

Mapping System

Version-specific data is stored under:

mappings/versions/<loader>/<mcVersion>/

A typical dataset contains:

mappings/versions/forge/1.19.2/
├── version.json
├── classes.json
├── members.json
├── removed.json
├── idioms.json
└── templates/

Mapping Components

File	Purpose
version.json	Version characteristics, Java version, Forge version, metadata format, lifecycle style, pack_format, Gradle version, etc.
classes.json	IR class IDs → version-specific FQCNs
members.json	IR members → version-specific names and forms
removed.json	Removed concepts and migration guidance
idioms.json	Version-specific forms such as constructors ↔ static factories
templates/	mods.toml, mcmod.info, build.gradle, and other target templates

Adding a new version requires only a new mapping dataset; the engine itself does not need to be modified.

⸻

Java Platform Mappings

Java compatibility is maintained independently under:

mappings/java/

Minecraft versions can therefore map to different Java platforms such as:

Java 8 → Java 16 → Java 17 → Java 21

The system can detect or handle:

* Newer syntax such as var, records, switch expressions, pattern matching, and sealed classes
* Illegal identifiers
* Restricted type names
* Removed JDK classes
* Encapsulated internal packages
* Removed methods
* APIs whose arguments became invalid
* Reflective lookups such as Class.forName(...)

The Java 8 downgrade mapping currently covers 40 APIs introduced between Java 9 and Java 16, including examples such as:

List.of
String.isBlank
Stream.toList
Files.readString
Optional.isEmpty

⸻

Version Reuse

Two mechanisms prevent unnecessary duplication.

Aliases

For fully compatible versions:

"aliases": {
  "1.19.1": {
    "forgeVersion": "42.0.9",
    "loaderVersionRange": "[42,)"
  }
}

The alias reuses the same mapping data while allowing metadata overrides.

Overlays

For versions with minor differences:

"basedOn": "1.19.4"

Only differences are stored. The base mapping is loaded first and then overridden by the overlay.

Aliases and overlays can also be combined.

⸻

Supported Versions

Fabric

9 datasets, using Yarn mappings:

Version	Java
1.15.2 / 1.16.5	8
1.17.1	16
1.18.2	17
1.19.2 / 1.19.4	17
1.20.1 / 1.20.4	17
1.21.1	21

Fabric and Forge share the same mc.* IR IDs while using different version-specific mappings.

See FABRIC-IR-CONTRACT.md.

Fabric did not exist before Minecraft 1.14, so there are no Fabric 1.12.x mappings.

Forge

13 datasets + 7 aliases = 20 selectable versions:

Dataset	Type	Aliases
1.12.2	Full	1.12, 1.12.1
1.14.4	Overlay	—
1.15.2	Full	—
1.16.5	Full	1.16.4
1.17.1	Full	—
1.18.1	Overlay	1.18
1.18.2	Full	—
1.19.1	Overlay	1.19
1.19.2	Full	—
1.19.3	Overlay	—
1.19.4	Full	—
1.20.1	Full	1.20
1.21.1	Overlay	1.21

This provides 380 directed Forge conversion paths and 72 directed Fabric conversion paths.

⸻

Cross-Loader Conversion

Forge ↔ Fabric conversion is currently not supported.

The port command requires the source and target to use the same loader.

The architecture already reserves the necessary infrastructure:

* Both loaders share the mc.* IR namespace.
* Mapping data contains cross-loader migration guidance.
* crossloader-port already exists in the capability system as available: false.

Cross-loader conversion will remain more difficult than same-loader conversion because Forge and Fabric differ significantly in entry points, event systems, registration, networking, and other architectural components.

Many cases will therefore require manual migration even after cross-loader support is implemented.

⸻

Frontends

Two functionally equivalent Windows frontends are provided:

Frontend	Technology	Target
frontend/ModPorter.WinUI/	WinUI 3 / C#	Windows 10 1809+
frontend/ModPorter.Win32/	Native Win32 API / C++11	Windows XP ~ 11

The WinUI frontend uses Fluent design with Mica and NavigationView.

The Win32 frontend has no .NET / WinRT dependency and uses statically linked CRT for a standalone executable.

Both frontends share:

%LOCALAPPDATA%\ModPorter\settings.json

and communicate with the backend through three versioned JSON interfaces:

modporter capabilities
modporter run <actionId> --params <json>
modporter-report.json

This allows backend capabilities to be extended without requiring corresponding frontend changes.

Currently reserved actions include:

* Cross-loader conversion
* Batch conversion
* Mapping data package import

⸻

Library API

The core API is exposed through:

io.modporter.core

Example:

PortEngine engine =
    new DefaultPortEngine(new MappingRepository(mappingsDir));
engine.supportedVersions();
PortResult result =
    engine.port(request, listener);
result.report().entries();

listener provides per-file progress callbacks, while the structured report exposes severity, file, line, category, and message information.

⸻

IR Contract

The IR provides stable identifiers independent of concrete Minecraft versions.

Class IDs

Examples:

mc.item.Item
forge.SubscribeEvent

Member IDs

For example:

putInt

Members not explicitly listed in members.json are assumed to retain their IR name and shape.

Concept IDs

Used by removed.json and guidance as semantic migration anchors.

The source version identifies what a symbol represents; the target version independently describes how that concept should be implemented.

Idiom IDs

Used by idioms.json to represent different syntactic forms of the same semantic operation, such as:

constructor ↔ static factory

⸻

Semantic Migration

The converter distinguishes between simple renaming and actual semantic migration.

For example:

FMLPreInitializationEvent
FMLInitializationEvent

map to canonical lifecycle concepts and can ultimately become FMLCommonSetupEvent in newer Forge versions, with a migration note when methods may need to be merged.

Similarly:

@Mod.EventHandler

can be converted to:

@SubscribeEvent

while adding a TODO explaining that the method must be registered with the appropriate event bus.

For mechanisms that were fundamentally redesigned, such as GameRegistry, the converter preserves the original code and provides migration guidance rather than pretending that a textual replacement is sufficient.

⸻

Mapping Sources

The 1.12.2 → 1.19.2 mappings were cross-checked against:

* williewillus — 1.13/1.14 Update Primer
* Forge — Porting 1.12 to 1.14
* ChampionAsh5357’s 1.18.2 → 1.19 migration primer

These references were used to verify changes involving registration, lifecycle, flattening, models, language files, recipes, networking, TileEntities, Capability, world generation, RegisterEvent, component APIs, event packages, and other version-specific changes.

Known symbol collisions are represented as ambiguous candidates. When the engine cannot determine the correct receiver type, it does not guess: the original code is preserved and a TODO is generated.

⸻

Known Limitations

v0.1

* Member renaming is heuristic: Complete type inference is not currently implemented. Static calls can validate their scope, while instance calls may require manual review.
* Ambiguous mappings are never guessed: They receive TODOs instead.
* Formatting is not preserved: JavaParser’s standard formatter is used; whitespace and alignment may change, while ordinary comments are retained.
* Structural migrations are not automatic: Registration systems, networking, Capability, GUI/Container architecture, and similar large-scale changes require manual work.
* Some semantic changes require manual restructuring: Examples include item metadata/subtypes and block-property builders.
* mods.toml parsing is intentionally limited: Only commonly used fields are currently supported.
* Downgrade conversion is less extensively validated: The architecture and mappings support bidirectional conversion, but paths such as 1.19 → 1.12 require additional testing.
* Third-party dependencies are not converted.

⸻

Project Structure

src/main/java/io/modporter/
├── core/       # Public API: PortEngine / PortRequest / PortResult / Report / ProgressListener
├── mappings/   # MappingRepository / MappingResolver
├── engine/     # DefaultPortEngine / ModMeta
├── passes/     # JavaSourcePass / MetadataPass / LangPass / AssetJsonPass / BuildGradlePass
└── cli/        # picocli command-line entry point

⸻

Roadmap

The capability system already reserves extension points for:

* Cross-loader conversion
* Fabric / Forge / Quilt / NeoForge interoperability
* Batch conversion
* Mapping data package import
* Additional Minecraft versions
* Additional Java platform migrations
* More structural API migrations
* Expanded downgrade validation
* Additional frontend integrations

⸻

Design Philosophy

mc-mod-porter does not attempt to blindly rewrite every API difference.

Its primary goals are:

1. Keep version-specific knowledge in data, not code.
2. Use a stable IR to avoid pairwise version mappings.
3. Automate deterministic transformations.
4. Preserve code when a safe transformation cannot be determined.
5. Explain manual work through TODOs and structured reports.
6. Keep the core independent from the CLI and UI.

The goal is not to claim that every Minecraft mod can be converted automatically.

The goal is to make cross-version porting systematic, repeatable, inspectable, and progressively more automatable, while keeping the developer in control of changes that require understanding the mod’s architecture.

⸻

License

See the repository’s LICENSE file for licensing and redistribution terms.