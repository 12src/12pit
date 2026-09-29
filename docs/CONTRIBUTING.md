# Contributing to 12pit

## Start contributing

Bug fixes, documentation, tests, maintenance, and new features are all welcome. In the pull request description, explain what changed and why.

If you need a place to start, look for an open issue with the https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/help%20wanted labels. New contributors can start with https://github.com/12src/12pit/labels/good%20first%20issue and https://github.com/12src/12pit/labels/confirmed. Check the assignees, comments, and linked pull requests before you begin.

For a larger feature or a change that needs a design decision, open an issue before implementing it. The [issue guide](ISSUES.md) explains what to include. If the scope is unclear, ask in the issue or in the `#developer` channel on [Discord](https://discord.gg/e9PRKMUenc).

## Let people know

Leave a comment when you start work on an issue. Open a draft pull request early if your work may overlap with someone else's, so others know it is being worked on.

## Make the change

Keep commits focused. Each commit should make one clear change. Prefer [Conventional Commits](https://www.conventionalcommits.org/) messages, such as `fix: correct profile loading` or `docs: clarify build steps`. A pull request should usually have one purpose. A few small changes are fine when they are easy to review together.

Use the [architecture guide](ARCHITECTURE.md) when deciding where code belongs. Read the relevant code first and reuse existing helpers when they fit. Keep new behavior in the feature that owns it. Extract shared code only when there is a real need, and leave unrelated cleanup out of the pull request.

Keep small, one-off code inline when that is clearer. Extract a helper when it improves clarity or will be reused. Avoid extra complexity when it brings little performance benefit. Do not add boilerplate or manual copyright headers just to match nearby files.

Keep text in docs, logs, the UI, commits, and pull requests short and natural.

## Comments

Let the code explain itself when it can. Add a comment when the code cannot show an important reason clearly or when you need to document a hidden contract, such as an unexpected value from an external API. Keep comments short, update them with the code, and do not use them to narrate obvious steps.

## Check and submit

Before opening the pull request, run the checks relevant to your change and try a build once. Report what you ran and any known limits. Add focused tests when behavior changes. If the change affects package ownership or dependency direction, update the architecture guide and relevant tests.

Explain what changed and why. Report failed checks and ask for help if you need it. When the work is ready, mark the draft pull request ready for review.
