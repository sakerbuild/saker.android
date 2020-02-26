package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class AarFolderExtractWorkerTaskFactory extends AarEntryExporterWorkerTaskFactoryBase<AarResourcesTaskOutput> {
	private static final long serialVersionUID = 1L;

	//includes slash!
	private String folderName;

	/**
	 * For {@link Externalizable}.
	 */
	public AarFolderExtractWorkerTaskFactory() {
	}

	public AarFolderExtractWorkerTaskFactory(FileLocation inputFile, String foldername) {
		super(inputFile,
				SakerPath.valueOf("saker.aar.extract").resolve(createOutputRelativePath(inputFile)).resolve(foldername),
				OUTPUT_KIND_EXECUTION);
		folderName = verifyFolderName(foldername) + "/";
	}

	private static SakerPath verifyFolderName(String foldername) {
		SakerPath path = SakerPath.valueOf(foldername);
		if (!path.isForwardRelative()) {
			throw new IllegalArgumentException("Folder must be relative: " + foldername);
		}
		if (path.getNameCount() != 1) {
			throw new InvalidPathFormatException("Folder must have 1 path name.");
		}
		return path;
	}

	public TaskIdentifier getWorkerTaskId() {
		return new AarFolderExtractWorkerTaskIdentifier(outputRelativePath, outPathKind);
	}

	@Override
	public AarResourcesTaskOutput run(TaskContext taskcontext) throws Exception {
		//TODO better display id
		taskcontext.setStandardOutDisplayIdentifier("saker.aar.resources");
		return super.run(taskcontext);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(folderName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		folderName = (String) in.readObject();
	}

	@Override
	protected AarResourcesTaskOutput executionAarNotFound(SakerPath inputFile) {
		return new AarNotFoundAarClassesTaskOutput(inputFile);
	}

	@Override
	protected AarResourcesTaskOutput localAarNotFound(SakerPath localinputpath) {
		return new AarNotFoundAarClassesTaskOutput(localinputpath);
	}

	@Override
	protected AarResourcesTaskOutput handleLocalFile(TaskContext taskcontext, SakerPath localpath) throws Exception {
		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputRelativePath);
		outdir.clear();

		NavigableMap<SakerPath, ContentDescriptor> dependencies = new TreeMap<>();
		Set<FileLocation> resfilelocations = new LinkedHashSet<>();

		try (ZipFile zf = new ZipFile(LocalFileProvider.toRealPath(localpath).toFile())) {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = (ZipEntry) entries.nextElement();
				String entryname = e.getName();
				if (!isResourceEntryName(entryname)) {
					continue;
				}
				SakerPath respath = SakerPath.valueOf(e.getName().substring(4));
				ByteArrayRegion resourcebytes;
				try (InputStream zipin = zf.getInputStream(e)) {
					resourcebytes = StreamUtils.readStreamFully(zipin);
				}

				ByteArraySakerFile resfile = new ByteArraySakerFile(respath.getFileName(), resourcebytes);
				taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(outdir, respath.getParent()).add(resfile);
				SakerPath resfilepath = resfile.getSakerPath();

				dependencies.put(resfilepath, resfile.getContentDescriptor());
				resfilelocations.add(ExecutionFileLocation.create(resfilepath));
			}
		}
		outdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(null, dependencies);
		return new ExecutionAarResourcesTaskOutput(outdir.getSakerPath(), resfilelocations);
	}

	@Override
	protected AarResourcesTaskOutput handleExecutionFile(TaskContext taskcontext, SakerFile f) throws Exception {
		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputRelativePath);
		outdir.clear();

		NavigableMap<SakerPath, ContentDescriptor> dependencies = new TreeMap<>();
		Set<FileLocation> resfilelocations = new LinkedHashSet<>();

		try (InputStream instream = f.openInputStream();
				ZipInputStream zipin = new ZipInputStream(instream)) {
			for (ZipEntry e; (e = zipin.getNextEntry()) != null;) {
				String entryname = e.getName();
				if (!isResourceEntryName(entryname)) {
					continue;
				}
				SakerPath respath = SakerPath.valueOf(entryname.substring(4));
				ByteArrayRegion resourcebytes = StreamUtils.readStreamFully(zipin);

				ByteArraySakerFile resfile = new ByteArraySakerFile(respath.getFileName(), resourcebytes);
				taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(outdir, respath.getParent()).add(resfile);
				SakerPath resfilepath = resfile.getSakerPath();

				dependencies.put(resfilepath, resfile.getContentDescriptor());
				resfilelocations.add(ExecutionFileLocation.create(resfilepath));
			}
		}
		outdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(null, dependencies);
		return new ExecutionAarResourcesTaskOutput(outdir.getSakerPath(), resfilelocations);
	}

	private boolean isResourceEntryName(String entryname) {
		if (entryname.isEmpty()) {
			return false;
		}
		if (entryname.length() <= folderName.length()) {
			return false;
		}
		if (entryname.endsWith("/")) {
			return false;
		}
		if (!entryname.startsWith(folderName)) {
			return false;
		}
		return true;
	}

	public static SakerPath createOutputRelativePath(FileLocation inputFile) {
		SakerPath[] result = { null };
		inputFile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerPath inpath = loc.getPath();
				result[0] = SakerPath.valueOf(inpath.getFileName())
						.resolve(StringUtils.toHexString(FileUtils.hashString(inpath.toString())));
			}

			@Override
			public void visit(LocalFileLocation loc) {
				SakerPath inpath = loc.getLocalPath();
				result[0] = SakerPath.valueOf(inpath.getFileName())
						.resolve(StringUtils.toHexString(FileUtils.hashString(inpath.toString())));
			}
		});
		return result[0];
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((folderName == null) ? 0 : folderName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AarFolderExtractWorkerTaskFactory other = (AarFolderExtractWorkerTaskFactory) obj;
		if (folderName == null) {
			if (other.folderName != null)
				return false;
		} else if (!folderName.equals(other.folderName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "AarFolderExtractWorkerTaskFactory[folderName=" + folderName + "]";
	}

}
