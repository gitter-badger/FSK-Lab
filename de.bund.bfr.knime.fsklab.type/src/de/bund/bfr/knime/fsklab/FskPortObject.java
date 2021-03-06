package de.bund.bfr.knime.fsklab;
/*
 ***************************************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
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

import java.awt.BorderLayout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emfjson.jackson.module.EMFModule;
import org.emfjson.jackson.resource.JsonResourceFactory;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortObjectZipInputStream;
import org.knime.core.node.port.PortObjectZipOutputStream;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.util.FileUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.bund.bfr.knime.fsklab.nodes.common.ui.FLabel;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FPanel;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FTextField;
import de.bund.bfr.knime.fsklab.nodes.common.ui.ScriptPanel;
import de.bund.bfr.knime.fsklab.nodes.common.ui.UIUtils;
import de.bund.bfr.knime.fsklab.rakip.GenericModel;
import de.bund.bfr.knime.fsklab.rakip.RakipModule;
import de.bund.bfr.knime.fsklab.rakip.RakipUtil;
import metadata.DataBackground;
import metadata.GeneralInformation;
import metadata.MetadataFactory;
import metadata.MetadataPackage;
import metadata.MetadataTree;
import metadata.ModelMath;
import metadata.Scope;

/**
 * A port object for an FSK model port providing R scripts and model meta data.
 * 
 * @author Miguel Alba, BfR, Berlin.
 */
public class FskPortObject implements PortObject {

	/**
	 * Convenience access member for <code>new PortType(FSKPortObject.class)</code>
	 */
	public static final PortType TYPE = PortTypeRegistry.getInstance().getPortType(FskPortObject.class);
	public static final PortType TYPE_OPTIONAL = PortTypeRegistry.getInstance().getPortType(FskPortObject.class, true);

	/** Model script. */
	public String model;

	/** Visualization script. */
	public String viz;

	/** Paths to resources: plain text files and R workspace files (.rdata). */
	private String workingDirectory;

	/** Path to plot. */
	private String plot;

	/** README. */
	private final String readme;

	/**
	 * R workspace file with the results of running the model. It may be null if the
	 * model has not been run.
	 */
	public Path workspace;

	/** Path to spreadsheet. */
	private final String spreadsheet;

	/** List of R packages. */
	public final List<String> packages;

	private static int numOfInstances = 0;

	public int objectNum;
	public int selectedSimulationIndex = 0;
	public final List<FskSimulation> simulations = new ArrayList<>();

	// EMF metadata
	public GeneralInformation generalInformation = MetadataFactory.eINSTANCE.createGeneralInformation();
	public Scope scope = MetadataFactory.eINSTANCE.createScope();
	public DataBackground dataBackground = MetadataFactory.eINSTANCE.createDataBackground();
	public ModelMath modelMath = MetadataFactory.eINSTANCE.createModelMath();

	public FskPortObject(final String model, final String viz, final GeneralInformation generalInformation,
			final Scope scope, final DataBackground dataBackground, final ModelMath modelMath, final Path workspace,
			final List<String> packages, final String workingDirectory, final String plot, final String readme,
			final String spreadsheet) throws IOException {

		this.model = model;
		this.viz = viz;

		this.generalInformation = generalInformation;
		this.scope = scope;
		this.dataBackground = dataBackground;
		this.modelMath = modelMath;

		this.workspace = workspace;
		this.packages = packages;

		this.workingDirectory = workingDirectory;

		this.plot = plot;

		this.readme = StringUtils.defaultString(readme);
		this.spreadsheet = StringUtils.defaultString(spreadsheet);

		objectNum = numOfInstances;
		numOfInstances += 1;
	}

	public FskPortObject(final String workingDirectory, String readme, final List<String> packages) throws IOException {
		this.workingDirectory = workingDirectory;
		this.packages = packages;

		this.readme = readme;
		this.spreadsheet = "";
	}

