.PHONY : all
all : eval-java eval-java.exe eval_java.class lib/lib/util.class lib/lib/util$$OS.class lib/lib/util$$1.class

help :
	@err.println(                                                                                    \
	    "make:"                                                                             + "\n" + \
	    "    Compile all sources"                                                           + "\n" + \
	    "make dist:"                                                                        + "\n" + \
	    "make tarball:"                                                                     + "\n" + \
	    "    Package everything in a .tar.gz file"                                          + "\n" + \
	    "make update-bootstrap:"                                                            + "\n" + \
	    "    Update the bootstrap/ directory to the current version of java-shell-for-make" + "\n" + \
	    "make clean:"                                                                       + "\n" + \
	    "    Delete all generated files"                                                    + "\n" + \
	    "make help:"                                                                        + "\n" + \
	    "    Print list of commands"                                                                 \
	);

.PHONY : dist
dist : tarball

include bootstrap/enable-java-shell.mk

LIBS = $(shell println(glob("lib/*.jar").stream().map(File::getPath).collect(Collectors.joining(" ")));)

# Java >= 8 is required to compile util
eval_java.class lib/lib/util.class : %.class : %.java
	javac("-source", "1.8", "-target", "1.8", "-bootclasspath", "rt8.jar", "-extdirs", "", \
	      "-cp", "$(LIBS)".replaceAll("\\s", File.pathSeparator), "$<");

eval-java : eval-java.c
	exec("cc", "$<", "-o", "$@");

eval-java.exe : eval-java.c
	exec("i686-w64-mingw32-gcc", "$<", "-o", "$@");

TARBALL := $(notdir $(CURDIR)).tar.gz

.PHONY : tarball
tarball : $(TARBALL)
$(TARBALL) : enable-java-shell.mk eval-java eval-java.exe eval_java.class lib/lib/util.class lib/lib/util$$OS.class lib/lib/util$$1.class .gitignore $(LIBS)
	List<String> cmd = new ArrayList<>();          \
	cmd.add("tar");                                \
	cmd.add("-czvf");                              \
	cmd.add("$@");                                 \
	cmd.addAll(Arrays.asList("$^".split("\\s")));  \
	exec(cmd);

.PHONY : update-bootstrap
update-bootstrap : $(TARBALL)
	rm("bootstrap");                              \
	mkdirs("bootstrap");                          \
	exec("tar", "-xzf", "$<", "-C", "bootstrap");

.PHONY : clean
clean :
	rm("eval_java.class");                       \
	for (File f : glob("lib/**/*.class")) rm(f); \
	rm("eval-java");                             \
	rm("eval-java.exe");                         \
	rm("bootstrap/recipes");                     \
	rm("$(TARBALL)");
