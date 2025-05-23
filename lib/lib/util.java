package lib;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jline.ConsoleOperations;
import jline.ConsoleReader;

import org.xml.sax.InputSource;

public class util {
	private util() {}

	public static void println(Object o) {
		System.out.println(o);
	}

	public static void exit(int exitValue) throws SystemExit {
		throw new SystemExit(exitValue);
	}

	public static void exit(boolean succeeded) throws SystemExit {
		exit(succeeded ? 0 : 1);
	}

	public static void exitOnError(int exitValue) throws SystemExit {
		if (exitValue != 0)
			exit(exitValue);
	}

	public static void exitOnError(boolean succeeded) throws SystemExit {
		if (!succeeded)
			exit(succeeded);
	}

	public enum OS {
		WINDOWS,
		MACOSX,
		REDHAT,
		DEBIAN,
		LINUX
	}

	public static OS getOS() {
		String name = System.getProperty("os.name").toLowerCase();
		if (name.startsWith("windows"))
			return OS.WINDOWS;
		else if (name.startsWith("mac os x"))
			return OS.MACOSX;
		else if (new File("/etc/redhat-release").isFile())
			return OS.REDHAT;
		else if (name.startsWith("linux"))
			return OS.LINUX;
		else
			throw new RuntimeException("Unsupported OS: " + name);
	}

	public static int getJavaVersion() {
		String v = System.getProperty("java.version");
		if (v.startsWith("1."))
			v = v.substring(2, 3);
		else
			v = v.replaceAll("\\..*", "");
		return Integer.parseInt(v);
	}

	public static void mkdirs(String directory) {
		mkdirs(new File(directory));
	}

	public static void mkdirs(File directory) {
		directory.mkdirs();
	}

	public static void rm(String fileOrDirectory) {
		rm(new File(fileOrDirectory));
	}

	public static void rm(File fileOrDirectory) {
		if (Files.isSymbolicLink(fileOrDirectory.toPath())) {
			if (!fileOrDirectory.delete())
				throw new RuntimeException("could not delete file: " + fileOrDirectory);
		} else if (fileOrDirectory.exists()) {
			if (fileOrDirectory.isDirectory())
				for (File f : fileOrDirectory.listFiles())
					rm(f);
			if (!fileOrDirectory.delete())
				throw new RuntimeException("could not delete file: " + fileOrDirectory);
		}
	}

	public static void mv(String src, String dest) throws IOException, SystemExit {
		File srcFile = new File(src);
		File destFile = new File(dest);
		if (dest.endsWith("/"))
			if (Files.isSymbolicLink(destFile.toPath())) {
				System.err.println("file is not a directory: " + destFile);
				exit(1);
			} else if (!destFile.isDirectory()) {
				if (destFile.exists())
					System.err.println("file is not a directory: " + destFile);
				else
					System.err.println("directory does not exist: " + destFile);
				exit(1);
			}
		mv(srcFile, destFile);
	}

	public static void mv(File src, File dest) throws IOException, SystemExit {
		if (!(Files.isSymbolicLink(src.toPath()) || src.exists())) {
			System.err.println("file does not exist: " + src);
			exit(1);
		}
		if (Files.isSymbolicLink(dest.toPath())) {
			System.err.println("file exists: " + dest);
			exit(1);
		} else if (dest.isDirectory())
			dest = new File(dest, src.getName());
		if (Files.isSymbolicLink(dest.toPath()) || dest.exists()) {
			System.err.println("file exists: " + dest);
			exit(1);
		}
		if (!dest.getCanonicalFile().getParentFile().isDirectory()) {
			System.err.println("directory does not exist: " + dest.getCanonicalFile().getParentFile());
			exit(1);
		}
		if (Files.isSymbolicLink(src.toPath())) {
			if (!src.renameTo(dest))
				throw new RuntimeException("could not rename file: " + src + " -> " + dest);
		} else if (src.isDirectory()) {
			dest.mkdirs();
			for (File f : src.listFiles())
				mv(f, dest);
			if (!src.delete())
				throw new RuntimeException("could not delete file: " + src); }
		else if (!src.renameTo(dest))
			throw new RuntimeException("could not rename file: " + src + " -> " + dest);
	}

	public static void cp(String src, String dest) throws FileNotFoundException, IOException, SystemExit {
		copy(src, dest);
	}

