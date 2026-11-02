# Contributing to 12pit

For translation of the mod or documentation, see [the translation guide](TRANSLATING.md).

## Getting started

Contributions of any kind are greatly appreciated! Bug fixes, documentation, tests, maintenance, and new features are all welcome.

To get ideas of what to do next, take a look at https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/help%20wanted. For those who are just starting to contribute to the mod, https://github.com/12src/12pit/labels/confirmed and https://github.com/12src/12pit/labels/good%20first%20issue labels are better choices.

Before starting to work on a contribution, check whether the issue has any assignees, comments, and associated pull requests; chances are someone else already started on it. If the contribution requires designing a new feature or involves changes that could affect other parts of the mod in significant ways, open an issue first to discuss them. [Issue guide](ISSUES.md) tells you what information to put there.

## Claiming an issue

Comment on the issue you decided to work on. If your changes may overlap with someone else's work, make a draft pull request early on so other people know about it.

## Making your changes

Keep each commit focused on a single change. We follow [conventional commits](https://www.conventionalcommits.org/) style, like `fix: correct profile loading` or `docs: clarify build steps`. Try to keep a pull request focused on a particular goal; but, if a few related changes are easily reviewed together, they can be in the same pull request.

See [architecture guide](ARCHITECTURE.md) on the structure of dependencies and the packages, and [debugging guide](DEBUGGING.md) on how to run the development client and tests.

Look around in the code before making any changes and reuse the helpers where appropriate. Put a new behavior into the feature owning it; extract common functionality only if needed and don't bundle unrelated cleanup into your pull request.

[BootstrapLayoutTest](../src/test/java/pit12/BootstrapLayoutTest.java) creates instances of configuration classes and verifies their categories and registration order. Configuration classes not having injected dependencies have public no-argument constructors; if you introduce a configuration class requiring additional parameters, adjust the test to pass them.

User-visible strings, logs, and documentation should be clear and natural.

## Coding standards

Provide just enough interfaces or wrappers for the immediate need, for example, for a different implementation or testing. Extract a method, constant, or variable if doing so would make the code more readable or it would be reused somewhere; otherwise, put one-off expressions inline. Don't maintain duplicates of methods or wrappers that just forward a call.

Validate user input, file and network data, and any nullable Minecraft values at the place where they enter the mod. Inside the codebase, rely on the caller passing valid arguments and not repeat validations at each layer, and treat internal broken assumptions as bugs and let them fail directly. As `stop()` can run after only partially successful start, it should check that resources exist before releasing them.

Copy collections only when passing them to a consumer that could modify them or to another thread.

Access the live state on the client thread, and run file operations, HTTP requests, and heavy computations on workers owned by the component. Before using the results from workers, check that the component is alive and that the session, world, or the request generation number are still up-to-date. Update the state before notifying the listeners so that their callback sees the updated state.

When loading fails, don't touch existing files; when saving fails, keep the state dirty and validate the save revision before clearing the dirty flag. If you introduce a new version of a file format, keep saved ids stable and support older formats. Catch an exception only when you can do something useful with it or add some additional context; never put re-throws or noops in the catch block.

Avoid obvious waste, like reading or scanning the whole world on each tick. Before introducing any caching or special cases, measure their impact. If a complicated optimization gives too little benefit, stick to the simpler way.

Remove any unused code, like unused methods, fields, parameters, imports, and classes. Before removing a public class member, check the entire repo for its usage, including the tests, documentation, and Mixin configurations. Use the usual features of the Java language, such as autoboxing and normal imports, instead of boxing explicitly and using fully qualified class names.

## Comments

Imagine yourself reading the code for the first time. If the code makes its purpose obvious at a glance, there is no need for the comment. Otherwise, put a short comment where the reason behind some design decision, edge case, or quirks of some external API are not obvious from the code itself. Don't forget to update comments if you change the code later.

## Submitting a pull request

Use a short title in English, suitable for changelog entry, like `Remove gaps between timer groups`. If your pull request contains several related changes, describe their common goal in the title and the details in the description.

Before marking your pull request as ready for review, format the code with `./gradlew spotlessApply` (which also adds license headers) and `npm run format --prefix web-ui`, and run `./gradlew check` and build the mod once. Make sure to add focused tests when changing the behavior. In the pull request description, describe your changes and their purpose and the checks you run.
