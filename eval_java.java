import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class eval_java {

	public static void main(String[] args) {
		try {
			thisExecutableArg = args[0]; // path of eval-java(.exe) relative to current directory
			baseDir = new File(thisExecutableArg).getParentFile().getParentFile().getParentFile();
			if ("--spawn-repl-server".equals(args[1])) {
				// fire up a new REPL server process, print the port number, and exit
				int port = spawnReplServer();
				System.out.println("" + port);
				System.out.flush();
				System.exit(0);
			} else {
				javaDir = new File(baseDir, "recipes/java");
				classesDir = new File(new File(baseDir, "recipes/classes"), "" + getJavaVersion());
				classPath = new ArrayList<>(); {
					File libDir = new File(baseDir, "lib");
					classPath.add(libDir);
					for (File f : libDir.listFiles())
						if (f.getName().endsWith(".jar"))
							classPath.add(f);
					classPath.add(classesDir);
					String fromEnv = System.getenv("CLASSPATH");
					if (fromEnv != null)
						for (String p : fromEnv.split("\\s+")) {
							File f = new File(p);
							if (f.exists())
								classPath.add(f); }}
				if ("--repl-server".equals(args[1])) {
					// run a server that wraps a REPL
					ServerSocket server; {
						server = null;
						Integer port = null;
						String JAVA_REPL_PORT = System.getenv("JAVA_REPL_PORT");
						if (JAVA_REPL_PORT != null && !"".equals(JAVA_REPL_PORT)) {
							try {
								port= Integer.parseInt(JAVA_REPL_PORT);
								if (port < 0 || port > 65535)
									throw new IllegalArgumentException("Not a valid port number: " + port);
							} catch (NumberFormatException e) {
								throw new IllegalArgumentException("Not a valid port number: " + JAVA_REPL_PORT);
							}
							try {
								server = new ServerSocket(port);
							} catch (IOException ex) {
								throw new IOException("Port not available: " + port);
							}
						} else {
							port = 8000;
							for (; port < 8100; port++) {
								try {
									server = new ServerSocket(port);
									break;
								} catch (IOException ex) {
									continue;
								}
							}
							if (server == null)
								throw new IOException("No free port available in range [8000, 8099]");
						}
						System.out.println("port:" + port);
					}
					String JAVA_REPL_KILL_AFTER_IDLE = System.getenv("JAVA_REPL_KILL_AFTER_IDLE");
					if (JAVA_REPL_KILL_AFTER_IDLE != null) {
						try {
							server.setSoTimeout(1000 * Integer.parseInt(JAVA_REPL_KILL_AFTER_IDLE));
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Not a valid timeout: " + JAVA_REPL_KILL_AFTER_IDLE);
						}
					}
					try {
						while (true) {
							// accept connections (single-threaded)
							Socket conn = server.accept();
							try (OutputStream chan = conn.getOutputStream()) {
								PrintStream outStream = new PrintStream(
									new MultiplexedOutputStream(chan, '1'),
									true);
								PrintStream errStream = new PrintStream(
									new MultiplexedOutputStream(chan, '2'),
									true);
								InputStream restoreIn = System.in;
								PrintStream restoreOut = System.out;
								PrintStream restoreErr = System.err;
								System.setIn(conn.getInputStream());
								System.setOut(outStream);
								System.setErr(errStream);
								try {
									int v = rep();
									outStream.close();
									errStream.close();
									chan.write(("x:" + v).getBytes());
									chan.flush();
								} finally {
									System.setIn(restoreIn);
									System.setOut(restoreOut);
									System.setErr(restoreErr);
								}
							}
						}
					} catch (SocketTimeoutException e) {
					}
				} else if ("--repl".equals(args[1])) {
					// run in REPL mode (Read-Evaluate-Print-Loop)
					System.exit(repl());
				} else {
					// run one evaluation and return
					int v = eval(args[1], Arrays.copyOfRange(args, 2, args.length));
					if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
						// start a REPL server if JAVA_REPL_PORT is set and port is available,
						// so that the next `eval-java' call will be faster
						String JAVA_REPL_PORT = System.getenv("JAVA_REPL_PORT");
						if (JAVA_REPL_PORT != null && !"".equals(JAVA_REPL_PORT))
							try {
								spawnReplServer();
							} catch (Throwable e) {
								// fail silently
							}
					}
					System.exit(v);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			System.err.flush();
			System.exit(1);
		}
	}

	private static String thisExecutableArg = null;
	private static File baseDir = null;
	private static File javaDir = null;
	private static File classesDir = null;
	private static List<File> classPath = null;
	private static String javac = null;

	private static int eval(CharSequence javaCode, String... commandLineArgs) {
		return eval(javaCode.toString());
	}

	private static int eval(String javaCode, String... commandLineArgs) {
		javaCode = javaCode.trim();
		if (javaCode.isEmpty())
			return 0;
		if ("true".equals(System.getenv("ECHO")))
			System.err.println(javaCode);
		try {
			List<String> imports = new ArrayList<>(); {
				imports.add("java.io.*");
				imports.add("static java.lang.System.err");
				imports.add("java.net.*");
				imports.add("java.nio.file.*");
				imports.add("java.util.*");
				imports.add("java.util.regex.*");
				imports.add("java.util.stream.*");
				imports.add("static lib.util.*");
				String fromEnv = System.getenv("IMPORTS");
				if (fromEnv != null && !"".equals(fromEnv.trim()))
					for (String i : fromEnv.trim().split("\\s+"))
						imports.add(i);
				if ((fromEnv = System.getenv("STATIC_IMPORTS")) != null && !"".equals(fromEnv.trim()))
					for (String i : fromEnv.trim().split("\\s+"))
						imports.add("static " + i); }
			javaCode = Pattern.compile(" *\\\\$", Pattern.MULTILINE).matcher(javaCode).replaceAll("");
			javaCode = String.format(
				"%s\n\n" +
				"public class [CLASSNAME] {\n\n" +
				"public static void main(String[] commandLineArgs) throws Throwable {\n\n%s\n\n}\n}\n",
				"import " + String.join(";\nimport ", imports) + ";",
				javaCode);
			String className = "recipe_" + md5(javaCode);
			javaCode = javaCode.replace("[CLASSNAME]", className);
			File classFile = new File(classesDir, className + ".class");
			if (!classFile.exists()) {
				File javaFile = new File(javaDir, className + ".java");
				javaFile.getParentFile().mkdirs();
				OutputStream os = new FileOutputStream(javaFile);
				try {
					os.write(javaCode.getBytes("UTF-8"));
					os.flush();
				} finally {
					os.close();
				}
				if (javac == null) {
					javac = System.getProperty("os.name").toLowerCase().startsWith("windows")
						? "javac.exe"
						: "javac";
					String JAVA_HOME = System.getenv("JAVA_HOME");
					if (JAVA_HOME != null) {
						File f = new File(new File(new File(JAVA_HOME), "bin"), javac);
						if (f.exists())
							javac = f.getAbsolutePath();
					}
				}
				classesDir.mkdirs();
				Process p = new ProcessBuilder(
					javac,
					"-cp", classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)),
					"-d", classesDir.getAbsolutePath(),
					javaFile.getAbsolutePath()
				).start();
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
				} finally {
					executor.shutdownNow();
				}
				int rv = p.waitFor();
				System.out.flush();
				System.err.flush();
				if (rv != 0)
					return rv;
			}
			URLClassLoader classLoader = new URLClassLoader(
				classPath.stream().map(eval_java::toURL).toArray(URL[]::new),
				Thread.currentThread().getContextClassLoader());
			Thread.currentThread().setContextClassLoader(classLoader);
			Class.forName(className, true, classLoader)
			     .getDeclaredMethod("main", String[].class)
			     .invoke(null, (Object)commandLineArgs);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException) {
				e = ((InvocationTargetException)e).getTargetException();
				if ("lib.util$SystemExit".equals(e.getClass().getName()))
					try {
						Field f = e.getClass().getDeclaredField("exitValue");
						f.setAccessible(true);
						return f.getInt(e);
					} catch (IllegalAccessException|NoSuchFieldException iae) {
						throw new IllegalStateException(iae); // should not happen
					}
			}
			e.printStackTrace();
			System.err.flush();
			return 1;
		}
		return 0;
	}

	private static int repl() {
		return rep(true);
	}

	private static int rep() {
		return rep(false);
	}

	private static int rep(boolean loop) {
		Integer v = 0;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			StringBuilder javaCode = null;
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				if (javaCode == null) javaCode = new StringBuilder();
				javaCode.append(line).append('\n');
				if (!line.endsWith("\\")) {
					v = eval(javaCode);
					if (!loop)
						break;
					javaCode = null;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.flush();
			System.exit(1);
		}
		return v;
	}

	private static int spawnReplServer() throws IOException {
		File java = new File(
			new File(new File(System.getProperty("java.home")), "bin"),
			System.getProperty("os.name").toLowerCase().startsWith("windows")
				? "java.exe"
				: "java");
		ProcessBuilder pb = new ProcessBuilder(
			java.getAbsolutePath(),
			"-cp", baseDir != null ? baseDir.getAbsolutePath() : ".",
			"eval_java",
			thisExecutableArg,
			"--repl-server");
		Process p = pb.start();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = reader.readLine();
			if (line.startsWith("port:")) {
				line = line.substring("port:".length());
				return Integer.parseInt(line);
			} else
				throw new IllegalStateException("Unexpected output: " + line);
		} catch (Throwable e) {
			if (p.isAlive())
				p.destroy();
			throw new IllegalStateException("Failed to spawn REPL server", e);
		}
	}

	public static String md5(String data) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		byte[] bytes = MessageDigest.getInstance("MD5").digest(data.getBytes("UTF-8"));
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < bytes.length; i++)
			s.append(Integer
			         .toString((bytes[i] & 0xff) + 0x100, 16)
			         .substring(1));
		return s.toString();
	}

	public static int getJavaVersion() {
		String v = System.getProperty("java.version");
		if (v.startsWith("1."))
			v = v.substring(2, 3);
		else
			v = v.replaceAll("\\..*", "");
		return Integer.parseInt(v);
	}

	public static URL toURL(File f) {
		try {
			return f.toURI().toURL();
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	public static void copy(InputStream src, OutputStream dest) throws IOException {
		byte data[] = new byte[1024];
		int read;
		while ((read = src.read(data)) != -1)
			dest.write(data, 0, read);
	}

	private static class MultiplexedOutputStream extends FilterOutputStream {

		private final OutputStream stream;
		private final char prefix;
		private final ByteArrayOutputStream buffer;

		public MultiplexedOutputStream(OutputStream stream, char prefix) {
			super(new ByteArrayOutputStream());
			buffer = (ByteArrayOutputStream)out;
			this.stream = stream;
			this.prefix = prefix;
		}

		@Override
		public void flush() throws IOException {
			super.flush();
			if (buffer.size() > 0) {
				byte[] content = buffer.toByteArray();
				buffer.reset();
				int i = 0;
				for (int j = 0; j < content.length; j++) {
					if (content[j] == '\n') {
						stream.write(prefix);
						stream.write(':');
						stream.write(content, i, j + 1 - i);
						stream.flush();
						i = j + 1;
					}
				}
				if (i < content.length)
					buffer.write(content, i, content.length - i);
			}
		}

		@Override
		public void close() throws IOException {
			flush();
			if (buffer.size() > 0) {
				buffer.write('\n');
				stream.write(prefix);
				stream.write(':');
				stream.write(buffer.toByteArray());
				stream.flush();
			}
			super.close();
		}
	}
}
