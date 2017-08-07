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
package de.bund.bfr.knime.fsklab.nodes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NoInternalsModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.util.FileUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.ExtensionsKt;

import de.bund.bfr.fskml.FskMetaDataObject;
import de.bund.bfr.fskml.FskMetaDataObject.ResourceType;
import de.bund.bfr.fskml.URIS;
import de.bund.bfr.knime.fsklab.FskPortObject;
import de.bund.bfr.knime.fsklab.FskPortObjectSpec;
import de.bund.bfr.knime.fsklab.nodes.controller.LibRegistry;
import de.bund.bfr.knime.fsklab.nodes.controller.RController;
import de.bund.bfr.rakip.generic.GenericModel;
import de.bund.bfr.rakip.generic.RakipModule;
import de.unirostock.sems.cbarchive.ArchiveEntry;
import de.unirostock.sems.cbarchive.CombineArchive;

public class ReaderNodeModel extends NoInternalsModel {

  private static final PortType[] IN_TYPES = {};
  private static final PortType[] OUT_TYPES = {FskPortObject.TYPE};

  private final SettingsModelString filename = new SettingsModelString("filename", "");

  public ReaderNodeModel() {
    super(IN_TYPES, OUT_TYPES);
  }

  @Override
  protected void saveSettingsTo(NodeSettingsWO settings) {
    filename.saveSettingsTo(settings);
  }

  @Override
  protected void loadValidatedSettingsFrom(NodeSettingsRO settings)
      throws InvalidSettingsException {
    filename.loadSettingsFrom(settings);
  }

  @Override
  protected void validateSettings(NodeSettingsRO settings) throws InvalidSettingsException {
    filename.validateSettings(settings);
  }

  @Override
  protected void reset() {}

  @Override
  protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    return new PortObjectSpec[] {FskPortObjectSpec.INSTANCE};
  }

  @Override
  protected PortObject[] execute(PortObject[] inObjects, ExecutionContext exec) throws Exception {

    final Map<String, ArchiveEntry> entriesMap = new HashMap<>();
    final ArrayList<String> libNames = new ArrayList<>();

    final File file = FileUtil.getFileFromURL(FileUtil.toURL(filename.getStringValue()));

    try (final CombineArchive archive = new CombineArchive(file)) {
      for (final ArchiveEntry entry : archive.getEntriesWithFormat(URIS.r)) {

        final FskMetaDataObject fmdo = new FskMetaDataObject(entry.getDescriptions().get(0));
        final ResourceType resourceType = fmdo.getResourceType();

        switch (resourceType) {
          case modelScript:
            entriesMap.put("modelScript", entry);
            break;
          case parametersScript:
            entriesMap.put("paramScript", entry);
            break;
          case visualizationScript:
            entriesMap.put("visualizationScript", entry);
            break;
          case workspace:
            entriesMap.put("workspace", entry);
            break;
          // RAKIP JSON metadata is not supported currently by fskml.
          default:
            break;
        }
      }

      // Gets metadata file
      final URI jsonURI = new URI("http://json.org");
      entriesMap.put("metaData", archive.getEntriesWithFormat(jsonURI).get(0));

      // Gets library names
      // TODO: replace with JNA
      final URI libUri;
      if (SystemUtils.IS_OS_WINDOWS) {
        libUri = URIS.zip;
      } else if (SystemUtils.IS_OS_MAC) {
        libUri = URIS.tgz;
      } else if (SystemUtils.IS_OS_LINUX) {
        libUri = URIS.tar_gz;
      } else {
        throw new InvalidSettingsException("Unsupported platform");
      }

      for (final ArchiveEntry entry : archive.getEntriesWithFormat(libUri)) {
        final String name = entry.getFileName().split("\\_")[0];
        libNames.add(name);
      }
    }

    final String modelScript =
        entriesMap.containsKey("modelScript") ? loadScript(entriesMap.get("modelScript")) : "";
    final String paramScript =
        entriesMap.containsKey("paramScript") ? loadScript(entriesMap.get("paramScript")) : "";
    final String visualizationScript = entriesMap.containsKey("visualizationScript")
        ? loadScript(entriesMap.get("visualizationScript"))
        : "";
    final File workspaceFile =
        entriesMap.containsKey("workspace") ? toTempFile(entriesMap.get("workspace")) : null;
    if (!entriesMap.containsKey("metaData")) {
      throw new InvalidSettingsException("Missing model meta data");
    }
    final GenericModel genericModel = loadMetaData(entriesMap.get("metaData"));


    final Set<File> libFiles = new HashSet<>();
    if (!libNames.isEmpty()) {

      LibRegistry libRegistry = LibRegistry.instance();

      // Filters and installs missing libraries
      List<String> missingLibs = libNames.stream().filter(lib -> !libRegistry.isInstalled(lib))
          .collect(Collectors.toList());
      if (!missingLibs.isEmpty()) {
        libRegistry.installLibs(missingLibs);
      }

      // Converts and return set of Paths returned from plugin to set
      Set<File> libs =
          libRegistry.getPaths(libNames).stream().map(Path::toFile).collect(Collectors.toSet());
      libFiles.addAll(libs);
    }

    // validate model
    try (final RController controller = new RController()) {

      // Add path
      final LibRegistry libRegistry = LibRegistry.instance();
      final String installationPath =
          libRegistry.getInstallationPath().toString().replaceAll("\\", "/");
      final String cmd = String.format(".libPaths(c('%s', .libPaths()))", installationPath);
      final String[] newPaths = controller.eval(cmd).asStrings();

      // TODO: validate model with parameter values from parameter script

      // Restore .libPaths() to the original library path which is in the last position
      controller.eval(String.format(".libPaths()[%d]", newPaths.length));
    }

    final FskPortObject fskObj = new FskPortObject(modelScript, paramScript, visualizationScript,
        genericModel, workspaceFile, libFiles);
    return new FskPortObject[] { fskObj };
  }

  private static File toTempFile(final ArchiveEntry archiveEntry) throws IOException {
    final File file = FileUtil.createTempFile("temp", "");
    archiveEntry.extractFile(file);

    return file;
  }

  private static String loadScript(final ArchiveEntry archiveEntry) throws IOException {
    // Read script from temporary file and return script
    return FileUtils.readFileToString(toTempFile(archiveEntry), "UTF-8");
  }

  private static GenericModel loadMetaData(final ArchiveEntry archiveEntry) throws IOException {
    final File file = toTempFile(archiveEntry);
    final ObjectMapper objectMapper =
        ExtensionsKt.jacksonObjectMapper().registerModule(new RakipModule());

    return objectMapper.readValue(file, GenericModel.class);
  }
}