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

Write short sentences in all prose in the project. Use common words and project terminology where it helps to explain things clearly, naturally, and concisely.

## Keep the code plain

Implement the code the feature requires at the moment, in the most concise way possible. For every added functionality ask yourself who needs it. If the answer is nobody or only future feature, do not add it but rather add it in the relevant changeset. No extra wording should be included simply to make it consistent with other existing documents. Do not manually include any copyright notices; let Spotless take care of those for you. Implement interfaces or wrappers only if they have a reason, such as second implementation or testing.

Extract a method, constant or variable if that improves the readability of the code or is used in several places. If it is used in just one place and is clearly written inline then it should remain there. Do not keep two methods that do the same job or a method that simply calls another one.

Check a value where it enters the project, and only there. To decide where to check a value ask yourself who is responsible for giving you a good one. Value from a user, file, network or a Minecraft call that might return nothing is questionable, thus checking should be done where the value is obtained. Code called by a caller in this repository already knows what it passes, thus the callee does not need to check it again. If an internal invariant is violated, it is a bug. Handle such situation in place and do not hide it behind a fallback logic. An exception to this rule is `stop()`. The method may be called after failed initialization, thus it has to work with uncreated resources.

Make a copy of the collection when somebody else might modify it or it is passed to a different thread. If the collection is owned by just one class, then making a copy or returning an unmodifiable view protects nothing. When a method takes control of a collection make sure that it is specified in a comment in few words.

Catch an exception only if you can do something with it, handle it or provide more information to the caller. A `catch` block that either rethrows an exception or ignores it should be removed.

Avoid obvious waste, like parsing or scanning the whole world on every frame. Do not add any caches or specific cases for unmeasured gain. In case of small gain and complicated code use a simpler approach.

Remove any unused code, including methods, fields, parameters, imports and classes. Prior to removing a public member search the entire repository for it, including tests, documentation and Mixin configuration. Do that for your own changeset code; other unrelated cleanup should go in a separate pull request.

Use plain forms provided by the language. Rely on autoboxing instead of performing boxing operations yourself, and prefer imports over fully qualified names.

## Comments

Do not put comments when the code can explain the important reasons by itself. Put a comment when there is an important reason that is not clear from the code or when you need to document some hidden contract, for example, some unexpected values coming from some external API. Keep comments short, update them along with the code, and do not use them to document obvious steps.

## Check and submit

Format code with ./gradlew spotlessApply, run the checks relevant to your change, and do a build once before submitting the pull request. Mention what you ran and any known limitations. Submit focused tests if the behavior has changed.

Update the architecture guide and relevant tests if package role or dependencies change. Update the implementation guide if some basic interface, calling convention, or registration step changes.

Mention what you have changed and why. Mention any failed checks and ask for help if you need. Once you are ready with your work, mark your draft pull request for review.