	@Override
	public FskPortObjectSpec getSpec() {
		return FskPortObjectSpec.INSTANCE;
	}

	@Override
	public String getSummary() {
		return "FSK Object";
	}

	/**
	 * @return empty string if not set.
	 */
	public String getWorkingDirectory() {
		return workingDirectory != null ? workingDirectory : "";
	}

	/**
	 * @return empty string if not set.
	 */
	public String getPlot() {
		return plot != null ? plot : "";
	}

	public void setPlot(final String plot) {
		if (plot != null && !plot.isEmpty()) {
			this.plot = plot;
		}
	}

	/**
	 * @return empty string if not set.
	 */
	public String getReadme() {
		return readme;
	}

	/**
	 * @return empty string if not set.
	 */
	public String getSpreadsheet() {
		return StringUtils.defaultString(spreadsheet);
	}

	/**
	 * Serializer used to save this port object.
	 * 
	 * @return a {@link FskPortObject}.
	 */
	public static final class Serializer extends PortObjectSerializer<FskPortObject> {

		private static final String MODEL = "model.R";
		private static final String VIZ = "viz.R";
		private static final String META_DATA = "metaData";

		private static final String CFG_GENERAL_INFORMATION = "generalInformation";
		private static final String CFG_SCOPE = "scope";
		private static final String CFG_DATA_BACKGROUND = "dataBackground";
		private static final String CFG_MODEL_MATH = "modelMath";

		private static final String WORKSPACE = "workspace";
		private static final String SIMULATION = "simulation";
		private static final String SIMULATION_INDEX = "simulationIndex";

		private static final String WORKING_DIRECTORY = "workingDirectory";

		private static final String PLOT = "plot";

		private static final String README = "readme";

		private static final String SPREADSHEET = "spreadsheet";

		@Override
		public void savePortObject(final FskPortObject portObject, final PortObjectZipOutputStream out,
				final ExecutionMonitor exec) throws IOException, CanceledExecutionException {

			// model entry (file with model script)
			out.putNextEntry(new ZipEntry(MODEL));
			IOUtils.write(portObject.model, out, "UTF-8");
			out.closeEntry();

			// viz entry (file with visualization script)
			out.putNextEntry(new ZipEntry(VIZ));
			IOUtils.write(portObject.viz, out, "UTF-8");
			out.closeEntry();

			writeEObject(CFG_GENERAL_INFORMATION, portObject.generalInformation, out);
			writeEObject(CFG_SCOPE, portObject.scope, out);
			writeEObject(CFG_DATA_BACKGROUND, portObject.dataBackground, out);
			writeEObject(CFG_MODEL_MATH, portObject.modelMath, out);

			// workspace entry
			if (portObject.workspace != null) {
				out.putNextEntry(new ZipEntry(WORKSPACE));
				Files.copy(portObject.workspace, out);
				out.closeEntry();
			}

			// libraries
			if (!portObject.packages.isEmpty()) {
				out.putNextEntry(new ZipEntry("library.list"));
				IOUtils.writeLines(portObject.packages, "\n", out, StandardCharsets.UTF_8);
				out.closeEntry();
			}

			// Save working directory
			if (StringUtils.isNotEmpty(portObject.workingDirectory)) {
				out.putNextEntry(new ZipEntry(WORKING_DIRECTORY));
				IOUtils.write(portObject.workingDirectory, out, "UTF-8");
				out.closeEntry();
			}

			// Save plot
			if (StringUtils.isNotEmpty(portObject.plot)) {
				out.putNextEntry(new ZipEntry(PLOT));
				IOUtils.write(portObject.plot, out, "UTF-8");
				out.closeEntry();
			}

			// Save README
			if (StringUtils.isNotEmpty(portObject.readme)) {
				out.putNextEntry(new ZipEntry(README));
				IOUtils.write(portObject.readme, out, "UTF-8");
				out.closeEntry();
			}

			// Save spreadsheet
			if (StringUtils.isNotEmpty(portObject.spreadsheet)) {
				out.putNextEntry(new ZipEntry(SPREADSHEET));
				IOUtils.write(portObject.spreadsheet, out, "UTF-8");
				out.closeEntry();
			}

			// Save simulations
			if (!portObject.simulations.isEmpty()) {
				out.putNextEntry(new ZipEntry(SIMULATION));

				try {
					ObjectOutputStream oos = new ObjectOutputStream(out);
					oos.writeObject(portObject.simulations);
				} catch (IOException exception) {
					// TODO: deal with exception
				}
				out.closeEntry();
			}

			// Save selected simulation index
			out.putNextEntry(new ZipEntry(SIMULATION_INDEX));

			try {
				ObjectOutputStream oos = new ObjectOutputStream(out);
				oos.writeObject(portObject.selectedSimulationIndex);
			} catch (IOException exception) {
				// TODO: deal with exception
			}
			out.closeEntry();

			out.close();
		}

