import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class eval_java {

	public static void main(String[] args) {
		File thisExecutable = new File(args[0]); // path of eval-java(.exe) relative to current directory
		String javaCode = args[1];
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
				if (fromEnv != null)
					for (String i : fromEnv.split("\\s+"))
						imports.add(i); }
			javaCode = Pattern.compile(" *\\\\$", Pattern.MULTILINE).matcher(javaCode).replaceAll("");
			javaCode = String.format(
				"%s\n\n" +
				"public class [CLASSNAME] {\n\n" +
				"public static void main(String args[]) throws Throwable {\n\n%s\n\n}\n}\n",
				"import " + String.join(";\nimport ", imports) + ";",
				javaCode);
			String className = "recipe_" + md5(javaCode);
			javaCode = javaCode.replace("[CLASSNAME]", className);
			File baseDir = thisExecutable.getParentFile().getParentFile().getParentFile();
			File javaDir = new File(baseDir, "recipes/java");
			File classesDir = new File(new File(baseDir, "recipes/classes"), "" + getJavaVersion());
			File classFile = new File(classesDir, className + ".class");
			List<File> classPath = new ArrayList<>(); {
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
				String javac = System.getProperty("os.name").toLowerCase().startsWith("windows")
					? "javac.exe"
					: "javac";
				String JAVA_HOME = System.getenv("JAVA_HOME");
				if (JAVA_HOME != null) {
					File f = new File(new File(new File(JAVA_HOME), "bin"), javac);
					if (f.exists())
						javac = f.getAbsolutePath();
				}
				classesDir.mkdirs();
				int rv = new ProcessBuilder(
					javac,
					"-cp", classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)),
					"-d", classesDir.getAbsolutePath(),
					javaFile.getAbsolutePath()
				).inheritIO().start().waitFor();
				System.out.flush();
				System.err.flush();
				if (rv != 0)
					System.exit(rv);
			}
			URLClassLoader classLoader = new URLClassLoader(
				classPath.stream().map(eval_java::toURL).toArray(URL[]::new),
				Thread.currentThread().getContextClassLoader());
			Thread.currentThread().setContextClassLoader(classLoader);
			Class.forName(className, true, classLoader)
			     .getDeclaredMethod("main", String[].class)
			     .invoke(null, (Object)new String[0]);
		} catch (Throwable e) {
			e.printStackTrace();
			System.err.flush();
			System.exit(1);
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
}
