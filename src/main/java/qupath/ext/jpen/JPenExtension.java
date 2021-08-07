/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.jpen;

import java.awt.Point;
import java.awt.geom.Point2D.Float;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpen.PButtonEvent;
import jpen.PKindEvent;
import jpen.PLevelEvent;
import jpen.PScrollEvent;
import jpen.PenDevice;
import jpen.PenEvent;
import jpen.PenManager;
import jpen.PenProvider;
import jpen.PenProvider.Constructor;
import jpen.event.PenListener;
import jpen.event.PenManagerListener;
import jpen.owner.PenClip;
import jpen.owner.PenOwner;
import jpen.provider.NativeLibraryLoader;
import jpen.provider.osx.CocoaProvider;
import jpen.provider.wintab.WintabProvider;
import jpen.provider.xinput.XinputProvider;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.viewer.tools.QuPathPenManager;
import qupath.lib.gui.viewer.tools.QuPathPenManager.PenInputManager;

/**
 * QuPath extension to make the Brush tool pressure-sensitive when used with a graphics tablet,
 * by using JPen - http://jpen.sourceforge.net/
 * 
 * @author Pete Bankhead
 *
 */
public class JPenExtension implements QuPathExtension, GitHubProject {
	
	private static Logger logger = LoggerFactory.getLogger(JPenExtension.class);
	
	private static boolean alreadyInstalled = false;
	
	private static int defaultFrequency = 40;
	
	private static boolean nativeLibraryLoaded = false;
	
	static {
		try {
			nativeLibraryLoaded = loadNativeLibrary();
			if (nativeLibraryLoaded)
				logger.debug("Native library loaded");
			else
				logger.debug("Unable to preload JPen native library (I couldn't find it)");
		} catch (Throwable t) {
			logger.warn("Unable to preload JPen native library: " + t.getLocalizedMessage(), t);
		}
	}


	@Override
	public synchronized void installExtension(QuPathGUI qupath) {
		if (alreadyInstalled)
			return;
		try {
			loadNativeLibrary();
		} catch (Throwable t) {
			logger.warn("Unable to preload JPen native library: " + t.getLocalizedMessage(), t);
		}
		try {
			PenManager pm = new PenManager(new PenOwnerFX());
			pm.pen.setFirePenTockOnSwing(false);
			pm.pen.setFrequencyLater(defaultFrequency);
			PenInputManager manager = new JPenInputManager(pm);
			QuPathPenManager.setPenManager(manager);
			alreadyInstalled = true;
		} catch (Throwable t) {
			logger.warn("Unable to add JPen support: " + t.getLocalizedMessage(), t);
		}
	}
	
	
	@Override
	public Version getQuPathVersion() {
		return Version.parse("v0.3.0");
	}
	