		@Override
		@SuppressWarnings("unchecked")
		public FskPortObject loadPortObject(PortObjectZipInputStream in, PortObjectSpec spec, ExecutionMonitor exec)
				throws IOException, CanceledExecutionException {

			String modelScript = "";
			String visualizationScript = "";

			GeneralInformation generalInformation = MetadataFactory.eINSTANCE.createGeneralInformation();
			Scope scope = MetadataFactory.eINSTANCE.createScope();
			DataBackground dataBackground = MetadataFactory.eINSTANCE.createDataBackground();
			ModelMath modelMath = MetadataFactory.eINSTANCE.createModelMath();

			Path workspacePath = FileUtil.createTempFile("workspace", ".r").toPath();
			List<String> packages = new ArrayList<>();

			String workingDirectory = ""; // Empty string if not set

			String plot = ""; // Empty string if not set
			String readme = ""; // Empty string if not set
			String spreadsheet = ""; // Empty string if not set

			List<FskSimulation> simulations = new ArrayList<>();
			int selectedSimulationIndex = 0;

			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				String entryName = entry.getName();

				if (entryName.equals(MODEL)) {
					modelScript = IOUtils.toString(in, "UTF-8");
				} else if (entryName.equals(VIZ)) {
					visualizationScript = IOUtils.toString(in, "UTF-8");
				}

				// If found old deprecated metadata, restore it and convert it to new EMF
				// metadata
				else if (entryName.equals(META_DATA)) {

					final String metaDataAsString = IOUtils.toString(in, "UTF-8");
					ObjectMapper mapper = new ObjectMapper().registerModule(new RakipModule());
					GenericModel genericModel = mapper.readValue(metaDataAsString, GenericModel.class);

					generalInformation = RakipUtil.convert(genericModel.generalInformation);
					scope = RakipUtil.convert(genericModel.scope);
					dataBackground = RakipUtil.convert(genericModel.dataBackground);
					modelMath = RakipUtil.convert(genericModel.modelMath);
				}

				else if (entryName.equals(CFG_GENERAL_INFORMATION)) {
					generalInformation = readEObject(in, GeneralInformation.class);
				} else if (entryName.equals(CFG_SCOPE)) {
					scope = readEObject(in, Scope.class);
				} else if (entryName.equals(CFG_DATA_BACKGROUND)) {
					dataBackground = readEObject(in, DataBackground.class);
				} else if (entryName.equals(CFG_MODEL_MATH)) {
					modelMath = readEObject(in, ModelMath.class);
				} else if (entryName.equals(WORKSPACE)) {
					Files.copy(in, workspacePath, StandardCopyOption.REPLACE_EXISTING);
				} else if (entryName.equals("library.list")) {
					packages = IOUtils.readLines(in, "UTF-8");
				} else if (entryName.equals(WORKING_DIRECTORY)) {
					workingDirectory = IOUtils.toString(in, "UTF-8");
				} else if (entryName.equals(PLOT)) {
					plot = IOUtils.toString(in, "UTF-8");
				} else if (entryName.equals(README)) {
					readme = IOUtils.toString(in, "UTF-8");
				} else if (entryName.equals(SPREADSHEET)) {
					spreadsheet = IOUtils.toString(in, "UTF-8");
				} else if (entryName.equals(SIMULATION)) {
					try {
						ObjectInputStream ois = new ObjectInputStream(in);
						simulations = ((List<FskSimulation>) ois.readObject());
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}

				else if (entryName.equals(SIMULATION_INDEX)) {
					ObjectInputStream ois = new ObjectInputStream(in);
					try {
						selectedSimulationIndex = ((Integer) ois.readObject()).intValue();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			in.close();

			// Check the existence of the working directory and create one if it's not
			// available locally.
			if (!workingDirectory.isEmpty()) {
				URL url = FileUtil.toURL(workingDirectory);
				try {
					Path localPath = FileUtil.resolveToPath(url);
					if (!Files.exists(localPath)) {
						NodeContext nodeContext = NodeContext.getContext();
						WorkflowManager wfm = nodeContext.getWorkflowManager();
						WorkflowContext workflowContext = wfm.getContext();

						// get the location of the current workflow to create the working directory in
						// it and use the name with current reader node id for the prefix of the working
						// directory name
						File currentWorkingDirectory = new File(workflowContext.getCurrentLocation(),
								nodeContext.getNodeContainer().getNameWithID().toString().replaceAll("\\W", "")
										.replace(" ", "") + "_" + "workingDirectory"
										+ new AtomicLong((int) (100000 * Math.random())).getAndIncrement());
						if (!currentWorkingDirectory.exists()) {
							currentWorkingDirectory.mkdir();
							workingDirectory = currentWorkingDirectory.toString();
						}
					}
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			final FskPortObject portObj = new FskPortObject(modelScript, visualizationScript, generalInformation, scope,
					dataBackground, modelMath, workspacePath, packages, workingDirectory, plot, readme, spreadsheet);

			if (!simulations.isEmpty()) {
				portObj.simulations.addAll(simulations);
			}
			portObj.selectedSimulationIndex = selectedSimulationIndex;
			return portObj;
		}

		@SuppressWarnings("unchecked")
		private <T> T readEObject(PortObjectZipInputStream zipStream, Class<T> valueType) throws IOException {
			final ResourceSet resourceSet = new ResourceSetImpl();
			String jsonStr = IOUtils.toString(zipStream, "UTF-8");

			ObjectMapper mapper = EMFModule.setupDefaultMapper();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
					.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new JsonResourceFactory(mapper));
			resourceSet.getPackageRegistry().put(MetadataPackage.eINSTANCE.getNsURI(), MetadataPackage.eINSTANCE);

			Resource resource = resourceSet.createResource(URI.createURI("*.extension"));
			InputStream inStream = new ByteArrayInputStream(jsonStr.getBytes(StandardCharsets.UTF_8));
			resource.load(inStream, null);

			return (T) resource.getContents().get(0);
		}

		/**
		 * Create {@link ZipEntry} with Json string representing a metadata class.
		 * 
		 * @throws IOException
		 */
		private static <T extends EObject> void writeEObject(String entryName, T value, PortObjectZipOutputStream out)
				throws IOException {

			out.putNextEntry(new ZipEntry(entryName));

			ObjectMapper mapper = EMFModule.setupDefaultMapper();
			String jsonStr = mapper.writeValueAsString(value);
			IOUtils.write(jsonStr, out, "UTF-8");

			out.closeEntry();
		}
	}

	@Override
	public JComponent[] getViews() {
		JPanel modelScriptPanel = new ScriptPanel("Model script", model, false);
		JPanel vizScriptPanel = new ScriptPanel("Visualization script", viz, false);

		JTree tree = MetadataTree.createTree(generalInformation, scope, dataBackground, modelMath);
		final JScrollPane metaDataPane = new JScrollPane(tree);
		metaDataPane.setName("Meta data");

		final JPanel librariesPanel = UIUtils.createLibrariesPanel(packages);

		JPanel simulationsPanel = new SimulationsPanel();

		// Readme
		JTextArea readmeArea = new JTextArea(readme);
		readmeArea.setEnabled(false);

		JPanel readmePanel = new JPanel(new BorderLayout());
		readmePanel.setName("README");
		readmePanel.add(new JScrollPane(readmeArea));

		return new JComponent[] { modelScriptPanel, vizScriptPanel, metaDataPane, librariesPanel, simulationsPanel,
				readmePanel };
	}

	private class SimulationsPanel extends FPanel {

		private static final long serialVersionUID = -4887698302872695689L;

		private JScrollPane parametersPane;

		private final ScriptPanel scriptPanel;
		private final FPanel simulationPanel;

		public SimulationsPanel() {

			// Panel to show parameters (show initially the simulation 0)
			FskSimulation defaultSimulation = simulations.get(0);
			JPanel formPanel = createFormPane(defaultSimulation);
			parametersPane = new JScrollPane(formPanel);

			// Panel to show preview of generated script out of parameters
			String previewScript = buildParameterScript(defaultSimulation);
			scriptPanel = new ScriptPanel("Preview", previewScript, false);

			simulationPanel = new FPanel();

			createUI();
		}

		private void createUI() {

			simulationPanel.setLayout(new BoxLayout(simulationPanel, BoxLayout.Y_AXIS));
			simulationPanel.add(parametersPane);
			simulationPanel.add(UIUtils.createTitledPanel(scriptPanel, "Preview script"));

			// Panel to select simulation
			String[] simulationNames = simulations.stream().map(FskSimulation::getName).toArray(String[]::new);
			JList<String> list = new JList<>(simulationNames);
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent e) {

					// Get selected simulation
					int selectedIndex = list.getSelectedIndex();
					if (selectedIndex != -1) {

						// Update parameters panel
						simulationPanel.remove(parametersPane);

						FskSimulation selectedSimulation = simulations.get(selectedIndex);
						JPanel formPanel = createFormPane(selectedSimulation);

						parametersPane = new JScrollPane(formPanel);
						simulationPanel.add(parametersPane, 0);

						revalidate();
						repaint();

						// Update previewPanel
						String previewScript = buildParameterScript(selectedSimulation);
						scriptPanel.setText(previewScript);
					}
				}
			});
			list.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			JScrollPane browsePanel = new JScrollPane(list);

			// Build simulations panel
			setLayout(new BorderLayout());
			setName("Simulations");
			add(browsePanel, BorderLayout.WEST);
			add(simulationPanel, BorderLayout.CENTER);
		}

		private JPanel createFormPane(FskSimulation simulation) {

			List<FLabel> nameLabels = new ArrayList<>(simulations.size());
			List<JComponent> valueLabels = new ArrayList<>(simulations.size());
			for (Map.Entry<String, String> entry : simulation.getParameters().entrySet()) {
				nameLabels.add(new FLabel(entry.getKey()));

				FTextField field = new FTextField();
				field.setText(entry.getValue());
				field.setEditable(false);
				valueLabels.add(field);
			}

			FPanel formPanel = UIUtils.createFormPanel(nameLabels, valueLabels);

			return formPanel;
		}
	}

	/** Builds string with R parameters script out. */
	private static String buildParameterScript(FskSimulation simulation) {

		String paramScript = "";
		for (Map.Entry<String, String> entry : simulation.getParameters().entrySet()) {
			String parameterName = entry.getKey();
			String parameterValue = entry.getValue();

			paramScript += parameterName + " <- " + parameterValue + "\n";
		}

		return paramScript;
	}

	@Override
	public String toString() {
		return generalInformation != null && generalInformation.getName() != null ? generalInformation.getName() : "";
	}
}
