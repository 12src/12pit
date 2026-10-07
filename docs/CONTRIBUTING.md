# Contributing to 12pit

For translations, see the [translation guide](https://github.com/12src/12pit/blob/main/docs/TRANSLATING.md).

## Getting Started

Contributions to bug fixes, documentation, tests, maintenance, and new features are welcome.

Find an open issue labeled as https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/help%20wanted. Contributors new to the project can start with issues labeled as https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/good%20first%20issue. Look at assignees, comments, and referenced pull requests first.

If the contribution is a larger feature or a change that needs design decisions, create an issue before implementing it. The [issue guide](https://github.com/12src/12pit/blob/main/docs/ISSUES.md) outlines everything you need to include. Ask for clarification in the issue or `#developer` channel on [Discord](https://discord.gg/e9PRKMUenc) if the scope of the change is unclear.

## Claiming an Issue

Leave a comment when you start working on an issue. If your contribution may overlap with another contributor's work, submit a draft pull request to let others know you are working on it.

## Working on Your Change

Each commit should represent one change only. Messages that follow the [conventional commit format](https://www.conventionalcommits.org/) are preferred: `fix: correct profile loading`, `docs: clarify build steps`, etc. A pull request should generally have one goal. A couple of changes are okay if they are easy to review together.

The [architecture guide](https://github.com/12src/12pit/blob/main/docs/ARCHITECTURE.md) outlines package roles and dependencies. The [implementation guide](https://github.com/12src/12pit/blob/main/docs/IMPLEMENTATION.md) outlines lifecycle, configuration, commands, HUDs, and other project-related classes. The [debugging guide](https://github.com/12src/12pit/blob/main/docs/DEBUGGING.md) outlines the development client and checks.

Read through the relevant code and use existing helpers when appropriate. Keep all new behavior within its feature. Extract any shared code when really necessary. Do not add any unrelated code cleanup in the pull request.

Write short sentences in all prose in the project. Use common words and project terminology where it helps to explain things clearly, naturally, and concisely.

## Coding Standards

Implement what the feature needs now. Add interfaces or wrappers when they serve a concrete need, such as a second implementation or testing. Let Spotless add copyright notices.

Extract a method, constant or variable if that improves the readability of the code or is used in several places. If it is used in just one place and is clearly written inline then it should remain there. Do not keep two methods that do the same job or a method that simply calls another one.

Validate user input, file and network data, and potentially missing Minecraft values where they enter the project. Trust internal callers instead of repeating those checks. Treat broken internal invariants as bugs and handle them where they occur. Avoid fallbacks that hide them. `stop()` must tolerate resources that were never created because initialization may have failed.

Copy a collection only when another consumer may modify it or when passing it to another thread. Briefly document when a method takes ownership of a collection.

Catch an exception only if you can do something with it, handle it or provide more information to the caller. A `catch` block that either rethrows an exception or ignores it should be removed.

Avoid obvious waste, like parsing or scanning the whole world on every frame. Do not add any caches or specific cases for unmeasured gain. In case of small gain and complicated code use a simpler approach.

Remove any unused code, including methods, fields, parameters, imports and classes. Prior to removing a public member search the entire repository for it, including tests, documentation and Mixin configuration. Do that for your own changeset code; other unrelated cleanup should go in a separate pull request.

Use plain forms provided by the language. Rely on autoboxing instead of performing boxing operations yourself, and prefer imports over fully qualified names.

## Comments

Write short comments for non-obvious reasons and hidden contracts, such as unexpected return values from an external API. Update comments with the code.

## Submitting a Pull Request

Format code with ./gradlew spotlessApply, run the checks relevant to your change, and do a build once before submitting the pull request. Mention what you ran and any known limitations. Submit focused tests if the behavior has changed.

Update the architecture guide and relevant tests if package role or dependencies change. Update the implementation guide if some basic interface, calling convention, or registration step changes.

Describe what changed and why, and report any failed checks. Mark the draft pull request ready for review when the work is complete.
