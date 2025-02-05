UNAME_P := $(shell uname -m)

CLASSES := eval_java.class eval_java$$MultiplexedOutputStream.class \
           lib/lib/util.class lib/lib/util$$OS.class lib/lib/util$$SystemExit.class lib/lib/util$$1.class

.PHONY : all
all : $(CLASSES) \
      bin/darwin_amd64/eval-java \
      bin/darwin_arm64/eval-java \
      bin/linux_amd64/eval-java \
      bin/linux_arm64/eval-java \
      bin/windows_amd64/eval-java.exe

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

DOCKER_IMAGE_ := java-shell-for-make_debian_

# can not use --platform linux/amd64,linux/arm64 (see https://github.com/docker/buildx/issues/59)
.PHONY : docker-images
docker-images :
	List<String> images = new ArrayList<>(); { \
		exitOnError( \
			captureOutput(line -> { \
				if (images.size() > 0 || !line.startsWith("REPOSITORY")) \
					images.add(line.trim().split(" ")[0]); \
			}, "docker", "images") \
		); \
	} \
	if (!images.contains("$(DOCKER_IMAGE_)amd64") || !images.contains("$(DOCKER_IMAGE_)arm64")) { \
		exitOnError(captureOutput(err::println, "docker", "buildx", "create", \
		                                                  "--use", "--name=mybuilder", \
		                                                  "--driver", "docker-container", \
		                                                  "--driver-opt", "image=moby/buildkit:buildx-stable-1")); \
		try { \
			if (!images.contains("$(DOCKER_IMAGE_)amd64")) { \
				exitOnError(captureOutput(err::println, "docker", "buildx", "build", "--platform", "linux/amd64", \
				                                        "-t", "$(DOCKER_IMAGE_)amd64", \
				                                        "--load", \
				                                        "docker")); \
			} \
			if (!images.contains("$(DOCKER_IMAGE_)arm64")) { \
				exitOnError(captureOutput(err::println, "docker", "buildx", "build", "--platform", "linux/arm64", \
				                                        "-t", "$(DOCKER_IMAGE_)arm64", \
				                                        "--load", \
				                                        "docker")); \
			} \
		} finally { \
			exitOnError(captureOutput(err::println, "docker", "buildx", "rm", "mybuilder")); \
		} \
	}

ifeq ($(UNAME_P),x86_64)

bin/darwin_amd64/eval-java : eval-java.c
	mkdirs("$(dir $@)");          \
	exec("cc", "$<", "-o", "$@");

bin/windows_amd64/eval-java.exe : eval-java.c
	mkdirs("$(dir $@)");                             \
	exec("i686-w64-mingw32-gcc", "$<", "-o", "$@");

else ifneq ($(filter arm%,$(UNAME_P)),)

bin/darwin_amd64/eval-java : eval-java.c
	mkdirs("$(dir $@)");          \
	exec("cc", "$<", "-target", "x86_64-apple-macos10.12", "-o", "$@");

bin/darwin_arm64/eval-java : eval-java.c bin/darwin_amd64/eval-java
	mkdirs("$(dir $@)");          \
	exec("cc", "$<", "-o", "$@.tmp");
	exec("lipo", "-create", "-output", "$@", "$(word 2,$^)", "$@.tmp");
	rm("$@.tmp");

bin/windows_amd64/eval-java.exe : eval-java.c
	exec("$(MAKE)", "docker-images");
	mkdirs("$(dir $@)");                         \
	rm("$@");                                    \
	exec("docker", "run", "-it", "--rm",         \
	     "--platform", "linux/amd64",            \
	     "-v", "$(CURDIR):/host",                \
	     "$(DOCKER_IMAGE_)amd64", "bash", "-c",  \
	     "cd /host &&" +                         \
	     "i686-w64-mingw32-gcc $< -o $@");

endif

bin/linux_amd64/eval-java : eval-java.c
	exec("$(MAKE)", "docker-images");
	mkdirs("$(dir $@)");                         \
	rm("$@");                                    \
	exec("docker", "run", "-it", "--rm",         \
	     "--platform", "linux/amd64",            \
	     "-v", "$(CURDIR):/host",                \
	     "$(DOCKER_IMAGE_)amd64", "bash", "-c",  \
	     "cd /host &&" +                         \
	     "cc $< -o $@");

bin/linux_arm64/eval-java : eval-java.c
	exec("$(MAKE)", "docker-images");
	mkdirs("$(dir $@)");                        \
	rm("$@");                                   \
	exec("docker", "run", "-it", "--rm",        \
	     "--platform", "linux/arm64",           \
	     "-v", "$(CURDIR):/host",               \
	     "$(DOCKER_IMAGE_)arm64", "bash", "-c", \
	     "cd /host &&" +                        \
	     "cc $< -o $@");

TARBALL := $(notdir $(CURDIR)).tar.gz

.PHONY : tarball
tarball : $(TARBALL)
$(TARBALL) : enable-java-shell.mk eval-java.c \
             bin/darwin_amd64/eval-java bin/linux_amd64/eval-java bin/windows_amd64/eval-java.exe bin/darwin_arm64/eval-java bin/linux_arm64/eval-java \
             $(CLASSES) .gitignore $(LIBS)
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
	rm("eval_java.class");                         \
	rm("eval_java$MultiplexedOutputStream.class"); \
	for (File f : glob("lib/**/*.class")) rm(f);   \
	rm("bin/darwin_amd64/eval-java");              \
	rm("bin/linux_amd64/eval-java");               \
	rm("bin/windows_amd64/eval-java.exe");         \
	rm("bin/darwin_arm64/eval-java");              \
	rm("bin/linux_arm64/eval-java");               \
	rm("bootstrap/recipes");                       \
	rm("$(TARBALL)");
