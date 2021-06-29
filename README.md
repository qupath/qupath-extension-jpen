# QuPath JPen extension

Welcome to the JPen extension for [QuPath](http://qupath.github.io)!

This adds graphics tablet support using [JPen](https://github.com/nicarran/jpen).

The extension is intended for the (at the time of writing) not-yet-released 
QuPath v0.3.
It is not compatible with earlier QuPath versions - for which it also isn't needed.


## Building

You can build the extension with

```bash
gradlew clean shadowJar
```

The output will be under `build/libs`.

This should include the extension, *JPen* and its associated native libraries in a single jar file that can be dragged on top of QuPath for installation in the extensions directory.
