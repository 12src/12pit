# Contributing to 12pit

## Start contributing

Fixing bugs, documentation, tests, maintenance, or adding new features are all welcome contributions. Describe the changes and reasons in the pull request description.

Find an open issue labeled as https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/help%20wanted. Contributors new to the project can start with issues labeled as https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/good%20first%20issue. Look at assignees, comments, and referenced pull requests first.

If the contribution is a larger feature or a change that needs design decisions, create an issue before implementing it. The [issue guide](ISSUES.md) outlines everything you need to include. Ask for clarification in the issue or `#developer` channel on [Discord](https://discord.gg/e9PRKMUenc) if the scope of the change is unclear.

## Let people know

Leave a comment when you start working on an issue. If your contribution may overlap with another contributor's work, submit a draft pull request to let others know you are working on it.

## Make the change

Each commit should represent one change only. Messages that follow the [conventional commit format](https://www.conventionalcommits.org/) are preferred: `fix: correct profile loading`, `docs: clarify build steps`, etc. A pull request should generally have one goal. A couple of changes are okay if they are easy to review together.

The [architecture guide](ARCHITECTURE.md) outlines package roles and dependencies. The [implementation guide](IMPLEMENTATION.md) outlines lifecycle, configuration, commands, HUDs, and other project-related classes. The [debugging guide](DEBUGGING.md) outlines the development client and checks.

Read through the relevant code and use existing helpers when appropriate. Keep all new behavior within its feature. Extract any shared code when really necessary. Do not add any unrelated code cleanup in the pull request.

Keep short pieces of one-off code inline if it is clearer this way. Extract a helper when this improves readability or reusability. Do not introduce unnecessary complexity when it does not provide much performance gain. Do not add unnecessary boilerplate or copyright headers to match the files around.

Write short sentences in all prose in the project. Use common words and project terminology where it helps to explain things clearly, naturally, and concisely.

## Comments

Do not put comments when the code can explain the important reasons by itself. Put a comment when there is an important reason that is not clear from the code or when you need to document some hidden contract, for example, some unexpected values coming from some external API. Keep comments short, update them along with the code, and do not use them to document obvious steps.

## Check and submit

Run the checks relevant to your change and do a build once before submitting the pull request. Mention what you ran and any known limitations. Submit focused tests if the behavior has changed.

Update the architecture guide and relevant tests if package role or dependencies change. Update the implementation guide if some basic interface, calling convention, or registration step changes.

Mention what you have changed and why. Mention any failed checks and ask for help if you need. Once you are ready with your work, mark your draft pull request for review.