	public static void cp(File src, File dest) throws FileNotFoundException, IOException, SystemExit {
		copy(src, dest);
	}

	public static void cp(List<File> files, File dest) throws FileNotFoundException, IOException, SystemExit {
		copy(files, dest);
	}

	public static void cp(URL url, File file) throws FileNotFoundException, IOException, SystemExit {
		copy(url, file);
	}

	public static void cp(InputStream src, OutputStream dest) throws IOException {
		copy(src, dest);
	}

	public static void copy(String src, String dest) throws FileNotFoundException, IOException, SystemExit {
		File srcFile = new File(src);
		File destFile = new File(dest);
		if (dest.endsWith("/"))
			if (Files.isSymbolicLink(destFile.toPath())) {
				System.err.println("file is not a directory: " + destFile);
				exit(1);
			} else if (!destFile.isDirectory()) {
				if (destFile.exists())
					System.err.println("file is not a directory: " + destFile);
				else
					System.err.println("directory does not exist: " + destFile);
				exit(1);
			}
		copy(srcFile, destFile);
	}

	public static void copy(File src, File dest) throws FileNotFoundException, IOException, SystemExit {
		if (!(Files.isSymbolicLink(src.toPath()) || src.exists())) {
			System.err.println("file does not exist: " + src);
			exit(1);
		} else if (src.isDirectory()) {
			System.err.println("file is a directory: " + src);
			exit(1);
		}
		if (Files.isSymbolicLink(dest.toPath())) {
			System.err.println("file exists: " + dest);
			exit(1);
		} else if (dest.isDirectory())
			dest = new File(dest, src.getName());
		if (Files.isSymbolicLink(dest.toPath()) || dest.exists()) {
			System.err.println("file exists: " + dest);
			exit(1);
		}
		if (!dest.getCanonicalFile().getParentFile().isDirectory()) {
			System.err.println("directory does not exist: " + dest.getCanonicalFile().getParentFile());
			exit(1);
		}
		try (InputStream is = new FileInputStream(src);
		     OutputStream os = new FileOutputStream(dest)) {
			copy(is, os);
		}
	}

	public static void copy(List<File> files, File dest) throws FileNotFoundException, IOException, SystemExit {
		for (File f : files)
			copy(f, dest);
	}

	public static void copy(URL url, File file) throws FileNotFoundException, IOException, SystemExit {
		if (Files.isSymbolicLink(file.toPath()) || file.exists()) {
			System.err.println("file exists: " + file);
			exit(1);
		}
		if (!file.getCanonicalFile().getParentFile().isDirectory()) {
			System.err.println("directory does not exist: " + file.getCanonicalFile().getParentFile());
			exit(1);
		}
		try (InputStream is = new BufferedInputStream(url.openStream());
		     OutputStream os = new FileOutputStream(file)) {
			copy(is, os);
		}
	}

	public static void copy(InputStream src, OutputStream dest) throws IOException {
		byte data[] = new byte[1024];
		int read;
		while ((read = src.read(data)) != -1)
			dest.write(data, 0, read);
	}

	public static void touch(String file) throws IOException, SystemExit {
		touch(new File(file));
	}

	public static void touch(File file) throws IOException, SystemExit {
		if (!file.exists()) {
			if (!file.getCanonicalFile().getParentFile().isDirectory()) {
				System.err.println("directory does not exist: " + file.getCanonicalFile().getParentFile());
				exit(1);
			}
			file.createNewFile();
		} else
			file.setLastModified(System.currentTimeMillis());
	}

	public static String slurp(String file) throws IOException {
		return slurp(new File(file));
	}

