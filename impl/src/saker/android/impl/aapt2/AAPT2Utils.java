package saker.android.impl.aapt2;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.util.cache.CacheKey;
import saker.nest.bundle.NestBundleClassLoader;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;

public class AAPT2Utils {
	private AAPT2Utils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Tells whether or not the aapt2_jni library from the android build-tools SDK can be used.
	 * <p>
	 * Currently we set this to <code>false</code>, as loading the library somewhy crashes on macOS. (May crash on other
	 * operating systems as well, but we haven't tested that.)
	 * <p>
	 * If this issue gets fixed then we can set this to <code>true</code> and implement the rest of the functionality.
	 */
	// !!!!! this feature should be toggleable if implemented !!!!!
	private static final boolean AAPT2_JNI_LIB_USAGE_ENABLED = false;

	//maps dll local paths to executors
	private static final ConcurrentSkipListMap<SakerPath, Optional<AAPT2Executor>> jniAAPT2Executors = new ConcurrentSkipListMap<>();
	private static final ConcurrentSkipListMap<Path, Optional<AAPT2Executor>> cachedDllAAPT2Executors = new ConcurrentSkipListMap<>();

	public static AAPT2Executor getAAPT2Executor(SakerEnvironment environment,
			NavigableMap<String, SDKReference> sdkrefs) throws Exception {
		SDKReference buildtoolssdk = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		if (buildtoolssdk == null) {
			throw new SDKNotFoundException("Android build tools SDK not found for aapt2 invocation.");
		}
		if (AAPT2_JNI_LIB_USAGE_ENABLED) {
			SakerPath dllpath;
			if ("x86".equalsIgnoreCase(System.getProperty("os.arch"))) {
				dllpath = buildtoolssdk.getPath(AndroidBuildToolsSDKReference.PATH_LIB_JNI_AAPT2);
			} else {
				dllpath = buildtoolssdk.getPath(AndroidBuildToolsSDKReference.PATH_LIB64_JNI_AAPT2);
			}
			if (dllpath != null) {
				AAPT2Executor dllexecutor = ObjectUtils.getOptional(getDllAAPT2Executor(dllpath));
				if (dllexecutor != null) {
					return dllexecutor;
				}
				// fallback to executable based execution
			}
		}
		SakerPath exepath = buildtoolssdk.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdk);
		}
		Boolean supportsdaemon = environment
				.getEnvironmentPropertyCurrentValue(new AAPT2DaemonSupportedEnvironmentProperty(exepath));
		if (Boolean.TRUE.equals(supportsdaemon)) {
			return new AAPT2Executor() {
				@Override
				public int invokeAAPT2WithArguments(List<String> args, OutputStream output) throws Exception {
					return invokeAAPT2WithDaemonOrExecutable(environment, exepath, args, output);
				}
			};
		}
		return new AAPT2Executor() {
			@Override
			public int invokeAAPT2WithArguments(List<String> args, OutputStream output) throws Exception {
				return invokeAAPT2WithExecutable(exepath, args, output);
			}
		};
	}

	private static Optional<AAPT2Executor> getDllAAPT2Executor(SakerPath dllpath) {
		Optional<AAPT2Executor> execcutor = jniAAPT2Executors.get(dllpath);
		if (execcutor != null) {
			return execcutor;
		}
		FileHashResult hash;
		try {
			hash = LocalFileProvider.getInstance().hash(dllpath, FileUtils.DEFAULT_FILE_HASH_ALGORITHM);
			ClassLoader thiscl = AAPT2Utils.class.getClassLoader();
			NestBundleClassLoader cl = (NestBundleClassLoader) thiscl;
			Path bundlestoragepath = cl.getBundle().getBundleStoragePath().resolve("sdk_aapt2_jni")
					.resolve(StringUtils.toHexString(hash.getHash()));
			Path cacheddllpath = bundlestoragepath.resolve(System.mapLibraryName("aapt2_jni"));
			execcutor = cachedDllAAPT2Executors.get(cacheddllpath);
			if (execcutor != null) {
				return execcutor;
			}
			Files.createDirectories(bundlestoragepath);
			synchronized (("load_aapt2_jni" + cacheddllpath).intern()) {
				execcutor = cachedDllAAPT2Executors.get(cacheddllpath);
				if (execcutor != null) {
					return execcutor;
				}
				AAPT2Executor result = null;
				try {
					if (!Files.isRegularFile(cacheddllpath)) {
						Path tempdllpath = cacheddllpath
								.resolveSibling(cacheddllpath.getFileName() + "_" + UUID.randomUUID());
						try {
							try {
								Files.copy(LocalFileProvider.toRealPath(dllpath), tempdllpath);
							} catch (Exception e) {
								e.printStackTrace();
								// failed to copy the dll
								return null;
							}
							Files.move(tempdllpath, cacheddllpath);
						} catch (Exception e) {
							e.printStackTrace();
							// failed to move the dll to its final path
							if (!Files.isRegularFile(cacheddllpath)) {
								// failed to load the dll to the bundle storage
								return null;
							}
							// else the file already exists at path, we can continue
						} finally {
							try {
								Files.deleteIfExists(tempdllpath);
							} catch (Exception e) {
								// ignore
							}
						}
					}

					MultiDataClassLoader jnisupportcl = new MultiDataClassLoader(thiscl,
							SubDirectoryClassLoaderDataFinder.create("aapt2jnisupport", thiscl));
					try {
						Class<?> aapt2jniclass = Class.forName("com.android.tools.aapt2.Aapt2Jni", false, jnisupportcl);
						result = (AAPT2Executor) aapt2jniclass.getMethod("init", Path.class).invoke(null,
								cacheddllpath);
					} catch (LinkageError | Exception e) {
						e.printStackTrace();
						return null;
					}
					return Optional.of(result);
				} finally {
					cachedDllAAPT2Executors.put(cacheddllpath, Optional.ofNullable(result));
				}
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static int invokeAAPT2WithDaemonOrExecutable(SakerEnvironment environment, SakerPath exepath,
			List<String> args, OutputStream output) throws Exception, IOException, InterruptedException {
		try {
			return invokeAAPT2WithArgumentsWithDaemon(environment, exepath, args, output);
		} catch (AAPT2DaemonStartException e) {
			// continue, try without daemon
		}

		return invokeAAPT2WithExecutable(exepath, args, output);
	}

	private static int invokeAAPT2WithExecutable(SakerPath exepath, List<String> args, OutputStream output)
			throws IOException, InterruptedException {
		//TODO use @argument-file when input files are too many to fit on the command line
		//     (not necessary with daemon)

		ArrayList<String> fullcmd = new ArrayList<>(1 + args.size());
		fullcmd.add(exepath.toString());
		fullcmd.addAll(args);
		ProcessBuilder pb = new ProcessBuilder(fullcmd);
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		IOUtils.close(proc.getOutputStream());
		StreamUtils.copyStream(proc.getInputStream(), output);
		return proc.waitFor();
	}

	private static int invokeAAPT2WithArgumentsWithDaemon(SakerEnvironment environment, SakerPath exepath,
			List<String> args, OutputStream output) throws Exception {
		DaemonManager daemonmanager = environment.getCachedData(new AAPT2DaemonManagerCacheKey(environment, exepath));
		AAPT2DaemonController controller = daemonmanager.getDaemonController();
		try {
			return controller.executeCommand(args, output);
		} finally {
			controller.done();
		}
	}

	private static class DaemonManagerResource implements Closeable {
		private static final AtomicIntegerFieldUpdater<AAPT2Utils.DaemonManagerResource> AIFU_idCounter = AtomicIntegerFieldUpdater
				.newUpdater(AAPT2Utils.DaemonManagerResource.class, "idCounter");

		private SakerEnvironment environment;
		private SakerPath exePath;
		private final ConcurrentLinkedDeque<WeakReference<AAPT2DaemonController>> daemonControllers = new ConcurrentLinkedDeque<>();

		@SuppressWarnings("unused")
		private volatile int idCounter;

		public DaemonManagerResource(SakerEnvironment environment, SakerPath exePath) {
			this.environment = environment;
			this.exePath = exePath;
		}

		public AAPT2DaemonController getDaemonProcess() throws Exception {
			WeakReference<AAPT2DaemonController> proc;
			while (true) {
				proc = daemonControllers.pollFirst();
				if (proc == null) {
					break;
				}
				AAPT2DaemonController controller = proc.get();
				if (controller != null) {
					if (controller.use()) {
						return controller;
					}
					//don't put back, as it should be in it anyway
				}
			}
			while (true) {
				//new controllers may still be in use if the managers get GCd, and re-retrieved for the same exe
				AAPT2DaemonController ncontroller = environment.getCachedData(
						new AAPT2DaemonCacheKey(environment, exePath, AIFU_idCounter.getAndIncrement(this)));
				if (ncontroller.use()) {
					return ncontroller;
				}
			}
		}

		public void offer(AAPT2DaemonController controller) {
			daemonControllers.addFirst(new WeakReference<>(controller));
		}

		public void close(AAPT2DaemonController controller) {
			//remove the controller from the lost so it is no longer useable
			Iterator<WeakReference<AAPT2DaemonController>> it = daemonControllers.iterator();
			while (it.hasNext()) {
				WeakReference<AAPT2DaemonController> ref = it.next();
				AAPT2DaemonController c = ref.get();
				if (c == null) {
					it.remove();
					continue;
				}
				if (c == controller) {
					it.remove();
					break;
				}
			}
		}

		@Override
		public void close() throws IOException {
			//no-op
		}
	}

	private static class DaemonManager {
		private DaemonManagerResource resource;

		public DaemonManager(DaemonManagerResource resource) {
			this.resource = resource;
		}

		public AAPT2DaemonController getDaemonController() throws Exception {
			return resource.getDaemonProcess();
		}

		public void offer(AAPT2DaemonController controller) {
			resource.offer(controller);
		}

		public void close(AAPT2DaemonController controller) {
			resource.close(controller);
		}
	}

	private static class AAPT2DaemonController {
		public static final int STATE_READY = 0;
		public static final int STATE_CLOSED = 1;
		public static final int STATE_USED = 2;

		private static final AtomicIntegerFieldUpdater<AAPT2Utils.AAPT2DaemonController> AIFU_state = AtomicIntegerFieldUpdater
				.newUpdater(AAPT2Utils.AAPT2DaemonController.class, "state");

		private DaemonManager manager;
		private AAPT2DaemonProcess process;

		@SuppressWarnings("unused")
		private volatile int state = STATE_READY;

		public AAPT2DaemonController(AAPT2DaemonProcess process, DaemonManager manager) {
			this.process = process;
			this.manager = manager;
		}

		public boolean use() {
			if (!AIFU_state.compareAndSet(this, STATE_READY, STATE_USED)) {
				return false;
			}
			if (!process.isAlive()) {
				//the actual closing will still be called by the cache manager.
				//just set the state here so we're not reused
				this.state = STATE_CLOSED;
				return false;
			}
			return true;
		}

		public int executeCommand(Iterable<String> arguments, OutputStream stdout) throws Exception {
			StringBuilder sb = new StringBuilder();
			for (String a : arguments) {
				sb.append(a);
				sb.append('\n');
			}
			sb.append('\n');

			Integer res = null;
			try (OutputStreamWriter writer = new OutputStreamWriter(StreamUtils.closeProtectedOutputStream(stdout),
					StandardCharsets.UTF_8)) {
				OutputStream procos = process.process.getOutputStream();
				procos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
				procos.flush();
				while (true) {
					String l = process.outReader.readLine();
					if (l == null) {
						break;
					}
					//TODO sometimes this skips lines
					if ("Done".equals(l)) {
						if (res == null) {
							res = 0;
						}
						break;
					}
					if ("Error".equals(l)) {
						res = 1;
						continue;
					}
					writer.append(l);
					writer.write('\n');
				}

			}
			return res == null ? -1 : res;
		}

		public void done() {
			if (AIFU_state.compareAndSet(this, STATE_USED, STATE_READY)) {
				manager.offer(this);
			}
		}

		public void close() {
			this.state = STATE_CLOSED;
			manager.close(this);
		}
	}

	private static class AAPT2DaemonStartException extends IOException {
		private static final long serialVersionUID = 1L;

		public AAPT2DaemonStartException(String message) {
			super(message);
		}
	}

	private static class AAPT2DaemonProcess implements Closeable {
		protected Process process;
		protected BufferedReader outReader;

		public AAPT2DaemonProcess(Process process) throws IOException {
			this.process = process;
			this.outReader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

			String l = outReader.readLine();
			//compare ignore case just in case
			if (!"Ready".equals(l)) {
				AAPT2DaemonStartException failexc = new AAPT2DaemonStartException("Failed to start aapt2 daemon. ");
				try {
					IOUtils.close(process.getOutputStream(), process.getInputStream(), process.getErrorStream());
					process.destroyForcibly();
				} catch (Exception e) {
					failexc.addSuppressed(e);
				}
				throw failexc;
			}

		}

		public boolean isAlive() {
			return process.isAlive();
		}

		@Override
		public void close() throws IOException {
			//just kill it. if its in use, then this close is not called by the data cache manager. killing is fine
			IOUtils.close(process.getOutputStream(), process.getInputStream(), this.outReader,
					process.getErrorStream());
			process.destroyForcibly();
			//don't need to join, let the reader thread die
		}
	}

	private static class AAPT2DaemonManagerCacheKey implements CacheKey<DaemonManager, DaemonManagerResource> {
		private transient SakerEnvironment environment;
		private SakerPath exePath;

		public AAPT2DaemonManagerCacheKey(SakerEnvironment environment, SakerPath exePath) {
			this.environment = environment;
			this.exePath = exePath;
		}

		@Override
		public DaemonManagerResource allocate() throws Exception {
			return new DaemonManagerResource(environment, exePath);
		}

		@Override
		public DaemonManager generate(DaemonManagerResource resource) throws Exception {
			return new DaemonManager(resource);
		}

		@Override
		public boolean validate(DaemonManager data, DaemonManagerResource resource) {
			return true;
		}

		@Override
		public long getExpiry() {
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(DaemonManager data, DaemonManagerResource resource) throws Exception {
			resource.close();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((exePath == null) ? 0 : exePath.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AAPT2DaemonManagerCacheKey other = (AAPT2DaemonManagerCacheKey) obj;
			if (exePath == null) {
				if (other.exePath != null)
					return false;
			} else if (!exePath.equals(other.exePath))
				return false;
			return true;
		}

	}

	private static class AAPT2DaemonCacheKey implements CacheKey<AAPT2DaemonController, AAPT2DaemonProcess> {
		private transient SakerEnvironment environment;
		private SakerPath exePath;
		private int id;

		public AAPT2DaemonCacheKey(SakerEnvironment environment, SakerPath exePath, int id) {
			this.environment = environment;
			this.exePath = exePath;
			this.id = id;
		}

		@Override
		public AAPT2DaemonProcess allocate() throws Exception {
			ProcessBuilder pb = new ProcessBuilder(exePath.toString(), "daemon");
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			return new AAPT2DaemonProcess(proc);
		}

		@Override
		public AAPT2DaemonController generate(AAPT2DaemonProcess resource) throws Exception {
			DaemonManager manager = environment.getCachedData(new AAPT2DaemonManagerCacheKey(environment, exePath));
			return new AAPT2DaemonController(resource, manager);
		}

		@Override
		public boolean validate(AAPT2DaemonController data, AAPT2DaemonProcess resource) {
			return resource.isAlive();
		}

		@Override
		public long getExpiry() {
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(AAPT2DaemonController data, AAPT2DaemonProcess resource) throws Exception {
			if (data != null) {
				data.close();
			}
			resource.close();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((exePath == null) ? 0 : exePath.hashCode());
			result = prime * result + id;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AAPT2DaemonCacheKey other = (AAPT2DaemonCacheKey) obj;
			if (exePath == null) {
				if (other.exePath != null)
					return false;
			} else if (!exePath.equals(other.exePath))
				return false;
			if (id != other.id)
				return false;
			return true;
		}
	}
}
