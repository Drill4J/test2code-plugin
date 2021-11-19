#CLI

Command line interface for testing class parsing: classes count and method count.
In the future - difference between builds.

Usage example:

```
./runCli cli --help
```

And here's what run looks like:

```
Usage: class-parse [OPTIONS]

Options:
--classpath TEXT      Classpath (comma separated)
--build-version TEXT  Build version of your app
--packages TEXT       Project packages (comma separated)
--output TEXT         Output file, if not specified stdout is used
-h, --help            Show this message and exit
```

Example of output with specified parameters:

```
./runCli cli --classpath c:\path\to\classes\folder\or\jar --packages com/epam
classCount: 2098
methodCount: 22700
```