# QuPath JPen extension

Welcome to the JPen extension for [QuPath](http://qupath.github.io)!

This adds graphics tablet support using [JPen](https://github.com/nicarran/jpen).

The extension is intended for the (at the time of writing) not-yet-released 
QuPath v0.3.
It is not compatible with earlier QuPath versions - for which it also isn't needed.


## Installing

To install the JPen extension, download the latest `qupath-extension-jpen-[version]-all.jar` file from [releases](https://github.com/qupath/qupath-extension-jpen/releases) and drag it onto the main QuPath window.

If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

You might then need to restart QuPath (but not your computer).


## Building

You can build the extension with

```bash
gradlew clean shadowJar
```

The output will be under `build/libs`.

This should include the extension, *JPen* and its associated native libraries in a single jar file that can be dragged on top of QuPath for installation in the extensions directory.

> Note that you need `shadowJar` rather than `build` to include the JPen library itself.