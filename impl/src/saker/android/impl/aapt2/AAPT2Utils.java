package saker.android.impl.aapt2;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.util.cache.CacheKey;

public class AAPT2Utils {
	private AAPT2Utils() {
		throw new UnsupportedOperationException();
	}

	public static int invokeAAPT2WithArguments(SakerEnvironment environment, SakerPath exepath, List<String> args,
			OutputStream output) throws Exception {
		Boolean supportsdaemon = environment
				.getEnvironmentPropertyCurrentValue(new AAPT2DaemonSupportedEnvironmentProperty(exepath));
		if (Boolean.TRUE.equals(supportsdaemon)) {
			try {
				return invokeAAPT2WithArgumentsWithDaemon(environment, exepath, args, output);
			} catch (AAPT2DaemonStartException e) {
				//continue, try without daemon
			}
		}

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

	public static int invokeAAPT2WithArguments(SakerEnvironment environment, SakerPath exepath, String[] args,
			OutputStream output) throws Exception {
		return invokeAAPT2WithArguments(environment, exepath, ImmutableUtils.asUnmodifiableArrayList(args), output);
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
			if (!process.process.isAlive()) {
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
