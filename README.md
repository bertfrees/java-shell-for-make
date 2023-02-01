# Java shell for Make

Use Java as the shell script for your Makefiles and run them on any platform.

```Makefile
hello :
	System.err.println("Hi, I'm run from within a Makefile");
```

## Usage

1. Include this library in your source code, e.g. as a [Git
   submodule](https://git-scm.com/book/en/v2/Git-Tools-Submodules), or
   by downloading and extracting the tarball of the latest release.

2. Include the following code near the top of your `Makefile`:

   ```Makefile
   include enable-java-shell.mk
   ```

3. If you are on Windows, get the
   [`make.exe`](https://github.com/bertfrees/java-shell-for-make/releases/download/make-4.3-p1/make.exe)
   executable needed to execute your `Makefile`.

## License

Copyright © 2022-2023 Bert Frees

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