	@Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "qupath", "qupath-extension-jpen");
	}


	/**
	 * Try to load native library from the extension jar.
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private static boolean loadNativeLibrary() throws URISyntaxException, IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		URL url = JPenExtension.class.getClassLoader().getResource("natives");
		logger.debug("JPen url: {}", url);
		if (url == null)
			return false;
		URI uri = url.toURI();
		Path path;
		if (uri.getScheme().equals("jar")) {
			try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
				var pathRoot = fs.getPath("natives");
				path = extractLib(pathRoot);
			}
		} else {
			path = Files.find(Paths.get(uri), 1, createMatcher()).findFirst().orElse(null);
		}
		if (Files.isRegularFile(path)) {
			logger.trace("Loading {}", path);
			System.load(path.toAbsolutePath().toString());
			
			// Try to update for the providers we use
			logger.trace("Updating cocoa");
			setLoaded(CocoaProvider.class);
			logger.trace("Updating xinput");
			setLoaded(XinputProvider.class);
			logger.trace("Updating wintab");
			setLoaded(WintabProvider.class);

			return true;
		} else {
			logger.debug("Path is not a regular file: {}", path);
			return false;
		}
	}
	
	/**
	 * In order to avoid forking JPen and support loading a native library from a jar, 
	 * we need to override the behavior of NativeLibraryLoader (which uses System.loadLibrary).
	 * If we have successfully loaded a library, we need to toggle the 'loaded' flag by reflection.
	 * 
	 * @param cls
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	static void setLoaded(Class<?> cls) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		var field = NativeLibraryLoader.class.getDeclaredField("loaded");
		field.setAccessible(true);

		var loader = cls.getDeclaredField("LIB_LOADER");
		loader.setAccessible(true);
		var obj = loader.get(cls);

		field.setBoolean(obj, true);
	}
	
	/**
	 * Extract native library to a temp file.
	 * @param pathRoot
	 * @return
	 * @throws IOException
	 */
	private static Path extractLib(Path pathRoot) throws IOException {
		var path = Files.find(pathRoot, 1, createMatcher()).findFirst().orElse(null);
		if (path == null)
			return null;
		logger.debug("JPen path to extract: {}", path);
		Path tempDir = Files.createTempDirectory("qupath-");
		Path tempFile = tempDir.resolve(pathRoot.relativize(path).toString());
		logger.trace("Requesting delete on exit");
		tempDir.toFile().deleteOnExit();
		tempFile.toFile().deleteOnExit();
		logger.debug("Copying {} to {}", path, tempFile);
		Files.copy(path, tempFile);
		logger.debug("Copy completed, new file size {}", tempFile.toFile().length());
		return tempFile;
	}

	private static BiPredicate<Path, BasicFileAttributes> createMatcher() {
		if (GeneralTools.isMac())
			return (p, a) -> matchLib(p, a, ".jnilib", ".dylib");
		if (GeneralTools.isWindows())
			return (p, a) -> matchLib(p, a, "64.dll");
		if (GeneralTools.isMac())
			return (p, a) -> matchLib(p, a, "64.so");
		return (p, a) -> false;
	}

	private static boolean matchLib(Path path, BasicFileAttributes attr, String... exts) {
		if (attr.isDirectory())
			return false;
		var name = path.getFileName().toString().toLowerCase();
		logger.trace("Checking name: {} against {}", name, Arrays.asList(exts));
		if (!name.startsWith("jpen") && !name.startsWith("libjpen"))
			return false;
		for (var ext : exts) {
			if (name.endsWith(ext))
				return true;
		}
		return false;
	}


	@Override
	public String getName() {
		return "JPen extension";
	}

	@Override
	public String getDescription() {
		return "Add pressure-sensitive graphics tablet support using JPen - http://jpen.sourceforge.net/html-home/";
	}	
	
	
	
	private static class JPenInputManager implements PenInputManager, PenListener, PenManagerListener {
		
		private PenManager pm;
		private long lastEventTime = 0L;
		
		JPenInputManager(PenManager pm) {
			this.pm = pm;
			this.pm.addListener(this);
			this.pm.pen.addListener(this);
		}
		
		boolean isRecent() {
			if (lastEventTime == 0L)
				return false;
			long timeDifference = System.currentTimeMillis() - lastEventTime;
			return timeDifference <= pm.pen.getFrequency();
		}

		@Override
		public boolean isEraser() {
			if (pm != null && !pm.getPaused() && isRecent() && pm.pen.getKind().getType() == jpen.PKind.Type.ERASER)
				return true;
			return false;
		}

		@Override
		public double getPressure() {
			if (pm != null && !pm.getPaused() && isRecent()) {
				switch (pm.pen.getKind().getType()) {
				case ERASER:
				case STYLUS:
					double pressure = pm.pen.getLevelValue(jpen.PLevel.Type.PRESSURE);
					return pressure;
				case CURSOR:
				case CUSTOM:
				case IGNORE:
				default:
					break;
				}
			}
			return 1.0;
		}

		@Override
		public void penKindEvent(PKindEvent ev) {}

		@Override
		public void penLevelEvent(PLevelEvent ev) {
			lastEventTime = ev.getTime();
		}

		@Override
		public void penButtonEvent(PButtonEvent ev) {}

		@Override
		public void penScrollEvent(PScrollEvent ev) {}

		@Override
		public void penTock(long availableMillis) {
			// Log that a pen event has been noted (can be fired when pen is hovering above the device)
			lastEventTime = System.currentTimeMillis();
		}

		@Override
		public void penDeviceAdded(Constructor providerConstructor, PenDevice penDevice) {
			logger.debug("PenDevice added: {} ({})", penDevice, providerConstructor);
		}

		@Override
		public void penDeviceRemoved(Constructor providerConstructor, PenDevice penDevice) {
			logger.debug("PenDevice removed: {} ({})", penDevice, providerConstructor);
		}
		
	}
	
	
	/** 
	 * PenOwner implementation for JavaFX.
	 * <p>
	 * This doesn't bother checking clips/turning off things when outside the active window...
	 * 
	 * @author Pete Bankhead
	 *
	 */
	private static class PenOwnerFX implements PenOwner {
		
		private PenManagerHandle penManagerHandle;
		private PenClip penClip = new QuPathViewerPenClip();

		@Override
		public PenClip getPenClip() {
			return penClip;
		}

		@Override
		public Collection<Constructor> getPenProviderConstructors() {
			return Arrays.asList(
					 new PenProvider.Constructor[]{
						 // new SystemProvider.Constructor(), //Does not work because it needs a java.awt.Component to register the MouseListener
						 new XinputProvider.Constructor(),
						 new WintabProvider.Constructor(),
						 new CocoaProvider.Constructor()
					 }
				 );
		}

		@Override
		public boolean enforceSinglePenManager() {
			return true;
		}

		@Override
		public Object evalPenEventTag(PenEvent event) {
			if (penManagerHandle == null)
				return null;
			return penManagerHandle.retrievePenEventTag(event);
		}

		@Override
		public boolean isDraggingOut() {
			return false;
		}

		@Override
		public void setPenManagerHandle(PenManagerHandle handle) {
			this.penManagerHandle = handle;
			penManagerHandle.setPenManagerPaused(false);
		}
		
	}
	
	/**
	 * Best to accept any location on screen - attempts to filter by viewer 
	 * where not entirely successful when working with multiple viewer.
	 */
	static class QuPathViewerPenClip implements PenClip {
		
		@Override
		public void evalLocationOnScreen(Point locationOnScreen) {
			locationOnScreen.x = 0;
			locationOnScreen.y = 0;
		}

		@Override
		public boolean contains(Float point) {
			return true;
		}
		
	}

}