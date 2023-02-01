.PHONY : all
all : eval-java eval-java.exe eval_java.class lib/util.class lib/util$$OS.class

.PHONY : dist
dist : tarball

include bootstrap/enable-java-shell.mk

# Java >= 8 is required to compile util
eval_java.class lib/util.class : %.class : %.java
	javac("-source", "1.8", "-target", "1.8", "-bootclasspath", "rt8.jar", "-extdirs", "", "$<");

eval-java : eval-java.c
	exec("cc", "$<", "-o", "$@");

eval-java.exe : eval-java.c
	exec("i686-w64-mingw32-gcc", "$<", "-o", "$@");

TARBALL := $(notdir $(CURDIR)).tar.gz

.PHONY : tarball
tarball : $(TARBALL)
$(TARBALL) : enable-java-shell.mk eval-java eval-java.exe eval_java.class lib/util.class lib/util$$OS.class .gitignore
	List<String> cmd = new ArrayList<>();          \
	cmd.add("tar");                                \
	cmd.add("-czvf");                              \
	cmd.add("$@");                                 \
	cmd.addAll(Arrays.asList("$^".split("\\s")));  \
	exec(cmd);

.PHONY : update-bootstrap
update-bootstrap : enable-java-shell.mk eval-java eval-java.exe eval_java.class lib/util.class lib/util$$OS.class .gitignore
	cp(".gitignore", "bootstrap/");             \
	cp("enable-java-shell.mk", "bootstrap/");   \
	cp("eval-java", "bootstrap/");              \
	cp("eval-java.exe", "bootstrap/");          \
	cp("eval_java.class", "bootstrap/");        \
	cp("lib/util.class", "bootstrap/lib/");     \
	cp("lib/util$$OS.class", "bootstrap/lib/");

.PHONY : clean
clean :
	rm("eval_java.class");      \
	rm("lib/util.class");       \
	rm("lib/util$$OS.class");   \
	rm("eval-java");            \
	rm("eval-java.exe");        \
	rm("bootstrap/recipes");    \
	rm("$(TARBALL)");
