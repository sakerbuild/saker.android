package saker.android.d8support;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

import saker.build.runtime.execution.SakerLog;
import saker.build.task.TaskContext;

public final class D8ExecutionDiagnosticsHandler implements DiagnosticsHandler {
	private final TaskContext taskContext;
	private boolean hadError;

	public D8ExecutionDiagnosticsHandler(TaskContext taskcontext) {
		this.taskContext = taskcontext;
	}

	public boolean hadError() {
		return hadError;
	}

	@Override
	public void error(Diagnostic diagnostic) {
		hadError = true;
		log(SakerLog.error(), diagnostic);
	}

	@Override
	public void info(Diagnostic diagnostic) {
		log(SakerLog.info(), diagnostic);
	}

	@Override
	public void warning(Diagnostic diagnostic) {
		log(SakerLog.warning(), diagnostic);
	}

	private void log(SakerLog log, Diagnostic diagnostic) {
		log.out(taskContext);
		StringBuilder sb = new StringBuilder();
		Origin origin = diagnostic.getOrigin();
		if (origin != Origin.unknown()) {
			int l = sb.length();
			if (origin instanceof SakerFileOrigin) {
				log.path(((SakerFileOrigin) origin).getFilePath());
			} else {
				sb.append(origin);
			}
			Position pos = diagnostic.getPosition();
			if (pos != Position.UNKNOWN) {
				if (sb.length() != l) {
					sb.append(' ');
				}
				sb.append("at ");
				sb.append(pos);
			}
			if (sb.length() != l) {
				//something was added before
				sb.append(": ");
			}
		}
		sb.append(diagnostic.getDiagnosticMessage());
		log.println(sb.toString());
	}
}