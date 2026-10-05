# Resource / data pack formats (2026-10-04)

Evidence: official Mojang version manifest and HTTP Range extraction of client.jar `version.json`; no client executed and no project compiled.
- Manifest: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- Range schema and overlay rules: https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9
- Item definitions / overrides removal: https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4

`resourcePackFormat` / `dataPackFormat` are exact two-integer arrays; `packMetadataStyle` is legacy or range. `itemModelDefinitions` is confirmed by the presence of `assets/minecraft/items/*.json` in each official client ZIP directory.
All directories and aliases explicitly set capabilities so reverse-basedOn and hotfix aliases cannot inherit an incorrect version. `packFormat` remains the resource major for legacy consumers.
1.12 / 1.12.1 / 1.12.2 have no version.json; resource format 3 is retained from pre-existing project data, NOT newly verified here. dataPackFormat is explicitly null (no unsupported claim).
Raw per-version evidence and extraction script are archived in shared/modporter/20261004-resource-update/official-data/ and verify_pack_data.py. Only ZIP directory ranges and the single metadata entry were read, not full clients.

| Version | Resource | Data | Item definitions | Official metadata |
|---|---|---|---|---|
| 1.12 | 3.0 | unknown/not supported | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/da76e0a25ffccf2765f9e86ce61c063e44b2183b/1.12.json) |
| 1.12.1 | 3.0 | unknown/not supported | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/5b3e7d137ea360e1d418f0cf68de160acf93fbff/1.12.1.json) |
| 1.12.2 | 3.0 | unknown/not supported | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) |
| 1.14.4 | 4.0 | 4.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/be146d5f66a3627ed0a87c234c4d8dde8ab35098/1.14.4.json) |
| 1.15.2 | 5.0 | 5.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/e9d0adb8f642abe422909ede50f651b2b58a3573/1.15.2.json) |
| 1.16 | 5.0 | 5.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/e9d21d375f9c961f0e9731d4e463306d76e77c48/1.16.json) |
| 1.16.1 | 5.0 | 5.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/54fa3af57d041d2771e66d390197b2c0288e697c/1.16.1.json) |
| 1.16.2 | 6.0 | 6.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/998d9ef5770d05c20d760dc16cf85151f35009f2/1.16.2.json) |
| 1.16.3 | 6.0 | 6.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/6485dd131ef68c968041a9f6fd73094b027e42e1/1.16.3.json) |
| 1.16.4 | 6.0 | 6.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/596ad61fda7612d9edf8881cf81869276bdb7f82/1.16.4.json) |
| 1.16.5 | 6.0 | 6.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/fba9f7833e858a1257d810d21a3a9e3c967f9077/1.16.5.json) |
| 1.17.1 | 7.0 | 7.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/e0e7ab5ed6f55bbd874ef95be3c9356d67e64b57/1.17.1.json) |
| 1.18 | 8.0 | 8.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/7367ea8b7cad7c7830192441bb2846be0d2ceeac/1.18.json) |
| 1.18.1 | 8.0 | 8.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/7ff864e988a2c29907154d5f9701e87e5d5e554a/1.18.1.json) |
| 1.18.2 | 8.0 | 9.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/334b33fcba3c9be4b7514624c965256535bd7eba/1.18.2.json) |
| 1.19 | 9.0 | 10.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/14bbfb25fb1c1c798e3c9b9482b081a78d1f3a9d/1.19.json) |
| 1.19.1 | 9.0 | 10.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/39d5e8925d37490c6f2abb2e02b8c6f1b35719df/1.19.1.json) |
| 1.19.2 | 9.0 | 10.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/ed548106acf3ac7e8205a6ee8fd2710facfa164f/1.19.2.json) |
| 1.19.3 | 12.0 | 10.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/c3130557321381c9063c259d012fa5d27ae33f68/1.19.3.json) |
| 1.19.4 | 13.0 | 12.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/b67b917c119918ddd3aa43ad98eed089a06dbc97/1.19.4.json) |
| 1.20 | 15.0 | 15.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/f8e75b306c980514a20178dfd248074efb94499c/1.20.json) |
| 1.20.1 | 15.0 | 15.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/c0a00f47b3dae01d83e21be9a646c9232379d9ab/1.20.1.json) |
| 1.20.2 | 18.0 | 18.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/fbdf078829aed78fd2ef22a53470aa7e37f74e5c/1.20.2.json) |
| 1.20.3 | 22.0 | 26.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/0fe5e3bb3fe9701374fecd6c8eda027b876565ec/1.20.3.json) |
| 1.20.4 | 22.0 | 26.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/bff1278922bca138b7722385a81366f3077abffa/1.20.4.json) |
| 1.20.5 | 32.0 | 41.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/2b95c082b76616fcf6e2aa9fe443a45c15ccb435/1.20.5.json) |
| 1.20.6 | 32.0 | 41.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/84c5b02d36a9be4560597687f9e2d19d69fada5d/1.20.6.json) |
| 1.21 | 34.0 | 48.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/beaf2987935d1a33ffe90a61c812e444ca5a38cc/1.21.json) |
| 1.21.1 | 34.0 | 48.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/22a1966494dfa4eeb5ee778c8e6ed5b774839582/1.21.1.json) |
| 1.21.2 | 42.0 | 57.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/80e1de30a38523ed5ba094ae92efeebc28a81be8/1.21.2.json) |
| 1.21.3 | 42.0 | 57.0 | False | [manifest entry](https://piston-meta.mojang.com/v1/packages/bc2ae20dec0804f5d3f73943d6517ce8ceb36cc0/1.21.3.json) |
| 1.21.4 | 46.0 | 61.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/b547a27fc4d490dc10d62e40a42ace065162b644/1.21.4.json) |
| 1.21.5 | 55.0 | 71.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/5b1e09e69f4f9c650ba11c36d8b27fd0153e4e82/1.21.5.json) |
| 1.21.6 | 63.0 | 80.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/803d74fb7bf8f1e5cdcb387e411d0335721060da/1.21.6.json) |
| 1.21.7 | 64.0 | 81.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/0a6e46f9485369b13e18a81376ba5751a3bcaaba/1.21.7.json) |
| 1.21.8 | 64.0 | 81.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/403dee3925f64c7138b72a5302130829cb588784/1.21.8.json) |
| 1.21.9 | 69.0 | 88.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/5f4990c6189ca01b97c58996bec83ebcd879f0a6/1.21.9.json) |
| 1.21.10 | 69.0 | 88.0 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/a00569761f3e217e9a71424ff21ecdf467f5b835/1.21.10.json) |
| 1.21.11 | 75.0 | 94.1 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/4f6bd9388f12e9d7adc2ded64acba66212d60521/1.21.11.json) |
| 26.1 | 84.0 | 101.1 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/84d0ff7bd4428695691af5a178edae22c7c83d89/26.1.json) |
| 26.1.1 | 84.0 | 101.1 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/5a42f7f729831da920768fc30db7f0b19b9eaa20/26.1.1.json) |
| 26.1.2 | 84.0 | 101.1 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/ee044d53f31fdcbfa31be3684ad321d44c81c3a5/26.1.2.json) |
| 26.2 | 88.0 | 107.1 | True | [manifest entry](https://piston-meta.mojang.com/v1/packages/c7868781b30aaf24be0dac894c94a34e5d6df10d/26.2.json) |