	public static String slurp(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()));
	}

	public static void write(File file, String string) throws IOException, SystemExit {
		if (Files.isSymbolicLink(file.toPath())) {
			System.err.println("file is a symbolic link: " + file);
			exit(1);
		} else if (file.isDirectory()) {
			System.err.println("file is a directory: " + file);
			exit(1);
		}
		if (!file.getCanonicalFile().getParentFile().isDirectory()) {
			System.err.println("directory does not exist: " + file.getCanonicalFile().getParentFile());
			exit(1);
		}
		try (OutputStream os = new FileOutputStream(file, true)) {
			os.write(string.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("coding error", e);
		}
	}

	public static void unzip(File zipFile, File directory) throws IOException, SystemExit {
		if (Files.isSymbolicLink(directory.toPath())) {
			System.err.println("file is not a directory: " + directory);
			exit(1);
		} else if (!directory.isDirectory()) {
			if (directory.exists())
					System.err.println("file is not a directory: " + directory);
				else
					System.err.println("directory does not exist: " + directory);
			exit(1);
		}
		try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				File destFile = new File(directory, entry.getName());
				if (!entry.isDirectory()) {
					if (Files.isSymbolicLink(destFile.toPath()) || destFile.exists()) {
						System.err.println("file exists: " + destFile);
						exit(1);
					}
					destFile.getParentFile().mkdirs();
					try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(destFile))) {
						byte[] bytes = new byte[1024];
						int read = 0;
						while ((read = zip.read(bytes)) != -1)
							os.write(bytes, 0, read);
					}
				} else
					destFile.mkdirs();
				zip.closeEntry();
			}
		}
	}

	public static void exec(String... cmd) throws IOException, InterruptedException, SystemExit {
		exec(null, null, cmd);
	}

	public static void exec(List<String> cmd) throws IOException, InterruptedException, SystemExit {
		exec(null, null, cmd.toArray(new String[cmd.size()]));
	}

	public static void exec(File cd, String... cmd) throws IOException, InterruptedException, SystemExit {
		exec(cd, null, cmd);
	}

	public static void exec(File cd, List<String> cmd) throws IOException, InterruptedException, SystemExit {
		exec(cd, null, cmd.toArray(new String[cmd.size()]));
	}

	public static void exec(Map<String,String> env, String... cmd) throws IOException, InterruptedException, SystemExit {
		exec(null, env, cmd);
	}

	public static void exec(File cd, Map<String,String> env, List<String> cmd) throws IOException, InterruptedException, SystemExit {
		exec(cd, env, cmd.toArray(new String[cmd.size()]));
	}

	public static void exec(File cd, Map<String,String> env, String... cmd) throws IOException, InterruptedException, SystemExit {
		ProcessBuilder b = new ProcessBuilder(cmd);
		// this only makes sense when not evaluated through the REPL server
		b.redirectInput(ProcessBuilder.Redirect.INHERIT);
		if (cd != null)
			b = b.directory(cd);
		if (env != null)
			for (String v : env.keySet())
				if (env.get(v) != null)
					b.environment().put(v, env.get(v));
				else
					b.environment().remove(v);
		Process p = b.start();
		// Don't use inheritIO() because that will set the source and destination for the subprocess
		// standard I/O to be the same as those of the current Java process, which is not always the
		// same as System.{in,out,err}.
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CompletionService<Void> completionService =
			new ExecutorCompletionService<Void>(executor);
		completionService.submit(() -> { copy(p.getInputStream(), System.out); return null; });
		completionService.submit(() -> { copy(p.getErrorStream(), System.err); return null; });
		try {
			for (int done = 0; done < 2; done++)
				completionService.take().get();
		} catch (ExecutionException e) {
			Throwable t = e;
			while (t instanceof ExecutionException) t = ((ExecutionException)t).getCause();
			if (t instanceof IOException)
				// can be thrown by copy()
				throw (IOException)t;
			else if (t instanceof RuntimeException)
				throw (RuntimeException)t;
			else
				// should not happen
				throw new RuntimeException(t);
		} finally {
			executor.shutdownNow();
		}
		int rv = p.waitFor();
		System.out.flush();
		System.err.flush();
		exit(rv);
	}

	public static Map<String,String> env(String... variables) {
		Map<String,String> env = new HashMap<>();
		String var = null;
		for (String s : variables) {
			if (var != null) {
				env.put(var, s);
				var = null; }
			else if (s == null)
				throw new RuntimeException();
			else
				var = s; }
		if (var != null)
			throw new RuntimeException();
		return env;
	}

	public static String[] cons(String... rest) {
		return rest;
	}

	public static String[] cons(String a, String[] rest) {
		String[] array = new String[1 + rest.length];
		array[0] = a;
		System.arraycopy(rest, 0, array, 1, rest.length);
		return array;
	}

	public static String[] cons(String a, String b, String[] rest) {
		String[] array = new String[2 + rest.length];
		array[0] = a;
		array[1] = b;
		System.arraycopy(rest, 0, array, 2, rest.length);
		return array;
	}

	public static String[] cons(String a, String b, String c, String[] rest) {
		String[] array = new String[3 + rest.length];
		array[0] = a;
		array[1] = b;
		array[2] = c;
		System.arraycopy(rest, 0, array, 3, rest.length);
		return array;
	}

	public static String[] cons(String a, String b, String c, String d, String[] rest) {
		String[] array = new String[4 + rest.length];
		array[0] = a;
		array[1] = b;
		array[2] = c;
		array[3] = d;
		System.arraycopy(rest, 0, array, 4, rest.length);
		return array;
	}

	public static String[] cons(String a, String b, String c, String d, String e, String[] rest) {
		String[] array = new String[5 + rest.length];
		array[0] = a;
		array[1] = b;
		array[2] = c;
		array[3] = d;
		array[4] = e;
		System.arraycopy(rest, 0, array, 5, rest.length);
		return array;
	}

	public static int captureOutput(Consumer<String> collect, String... cmd) throws IOException, InterruptedException {
		return captureOutput(collect, null, null, cmd);
	}

	public static int captureOutput(Consumer<String> collect, List<String> cmd) throws IOException, InterruptedException {
		return captureOutput(collect, null, null, cmd.toArray(new String[cmd.size()]));
	}

	public static int captureOutput(Consumer<String> collect, File cd, String... cmd) throws IOException, InterruptedException {
		return captureOutput(collect, cd, null, cmd);
	}

	public static int captureOutput(Consumer<String> collect, File cd, List<String> cmd) throws IOException, InterruptedException {
		return captureOutput(collect, cd, null, cmd.toArray(new String[cmd.size()]));
	}

	public static int captureOutput(Consumer<String> collect, Map<String,String> env, String... cmd) throws IOException, InterruptedException {
		return captureOutput(collect, null, env, cmd);
	}

	public static int captureOutput(Consumer<String> collect, File cd, Map<String,String> env, List<String> cmd) throws IOException, InterruptedException {
		return captureOutput(collect, cd, env, cmd.toArray(new String[cmd.size()]));
	}

	public static int captureOutput(Consumer<String> collect, File cd, Map<String,String> env, String... cmd) throws IOException, InterruptedException {
		ProcessBuilder b = new ProcessBuilder(cmd);
		// this only makes sense when not evaluated through the REPL server
		b.redirectInput(ProcessBuilder.Redirect.INHERIT);
		if (cd != null)
			b = b.directory(cd);
		if (env != null)
			for (String v : env.keySet())
				b.environment().put(v, env.get(v));
		Process p = b.start();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CompletionService<Void> completionService =
			new ExecutorCompletionService<Void>(executor);
		completionService.submit(() -> { copy(p.getErrorStream(), System.err); return null; });
		completionService.submit(() -> {
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null)
					if (collect != null)
						collect.accept(line);
				return null; });
		try {
			for (int done = 0; done < 2; done++)
				completionService.take().get();
		} catch (ExecutionException e) {
			Throwable t = e;
			while (t instanceof ExecutionException) t = ((ExecutionException)t).getCause();
			if (t instanceof IOException)
				throw (IOException)t;
			else if (t instanceof RuntimeException)
				throw (RuntimeException)t;
			else
				// should not happen
				throw new RuntimeException(t);
		} finally {
			executor.shutdownNow();
		}
		int rv = p.waitFor();
		System.err.flush();
		return rv;
	}

	public static List<String> runInShell(String... cmd) throws IOException {
		List<String> newCmd = new ArrayList<>();
		for (String s : cmd)
			newCmd.add(s);
		return runInShell(newCmd);
	}

	public static List<String> runInShell(List<String> cmd) throws IOException {
		if (getOS() == OS.WINDOWS) {
			cmd = new ArrayList<>(cmd);
			for (int i = 0; i < cmd.size(); i++)
				if (Pattern.compile("[ \"]").matcher(cmd.get(i)).find())
					cmd.set(i, quote(cmd.get(i)));
			List<String> newCmd = new ArrayList<>();
			newCmd.add("cmd.exe");
			newCmd.add("/s");
			newCmd.add("/c");
			newCmd.add("\"" + String.join(" ", cmd) + "\"");
			return newCmd;
		} else {
			return cmd;
		}
	}

	public static void javac(String... cmd) throws IOException, InterruptedException, SystemExit {
		String javac = getOS() == OS.WINDOWS ? "javac.exe" : "javac";
		String JAVA_HOME = System.getenv("JAVA_HOME");
		if (JAVA_HOME != null) {
			File f = new File(new File(new File(JAVA_HOME), "bin"), javac);
			if (f.exists())
				javac = f.getAbsolutePath();
		}
		String[] javacCmd = new String[1 + cmd.length];
		javacCmd[0] = javac;
		System.arraycopy(cmd, 0, javacCmd, 1, cmd.length);
		exec(javacCmd);
	}

	private static String quote(String s) {
		return "\"" + s.replace("\"", "\\\"") + "\"";
	}

	public static String xpath(File file, String expression) throws FileNotFoundException, XPathExpressionException {
		return XPathFactory.newInstance().newXPath().compile(expression)
			.evaluate(new InputSource(new FileInputStream(file)));
	}

	public static List<File> glob(String pattern) throws IOException {
		return glob(Paths.get("."), pattern);
	}

	public static List<File> glob(File cd, String pattern) throws IOException {
		return glob(cd.toPath(), pattern);
	}

	public static List<File> glob(Path cd, String pattern) throws IOException {
		cd = cd.toFile().getCanonicalFile().toPath();
		Path base = cd;
		String prefix = "";
		List<File> list = new ArrayList<>();
		String separator = getOS() == OS.WINDOWS
			? (File.separator + File.separator)
			: File.separator;
		if (getOS() == OS.WINDOWS)
			pattern = pattern.replace("/", separator);
		if (pattern.startsWith("/")) { // *nix only
			base = Paths.get("/");
			prefix = "/";
		} else {
			while (pattern.startsWith(".." + separator)) {
				base = base.getParent();
				pattern = pattern.substring((".." + separator).length());
				prefix += (".." + separator);
			}
			pattern = base.toString().replace(File.separator, separator) + separator + pattern;
		}
		int cut = base.toFile().getPath().equals("/") ? 1 : base.toFile().getPath().length() + 1;
		final String fPrefix = prefix;
		PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
		Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (pathMatcher.matches(path))
					list.add(new File(fPrefix + path.toFile().getPath().substring(cut)));
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
		return list;
	}

	/**
	 * @param search a regular expression
	 */
	public static Stream<String> egrep(String search, String file) throws IOException {
		return egrep(search, new File(file));
	}

	public static Stream<String> egrep(String search, File file) throws IOException {
		return egrep(Pattern.compile(search), new FileReader(file));
	}

	public static Stream<String> egrep(String search, InputStream input) throws IOException {
		return egrep(Pattern.compile(search), new InputStreamReader(input));
	}

	public static Stream<String> egrep(Pattern search, Reader input) throws IOException {
		return new BufferedReader(input).lines().filter(line -> search.matcher(line).find());
	}

	/**
	 * @return the index of the option that the user chooses, or {@code -1}.
	 * @throw IllegalArgumentException if {@code options} is empty
	 * @throw IOException              if the prompt was cancelled by the user
	 */
	public static int prompt(String... options) throws IllegalArgumentException, IOException {
		ConsoleReader console = new ConsoleReader();
		if (options.length == 0)
			throw new IllegalArgumentException();
		int choice = 0;
		choose: for (;;) {
			for (int i = 0; i < options.length; i++) {
				if (i == choice)
					System.err.println("\033[7m" + options[i] + "\033[27m");
				else
					System.err.println(options[i]);
			}
			switch (console.readVirtualKey()) {
			case ConsoleOperations.CTRL_P: // UP
			case ConsoleOperations.CTRL_B: // LEFT
				if (choice > 0)
					choice--;
				break;
			case ConsoleOperations.CTRL_F: // RIGHT
			case ConsoleOperations.CTRL_N: // DOWN
				if (choice < options.length - 1)
					choice++;
				break;
			case 9:                        // TAB
				if (choice == options.length - 1)
					choice = 0;
				else
					choice++;
				break;
			case 10:                       // ENTER
			case ConsoleOperations.NEWLINE:
				break choose;
			case ConsoleOperations.EXIT:
				throw new IOException("prompt cancelled by user");
			}
			System.err.print("\033[F");
			for (int i = 0; i < options.length; i++) System.err.print("\033[A");
			System.err.println();
		}
		return choice;
	}

	public static int prompt(List<String> options) throws IllegalArgumentException, IOException {
		return prompt(options.toArray(new String[options.size()]));
	}

	public static final class SystemExit extends RuntimeException {
		private final int exitValue;
		private SystemExit(int exitValue) {
			super();
			this.exitValue = exitValue;
		}
	}
}
