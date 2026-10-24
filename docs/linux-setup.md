# Moving the MCA dev setup to Linux (instructions for Claude)

Written 2026-10-24 on the Windows machine, for the Claude Code session that sets this up on the user's Linux PC.
Follow the steps in order. The user speaks Portuguese; write code identifiers in English. Ask before anything that
publishes (creating repos, pushing to remotes other than the ones named here).

The Windows layout was `C:\Users\andre\Downloads\other\` with all repos side by side. On Linux, use one parent folder
too (suggested: `~/dev/other/`). Below, `$OTHER` means that folder.

## 1. What to install

- `git`, `gh` (log in as **Andre220**: `gh auth login`), `python3` with `pillow` (`pip install pillow`).
- **JDK 21** (or newer) to run Gradle. The projects build with Java **25** through Gradle toolchains: the foojay
  resolver downloads it by itself. Installing Temurin 25 by hand also works.
- Claude Code.
- Minecraft 26.2 runs from Gradle (`:fabric:runClient`), so there is no launcher to install. It needs GPU drivers with
  OpenGL. Under Wayland, if the window doesn't open, run it through XWayland.
- Git line endings: Windows had `core.autocrlf=true` and the repos store LF. On Linux use
  `git config --global core.autocrlf input`. Some source files have CRLF in the working tree, so edit scripts must
  keep each file's own newline style.

## 2. The repositories

| Folder | Remote | Branch | Notes |
|---|---|---|---|
| `minecraft-comes-alive` | `fork` = https://github.com/Andre220/minecraft-comes-alive.git, `origin` = https://github.com/Luke100000/minecraft-comes-alive.git (upstream, read only) | `feature/skidward-villager-layers` | The main work; the PR base is `1.21.1` upstream |
| (optional) `mca-workorders` | a git **worktree** of the MCA repo, only when working on it | `feature/villager-work-orders` (on the fork) | Village planning screen, builders and miners. 5 commits not merged into the main branch |
| `grounded-villages` | https://github.com/Andre220/grounded-villages.git (private) | `main` | Separate mod: terrain-fitted villages |
| `tabletop` | https://github.com/Andre220/tabletop.git (private) | `main` | Separate mod: place items on blocks |
| `village-furnishings` | **none yet**: comes as a git bundle (see 3) | `master` | Separate mod: shutters, casements, sinks |

```sh
mkdir -p "$OTHER" && cd "$OTHER"
git clone https://github.com/Andre220/minecraft-comes-alive.git
cd minecraft-comes-alive
git remote rename origin fork
git remote add origin https://github.com/Luke100000/minecraft-comes-alive.git
git fetch fork
git checkout feature/skidward-villager-layers
# only to work on the planning screen: git worktree add ../mca-workorders feature/villager-work-orders
cd "$OTHER"
gh repo clone Andre220/grounded-villages
gh repo clone Andre220/tabletop
git clone linux-transfer/village-furnishings.bundle village-furnishings
```

For Village Furnishings, creating its GitHub repo was blocked on Windows (it needed the user's permission). Ask the
user, then run `gh repo create Andre220/village-furnishings --private --source . --remote origin --push` inside it.

Everything was committed and pushed on 2026-10-24 (MCA `fe0f77063`, Village Furnishings `a0cf4c9`, work orders
`14969296f`). Check with `git status` / `git log` that each repo matches.

## 3. Files that are not in git: the `linux-transfer` folder

The user brings a folder `linux-transfer` (built on Windows next to the repos). Put each part in place:

| In `linux-transfer` | Goes to | What it is |
|---|---|---|
| `village-furnishings.bundle` | clone it (step 2) | The Village Furnishings repo with its history |
| `dev-mods/` | `minecraft-comes-alive/fabric/dev-mods/` | Jars added to the dev client at runtime (listed in `docs/dev-mods.md`) |
| `fabric-run/` | `minecraft-comes-alive/fabric/run/` | The dev client's game folder: `config/` (with `mca.json`), `mods/` (EMF, ETF), `resourcepacks/` (Fresh Animations), `saves/`, `options.txt` |
| `claude/CLAUDE.md` | `~/.claude/CLAUDE.md` | The user's global instructions |
| `claude/memory/` | `~/.claude/projects/<slug>/memory/` | Claude's memory of this project (see 4) |

Dev client flags that are on in `fabric/run/config/mca.json`:
- **Models:** `useSquidwardModels`, `squidwardHumanLikeModels`, `freshAnimationsIntegration` (Fresh Animations is
  the user's main path).
- **Expansion:** `villagerNeeds`, `villagerProperty`, `villagerRoutines`, `villagerEconomy`, `villagerWorkOrders`,
  `villagerBarter`, `villagerSpeechBubbles`, `villagerMouthAnimation`, `villagerBeards`, `villagerActionLabels`,
  `villagerArcheryPractice`, `villagerWardrobes`, `villagersChangeProfession`, `villagersInteractWithFenceGates`.
- **Others:** the notification flags and `villagerTagsHacks`.

## 4. Claude's own setup

- **Memory:** Claude Code keeps per-project memory in `~/.claude/projects/<slug>/memory/`. The slug is the project's
  absolute path with every `/` turned into `-`; for example, `/home/andre/dev/other/minecraft-comes-alive` becomes
  `-home-andre-dev-other-minecraft-comes-alive`.
  - Open Claude Code once in `$OTHER/minecraft-comes-alive` to see the exact folder, then copy
    `linux-transfer/claude/memory/*` into it. `MEMORY.md` is the index.
  - Some memories mention Windows paths (`C:\Users\andre\Downloads\other\...`): read them as `$OTHER/...`, and update
    them when touched.
- **Global `CLAUDE.md`:** asks to end every finished task with `🔔 Tarefa concluída: <nome curto em português>`. That
  line is read aloud by the **VoicedFeedback** hook. On Windows this was `VoicedFeedbackHook.exe` (Stop and
  Notification hooks in `~/.claude/settings.json`). It does not exist on Linux: don't copy those hooks. Ask the user
  whether they want a Linux replacement (for example `spd-say` or `piper` reading the last line). Keep the 🔔 line
  either way.
- **Helper scripts** live in the repo at `scripts/claude-tools/`:
  - `safe_gradle.sh`: the Linux version. It runs `./gradlew` only when the dev client is closed and prints
    `CLIENT OPEN - gradle skipped` otherwise. Use it for every Gradle call in the MCA repo.
  - `add_lang.py <langdir> <spec.json>`: adds en_us/pt_br keys without reformatting the lang files.
  - `guide_book.py`, `check_pages.py`, `add_chapter.py`: the in-game guide book (`ExpansionGuide.PAGES` must match).
  - `wardrobe_assets.py`: generates the wardrobe, dresser and outfit models, blockstates, loot, recipes, tags and
    names. Edit its geometry and re-run it.
  - `beards_png.py`, `board_assets.py`: beard textures, request board assets.

## 5. Working rules the user set (keep them)

- **The dev client:** the user opens it (`./gradlew :fabric:runClient`); Claude never launches it.
  - Never compile, run Gradle in the MCA repo, or copy jars into `fabric/dev-mods` while it is open: use
    `safe_gradle.sh`.
  - With the client open, edited resources (like `.jem` files) can be copied straight into
    `fabric/build/resources/main/...` and reloaded with F3+T.
- **Parallel chats:** about 3 Claude chats share the checkout. Commit only what this chat changed; leave other
  sessions' files alone. Commit only when the user asks.
- **Commit messages** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Expansion code:**
  - Everything new lives in `net.conczin.mca.expansion.*`.
  - Changes to MCA's core are short hooks marked `// expansion: <theme>`.
  - Every feature sits behind a `Config` flag that defaults to false.
- **Other mods:** integrated through data files (`data/mca/opportunities`, `daily_toggles`, block tags), never as
  compile dependencies. Separate mods live in separate repos.
- **Tests:** GameTests run on NeoForge with `./gradlew :neoforge:runGameTestServer -PmcaGameTest=<Class>`, after
  registering the class in `neoforge/.../gametest/McaGameTestRegistration.java`.
  - Test villagers need `setVillagerXp(1)`.
  - A profession change rebuilds the brain.
- **Resources:** run `./gradlew :fabric:processResources` so they reach the dev client.
- **Sibling mods:** rebuild with `./gradlew :fabric:build` in their repo, then copy
  `fabric/build/libs/<mod>-fabric-*.jar` into MCA's `fabric/dev-mods` (client closed).

## 6. Check the setup

```sh
cd "$OTHER/minecraft-comes-alive"
scripts/claude-tools/safe_gradle.sh :fabric:compileJava :neoforge:compileJava
scripts/claude-tools/safe_gradle.sh :neoforge:runGameTestServer -PmcaGameTest=WardrobeGameTests
cd "$OTHER/village-furnishings" && ./gradlew :fabric:build
```

Then ask the user to start the client (`./gradlew :fabric:runClient` in `minecraft-comes-alive`) and look at a
village, a wardrobe and a talking villager.

## 7. Where the work stands (2026-10-24)

- **Done, waiting for the user's look in game:**
  - Wardrobes: one door, joining in groups of up to 3, 3 outfits per door.
  - Dressers: 2 wide, 1 tall, 8 drawers.
  - Doors and drawers open with an animation; a click on the open door or drawer closes it.
  - Folded 3D outfits, the "MCA - Roupas" creative tab, and the "Deseja vestir essa roupa?" screen.
  - Villagers own 6 rolled outfits.
  - The square talking mouth, alternating wide and tall.
- **Next, per the user:**
  - Clothing styles: casual, pijama, banho (bath), trabalho (work).
  - Ownership of wardrobes and dressers.
  - A tailor (alfaiate) profession: clothes are limited, so outfits are real items.
- **To check:** how outfits look on a Tabletop table and in item frames (their rotation was set without seeing it).
- **Plans and design notes:** `docs/planning/`; the memory files hold the decisions and the user's feedback.
