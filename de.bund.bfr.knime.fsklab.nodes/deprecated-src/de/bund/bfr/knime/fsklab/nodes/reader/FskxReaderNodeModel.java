/*
 ***************************************************************************************************
 * Copyright (c) 2017 Federal Institute for Risk Assessment (BfR), Germany
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors: Department Biological Safety - BfR
 *************************************************************************************************
 */
package de.bund.bfr.knime.fsklab.nodes.reader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jdom2.JDOMException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NoInternalsModel;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.util.FileUtil;
import org.rosuda.REngine.REXPMismatchException;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.xml.stax.SBMLReader;

import de.bund.bfr.fskml.OmexMetaDataHandler;
import de.bund.bfr.fskml.URIS;
import de.bund.bfr.knime.fsklab.nodes.FskMetaData;
import de.bund.bfr.knime.fsklab.nodes.FskMetaData.DataType;
import de.bund.bfr.knime.fsklab.nodes.MetadataDocument;
import de.bund.bfr.knime.fsklab.nodes.Variable;
import de.bund.bfr.knime.fsklab.nodes.controller.IRController.RException;
import de.bund.bfr.knime.fsklab.nodes.controller.LibRegistry;
import de.bund.bfr.knime.fsklab.nodes.controller.RController;
import de.bund.bfr.knime.pmm.fskx.port.FskPortObject;
import de.bund.bfr.knime.pmm.fskx.port.FskPortObjectSpec;
import de.unirostock.sems.cbarchive.ArchiveEntry;
import de.unirostock.sems.cbarchive.CombineArchive;
import de.unirostock.sems.cbarchive.CombineArchiveException;
import de.unirostock.sems.cbarchive.meta.MetaDataObject;

@Deprecated
class FskxReaderNodeModel extends NoInternalsModel {

	private static final NodeLogger LOGGER = NodeLogger.getLogger(FskxReaderNodeModel.class);

	// configuration keys
	public static final String CFGKEY_FILE = "filename";

	// defaults for persistent state
	private static final String DEFAULT_FILE = "c:/temp/foo.numl";

	// defaults for persistent state
	private final SettingsModelString filename = new SettingsModelString(CFGKEY_FILE, DEFAULT_FILE);

	// Specs
	private static final FskPortObjectSpec fskSpec = FskPortObjectSpec.INSTANCE;

	// Input and output port types
	private static final PortType[] IN_TYPES = {};
	private static final PortType[] OUT_TYPES = { FskPortObject.TYPE };

	public FskxReaderNodeModel() {
		super(IN_TYPES, OUT_TYPES);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws RException
	 * @throws REXPMismatchException
	 * @throws MalformedURLException
	 * @throws InvalidPathException
	 */
	@Override
	protected PortObject[] execute(final PortObject[] inData, final ExecutionContext exec)
			throws CombineArchiveException, REXPMismatchException, RException, InvalidPathException,
			MalformedURLException {

		FskPortObject portObj = new FskPortObject();

		File archiveFile = FileUtil.getFileFromURL(FileUtil.toURL(filename.getStringValue()));
		try (CombineArchive archive = new CombineArchive(archiveFile)) {

			OmexMetaDataHandler handler = new OmexMetaDataHandler(
					archive.getDescriptions().stream().map(MetaDataObject::getXmlDescription).collect(Collectors.toList()));

			// Gets model script
			if (handler.getModelScript() != null) {
				ArchiveEntry entry = archive.getEntry(handler.getModelScript());
				portObj.model = loadScriptFromEntry(entry);
			}

			// Gets parameters script
			if (handler.getParametersScript() != null) {
				ArchiveEntry entry = archive.getEntry(handler.getParametersScript());
				portObj.param = loadScriptFromEntry(entry);
			}

			// Gets visualization script
			if (handler.getVisualizationScript() != null) {
				ArchiveEntry entry = archive.getEntry(handler.getVisualizationScript());
				portObj.viz = loadScriptFromEntry(entry);
			}

			// Gets workspace
			if (handler.getWorkspaceFile() != null) {
				ArchiveEntry entry = archive.getEntry(handler.getWorkspaceFile());
				try {
					portObj.workspace = FileUtil.createTempFile("workspace", ".r");
					entry.extractFile(portObj.workspace);
				} catch (IOException e) {
					LOGGER.warn("Workspace could not be restored. Please rerun model to obtain results.");
				}
			}

			// Gets model meta data
			if (archive.getNumEntriesWithFormat(URIS.pmf) == 1) {
				ArchiveEntry entry = archive.getEntriesWithFormat(URIS.pmf).get(0);

				try (InputStream stream = Files.newInputStream(entry.getPath(), StandardOpenOption.READ)) {
					SBMLDocument doc = new SBMLReader().readSBMLFromStream(stream);
					portObj.template = new MetadataDocument(doc).getMetaData();
				} catch (IOException | XMLStreamException e) {
					LOGGER.error("Metadata document could not be read: " + entry.getFileName());
					e.printStackTrace();
				}
			}
			portObj.template.software = FskMetaData.Software.R;

			for (int i = 0; i < portObj.template.dependentVariables.size(); i++) {
				portObj.template.dependentVariables.get(i).type = DataType.numeric;
			}

			// Gets R libraries
			// Gets library names from the zip entries in the CombineArchive
			URI libUri = null;
			if (SystemUtils.IS_OS_WINDOWS) {
				libUri = URIS.zip;
			} else if (SystemUtils.IS_OS_MAC) {
				libUri = URIS.tgz;
			} else if (SystemUtils.IS_OS_LINUX) {
				libUri = URIS.tar_gz;
			}
			List<String> libNames = archive.getEntriesWithFormat(libUri).stream()
					.map(entry -> entry.getFileName().split("\\_")[0]).collect(Collectors.toList());

			if (!libNames.isEmpty()) {

				LibRegistry libRegistry = LibRegistry.instance();

				// Filters and installs missing libraries
				List<String> missingLibs = libNames.stream().filter(lib -> !libRegistry.isInstalled(lib))
						.collect(Collectors.toList());
				if (!missingLibs.isEmpty()) {
					libRegistry.installLibs(missingLibs);
				}

				// Converts and return set of Paths returned from plugin to set
				Set<File> libs = libRegistry.getPaths(libNames).stream().map(Path::toFile).collect(Collectors.toSet());
				portObj.libs.addAll(libs);
			}
		} catch (IOException | JDOMException | ParseException e) {
			e.printStackTrace();
		}

		// Validate model
		try (RController controller = new RController()) {
			
			// Add path
			LibRegistry libRegistry = LibRegistry.instance();
			String cmd = ".libPaths(c('" + libRegistry.getInstallationPath().toString().replace("\\", "/")
					+ "', .libPaths()))";
			String[] newPaths = controller.eval(cmd).asStrings();
			
			// Validate model with parameter values from parameter script
			final String fullScriptA = portObj.param + "\n" + portObj.model;
			controller.eval(fullScriptA);

			// Validate model with parameter values from metadata
			if (!portObj.template.independentVariables.isEmpty()) {
				String newScript = "";
				
				boolean onError = false;
				for (Variable v : portObj.template.independentVariables) {
					if (StringUtils.isAnyEmpty(v.name, v.value)) {
						onError = true;
						break;
					}
					newScript += v.name + " <- " + v.value + "\n";
				}
				
				if (onError) {
					LOGGER.warn("Parameter values from metadata are not valid");
				} else {
					final String fullScriptB = newScript + "\n" + portObj.model;
					controller.eval(fullScriptB);
				}
			}
			
			// Restore .libPaths() to the original library path which happens to be
			// in the last position
			controller.eval(".libPaths()[" + newPaths.length + "]");

		} catch (RException e) {
			throw new RException("Input model is not valid", e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new PortObject[] { portObj };
	}

	private static String loadScriptFromEntry(final ArchiveEntry entry) throws IOException {
		// Create temporary file with a random name. The name does not matter,
		// since the file will be
		// deleted by KNIME itself.
		File f = FileUtil.createTempFile("script", ".r");
		entry.extractFile(f);

		// Read script from f and return script
		return FileUtils.readFileToString(f, "UTF-8");
	}

	/** {@inheritDoc} */
	@Override
	protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
		return new PortObjectSpec[] { fskSpec };
	}

	/** {@inheritDoc} */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		filename.saveSettingsTo(settings);
	}

	/** {@inheritDoc} */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		filename.loadSettingsFrom(settings);
	}

	/** {@inheritDoc} */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
		filename.validateSettings(settings);
	}

	/** {@inheritDoc} */
	@Override
	protected void reset() {
		// does nothing
	}
}