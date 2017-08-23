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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import javax.swing.AbstractSpinnerModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.Pair;

import com.gmail.gcolaianni5.jris.bean.Record;
import com.gmail.gcolaianni5.jris.bean.Type;
import com.toedter.calendar.JDateChooser;

import de.bund.bfr.knime.fsklab.FskPortObject;
import de.bund.bfr.knime.fsklab.nodes.ui.ScriptPanel;
import de.bund.bfr.knime.fsklab.rakip.Assay;
import de.bund.bfr.knime.fsklab.rakip.DataBackground;
import de.bund.bfr.knime.fsklab.rakip.DietaryAssessmentMethod;
import de.bund.bfr.knime.fsklab.rakip.GeneralInformation;
import de.bund.bfr.knime.fsklab.rakip.GenericModel;
import de.bund.bfr.knime.fsklab.rakip.Hazard;
import de.bund.bfr.knime.fsklab.rakip.ModelEquation;
import de.bund.bfr.knime.fsklab.rakip.Parameter;
import de.bund.bfr.knime.fsklab.rakip.PopulationGroup;
import de.bund.bfr.knime.fsklab.rakip.Product;
import de.bund.bfr.knime.fsklab.rakip.Scope;
import de.bund.bfr.knime.fsklab.rakip.StudySample;
import de.bund.bfr.knime.ui.AutoSuggestField;
import ezvcard.VCard;

public class EditorNodeDialog extends DataAwareNodeDialogPane {

  private final ScriptPanel modelScriptPanel = new ScriptPanel("Model script", "", true);
  private final ScriptPanel paramScriptPanel = new ScriptPanel("Parameters script", "", true);
  private final ScriptPanel vizScriptPanel = new ScriptPanel("Visualization script", "", true);
  private final GeneralInformationPanel generalInformationPanel = new GeneralInformationPanel();
  private final ScopePanel scopePanel = new ScopePanel();
  private final DataBackgroundPanel dataBackgroundPanel = new DataBackgroundPanel();
  // TODO: model math panel

  private EditorNodeSettings settings;

  EditorNodeDialog() {

    /*
     * Initialize settings (current values are garbage, need to be loaded from settings/input port).
     */
    settings = new EditorNodeSettings();
    settings.genericModel = new GenericModel();

    // Add ScriptPanels
    addTab(modelScriptPanel.getName(), modelScriptPanel);
    addTab(paramScriptPanel.getName(), paramScriptPanel);
    addTab(vizScriptPanel.getName(), vizScriptPanel);
    addTab("General information", new JScrollPane(generalInformationPanel));
    addTab("Scope", new JScrollPane(scopePanel));
    addTab("Data background", new JScrollPane(dataBackgroundPanel));
    // TODO: add model math panel

    updatePanels();
  }

  // Update the scripts in the ScriptPanels
  private void updatePanels() {
    modelScriptPanel.getTextArea().setText(settings.modifiedModelScript);
    paramScriptPanel.getTextArea().setText(settings.modifiedParametersScript);
    vizScriptPanel.getTextArea().setText(settings.modifiedVisualizationScript);

    generalInformationPanel.init(settings.genericModel.generalInformation);
    scopePanel.init(settings.genericModel.scope);
    dataBackgroundPanel.init(settings.genericModel.dataBackground);
    // TODO: init model math panel
  }

  // --- settings methods ---
  /** Loads settings from input port. */
  @Override
  protected void loadSettingsFrom(NodeSettingsRO settings, PortObject[] input)
      throws NotConfigurableException {

    final EditorNodeSettings editorSettings = new EditorNodeSettings();
    try {
      editorSettings.loadSettings(settings);
    } catch (InvalidSettingsException exception) {
      throw new NotConfigurableException("InvalidSettingsException", exception);
    }

    final FskPortObject inObj = (FskPortObject) input[0];

    /*
     * If input model has not changed (the original scripts stored in settings match the input
     * model).
     */
    if (Objects.equals(editorSettings.originalModelScript, inObj.model)
        && Objects.equals(editorSettings.originalParametersScript, inObj.param)
        && Objects.equals(editorSettings.originalVisualizationScript, inObj.viz)) {
      // Updates settings
      this.settings = editorSettings;
    } else {
      // Discard settings and replace them with input model
      this.settings.originalModelScript = inObj.model;
      this.settings.originalParametersScript = inObj.param;
      this.settings.originalVisualizationScript = inObj.viz;

      this.settings.modifiedModelScript = inObj.model;
      this.settings.modifiedParametersScript = inObj.param;
      this.settings.modifiedVisualizationScript = inObj.viz;

      this.settings.genericModel = inObj.genericModel;
    }

    updatePanels();
  }

  /** Loads settings from saved settings. */
  @Override
  protected void loadSettingsFrom(NodeSettingsRO settings, PortObjectSpec[] specs)
      throws NotConfigurableException {
    try {
      this.settings.loadSettings(settings);
    } catch (InvalidSettingsException exception) {
      throw new NotConfigurableException("InvalidSettingsException", exception);
    }

    updatePanels();
  }

  @Override
  protected void saveSettingsTo(NodeSettingsWO settings) throws InvalidSettingsException {

    // Save modified scripts to settings
    this.settings.modifiedModelScript = modelScriptPanel.getTextArea().getText();
    this.settings.modifiedParametersScript = paramScriptPanel.getTextArea().getText();
    this.settings.modifiedVisualizationScript = vizScriptPanel.getTextArea().getText();

    // Trim non-empty scripts
    this.settings.modifiedModelScript = StringUtils.trim(this.settings.modifiedModelScript);
    this.settings.modifiedParametersScript =
        StringUtils.trim(this.settings.modifiedParametersScript);
    this.settings.modifiedVisualizationScript =
        StringUtils.trim(this.settings.modifiedVisualizationScript);

    this.settings.genericModel.generalInformation = generalInformationPanel.get();
    this.settings.genericModel.scope = scopePanel.get();
    this.settings.genericModel.dataBackground = dataBackgroundPanel.get();
    // TODO: get model math

    this.settings.saveSettings(settings);
  }

  private static final Map<String, Set<String>> vocabs = new HashMap<>();
  static {

    try (
        final InputStream stream = EditorNodeDialog.class
            .getResourceAsStream("/FSKLab_Config_Controlled Vocabularies.xlsx");
        final XSSFWorkbook workbook = new XSSFWorkbook(stream)) {

      final List<String> sheets = Arrays.asList(

          // GeneralInformation controlled vocabularies
          "Rights", "Format", "Software", "Language", "Language written in", "Status",

          // Product controlled vocabularies
          "Product-matrix name", "Product-matrix unit", "Method of production", "Packaging",
          "Product treatment", "Country of origin", "Area of origin", "Fisheries area",

          // Hazard controlled vocabularies
          "Hazard type", "Hazard name", "Hazard unit", "Hazard ind sum", "Laboratory country",

          // PopulationGroup controlled vocabularies
          "Region", "Country",

          // DataBackground controlled vocabularies
          "Laboratory accreditation",

          // Study controlled vocabularies
          "Study Design Type", "Study Assay Measurement Type", "Study Assay Technology Type",
          "Accreditation procedure Ass.Tec", "Study Protocol Type",
          "Study Protocol Parameters Name", "Study Protocol Components Type",

          // StudySample controlled vocabularies
          "Sampling strategy", "Type of sampling program", "Sampling method", "Lot size unit",
          "Sampling point",

          // DietaryAssessmentMethod controlled vocabularies
          "Method. tool to collect data", "Food descriptors",

          // Parameter controlled vocabularies
          "Parameter classification", "Parameter unit", "Parameter type", "Parameter unit category",
          "Parameter data type", "Parameter source", "Parameter subject", "Parameter distribution");

      for (final String sheet : sheets) {
        final Set<String> vocabulary = readVocabFromSheet(workbook, sheet);
        vocabs.put(sheet, vocabulary);
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static NodeLogger LOGGER = NodeLogger.getLogger("EditNodeDialog");

  /**
   * Read controlled vocabulary from spreadsheet.
   * 
   * @return Set with controlled vocabulary. If the sheet is not found returns empty set.
   */
  private static Set<String> readVocabFromSheet(final XSSFWorkbook workbook,
      final String sheetname) {

    final XSSFSheet sheet = workbook.getSheet(sheetname);
    if (sheet == null) {
      LOGGER.warn("Spreadsheet not found: " + sheetname);
      return Collections.emptySet();
    }

    final Set<String> vocab = new HashSet<>();
    for (Row row : sheet) {
      if (row.getRowNum() == 0)
        continue;
      final Cell cell = row.getCell(0);
      if (cell == null)
        continue;
      try {
        final String cellValue = cell.getStringCellValue();
        if (StringUtils.isNotBlank(cellValue))
          vocab.add(cellValue);
      } catch (Exception e) {
        // FIXME: A NPE is produced here ...
        // LOGGER.warning("Controlled vocabulary " + sheetname + ": wrong value " + cell);
      }
    }

    return vocab;
  }

  // TODO: this should be shared through the plugin (all the FSK nodes)
  private static final ResourceBundle bundle = ResourceBundle.getBundle("MessagesBundle");

  /**
   * Create a JLabel with no tooltip, retrieving its text from resource bundle. This is a
   * convenience method for {@link #createLabel(String, boolean)} where the property is not
   * mandatory.
   * 
   * @param textKey Key of the JLabel text in the resource bundle
   * @return JLabel
   */
  private static JLabel createLabel(final String textKey) {
    return createLabel(textKey, false);
  }

  /**
   * Create a JLabel with no tooltip, retrieving its text from resource bundle.
   * 
   * @param textKey Key of the JLabel text in the resource bundle
   * @param isMandatory Whether the property described by the JLabel is mandatory
   * @return JLabel
   */
  private static JLabel createLabel(final String textKey, final boolean isMandatory) {
    return new JLabel(bundle.getString(textKey) + (isMandatory ? "*" : ""));
  }

  /**
   * Create a JLabel retrieving text and tool tip text from resource bundle. This is a convenience
   * method for {@link #createLabel(String, String, boolean)} where the property is not mandatory.
   * 
   * @param textKey Key of the JLabel text in the resource bundle
   * @param toolTipKey Key of the tool tip text in the resource bundle
   * @return JLabel describing an optional property.
   */
  private static JLabel createLabel(final String textKey, final String toolTipKey) {
    return createLabel(textKey, toolTipKey, false);
  }

  /**
   * Create a JLabel retrieving text and tool tip text from resource bundle.
   * 
   * @param textKey Key of the JLabel text in the resource bundle
   * @param toolTipKey Key of the tool tip text in the resource bundle
   * @param isMandatory Whether the property described by the JLabel is mandatory
   * @return JLabel
   */
  private static JLabel createLabel(final String textKey, final String toolTipKey,
      final boolean isMandatory) {

    final JLabel label = new JLabel(bundle.getString(textKey) + (isMandatory ? "*" : ""));
    label.setToolTipText(bundle.getString(toolTipKey));

    return label;
  }

  private static JPanel createAdvancedPanel(final JCheckBox checkbox) {

    final JPanel panel = new JPanel();
    panel.setBackground(Color.lightGray);
    panel.add(checkbox);

    return panel;
  }

  private static JTextField createTextField() {
    return new JTextField(30);
  }

  private static JTextArea createTextArea() {
    return new JTextArea(5, 30);
  }

  /**
   * Adds a component to the end of a container. Also notifies the layout manager to add the
   * component to this container's layout using the specified constraints. This is a convenience
   * method for {@link EditorNodeDialog#add(JPanel, JComponent, int, int, int, int)} where the
   * initial grid width and height are 1.
   * 
   * @param panel the panel the component is added to
   * @param comp the component to be added
   * @param gridx the initial gridx value
   * @param gridy the initial gridy value
   * 
   * @see {@link Container#add(java.awt.Component, java.lang.Object)}
   */
  private static void add(final JPanel panel, final JComponent comp, final int gridx,
      final int gridy) {
    add(panel, comp, gridx, gridy, 1, 1);
  }

  /**
   * Adds a component to the end of a container. Also notifies the layout manager to add the
   * component to this container's layout using the specified constraints. This is a convenience
   * method for {@link EditorNodeDialog#add(JPanel, JComponent, int, int, int, int) where the
   * initial grid height is 1.
   * 
   * @param panel the panel the component is added to
   * @param comp the component to be added
   * @param gridx the initial gridx value
   * @param gridy the initial gridy value
   * @param gridwidth the initial grid width
   * 
   * @see {@link Container#add(java.awt.Component, java.lang.Object)}
   */
  private static void add(final JPanel panel, final JComponent comp, final int gridx,
      final int gridy, final int gridwidth) {
    add(panel, comp, gridx, gridy, gridwidth, 1);
  }

  /**
   * Adds a component to the end of a container. Also notifies the layout manager to add the
   * component to this container's layout using the specified constraints.
   * 
   * @param panel the panel the component is added to
   * @param comp the component to be added
   * @param gridx the initial gridx value
   * @param gridy the initial gridy value
   * @param gridwidth the initial grid width
   * @param gridheight the initial grid height
   * 
   * @see {@link Container#add(java.awt.Component, java.lang.Object)}
   */
  private static void add(final JPanel panel, final JComponent comp, final int gridx,
      final int gridy, final int gridwidth, final int gridheight) {

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = gridx;
    constraints.gridy = gridy;
    constraints.gridwidth = gridwidth;
    constraints.gridheight = gridheight;
    constraints.ipadx = 10;
    constraints.ipady = 10;
    constraints.anchor = GridBagConstraints.LINE_START;

    panel.add(comp, constraints);
  }

  /** Creates a JSpinner with 5 columns. */
  private static JSpinner createSpinner(final AbstractSpinnerModel model) {

    final JSpinner spinner = new JSpinner(model);
    ((DefaultEditor) spinner.getEditor()).getTextField().setColumns(5);

    return spinner;
  }

  /** Creates a SpinnerNumberModel for integers with no limits and initial value 0. */
  private static SpinnerNumberModel createSpinnerIntegerModel() {
    return new SpinnerNumberModel(0, null, null, 1);
  }

  /** Creates a SpinnerNumberModel for real numbers with no limits and initial value. */
  private static SpinnerNumberModel createSpinnerDoubleModel() {
    return new SpinnerNumberModel(0.0, null, null, .01);
  }

  /**
   * Creates a SpinnerNumberModel for percentages (doubles) and initial value 0.0.
   *
   * Has limits 0.0 and 1.0.
   */
  private static SpinnerNumberModel createSpinnerPercentageModel() {
    return new SpinnerNumberModel(0.0, 0.0, 1.0, .01);
  }

  private class NonEditableTableModel extends DefaultTableModel {

    private static final long serialVersionUID = 8760456472042745780L;

    NonEditableTableModel() {
      super(new Object[][] {}, new String[] {"header"});
    }

    @Override
    public boolean isCellEditable(final int row, final int column) {
      return false;
    }
  }

  private class HeadlessTable extends JTable {

    private static final long serialVersionUID = -8980920067513143776L;
    private final DefaultTableCellRenderer renderer;

    HeadlessTable(final NonEditableTableModel model, final DefaultTableCellRenderer renderer) {

      super(model);
      setTableHeader(null);
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      this.renderer = renderer;
    }

    @Override
    public DefaultTableCellRenderer getCellRenderer(final int row, final int column) {
      return renderer;
    }
  }

  private class ButtonsPanel extends JPanel {

    private static final long serialVersionUID = 6605670621595008750L;
    final JButton addButton = new JButton("Add");
    final JButton modifyButton = new JButton("Modify");
    final JButton removeButton = new JButton("Remove");

    ButtonsPanel() {
      add(addButton);
      add(modifyButton);
      add(removeButton);
    }
  }

  /**
   * Shows Swing ok/cancel dialog.
   * 
   * @return the selected option. JOptionaPane.OK_OPTION or JOptionaPane.CANCEL_OPTION.
   */
  private static int showConfirmDialog(final JPanel panel, final String title) {
    return JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.OK_OPTION,
        JOptionPane.PLAIN_MESSAGE);
  }

  /** Validatable dialogs and panels. */
  private class ValidatableDialog extends JDialog {

    private static final long serialVersionUID = -6572257674130882251L;
    private final JOptionPane optionPane;

    ValidatableDialog(final ValidatablePanel panel, final String dialogTitle) {
      super((Frame) null, true);

      optionPane =
          new JOptionPane(new JScrollPane(panel), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
      setTitle(dialogTitle);

      // Handle window closing properly
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      optionPane.addPropertyChangeListener(e -> {

        if (isVisible() && e.getSource() == optionPane
            && e.getPropertyName().equals(JOptionPane.VALUE_PROPERTY)
            && optionPane.getValue() != JOptionPane.UNINITIALIZED_VALUE) {

          int value = (int) optionPane.getValue();

          if (value == JOptionPane.YES_OPTION) {
            final List<String> errors = panel.validatePanel();
            if (errors.isEmpty()) {
              dispose();
            } else {
              final String msg = String.join("\n", errors);
              JOptionPane.showMessageDialog(this, msg, "Missing fields", JOptionPane.ERROR_MESSAGE);

              // Reset the JOptionPane's value. If you don't do this, if the user presses
              // the same button next time, no property change will be fired.
              optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
            }
          } else if (value == JOptionPane.NO_OPTION) {
            dispose();
          }
        }
      });

      setContentPane(optionPane);
      pack();
      setLocationRelativeTo(null); // center dialog
      setVisible(true);
    }

    Object getValue() {
      return optionPane.getValue();
    }
  }

  abstract class ValidatablePanel extends JPanel {

    private static final long serialVersionUID = -314660860010487287L;

    ValidatablePanel() {
      super(new GridBagLayout());
    }

    abstract List<String> validatePanel();
  }

  private abstract class EditPanel<T> extends ValidatablePanel {

    private static final long serialVersionUID = 5109496284766147394L;

    abstract void init(final T t);

    abstract T get();

    /**
     * @return list of JComponents related to optional properties
     */
    abstract List<JComponent> getAdvancedComponents();

    /**
     * Hide or show the JComponents related to optional properties.
     */
    void toggleMode() {
      final List<JComponent> components = getAdvancedComponents();
      components.forEach(it -> it.setVisible(!it.isVisible()));
    }
  }

  private class EditAssayPanel extends EditPanel<Assay> {

    private static final long serialVersionUID = -1195181696127795655L;

    private final JLabel nameLabel;
    private final JTextField nameTextField;

    private final JLabel descriptionLabel;
    private final JTextArea descriptionTextArea;

    EditAssayPanel(final boolean isAdvanced) {

      nameLabel = createLabel("GM.EditAssayPanel.nameLabel", "GM.EditAssayPanel.nameTooltip", true);
      nameTextField = createTextField();

      descriptionLabel = createLabel("GM.EditAssayPanel.descriptionLabel",
          "GM.EditAssayPanel.descriptionTooltip", true);
      descriptionTextArea = createTextArea();

      final List<Pair<JLabel, JComponent>> pairs = Arrays.asList(
          new Pair<>(nameLabel, nameTextField), new Pair<>(descriptionLabel, descriptionTextArea));

      addGridComponents(this, pairs);

      // If simple mode hide advanced components
      if (!isAdvanced) {
        descriptionLabel.setVisible(false);
        descriptionTextArea.setVisible(false);
      }
    }

    @Override
    void init(final Assay assay) {
      if (assay != null) {
        nameTextField.setText(assay.name);
        descriptionTextArea.setText(assay.description);
      }
    }

    @Override
    Assay get() {

      final Assay assay = new Assay();
      assay.name = nameTextField.getText();
      assay.description = descriptionTextArea.getText();

      return assay;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>(1);
      if (!hasValidValue(nameTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditAssayPanel.nameLabel"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(descriptionLabel, descriptionTextArea);
    }
  }

  private class EditDietaryAssessmentMethodPanel extends EditPanel<DietaryAssessmentMethod> {

    private static final long serialVersionUID = -931984426171199928L;

    private final JLabel dataCollectionToolLabel;
    private final AutoSuggestField dataCollectionToolField;

    private final JLabel nonConsecutiveOneDayLabel;
    private final JTextField nonConsecutiveOneDayTextField;

    private final JLabel dietarySoftwareToolLabel;
    private final JTextField dietarySoftwareToolTextField;

    private final JLabel foodItemNumberLabel;
    private final JTextField foodItemNumberTextField;

    private final JLabel recordTypeLabel;
    private final JTextField recordTypeTextField;

    private final JLabel foodDescriptionLabel;
    private final JComboBox<String> foodDescriptionComboBox;

    EditDietaryAssessmentMethodPanel(final boolean isAdvanced) {

      dataCollectionToolLabel =
          createLabel("GM.EditDietaryAssessmentMethodPanel.dataCollectionToolLabel",
              "GM.EditDietaryAssessmentMethodPanel.dataCollectionToolTooltip", true);
      dataCollectionToolField = new AutoSuggestField(10);

      nonConsecutiveOneDayLabel =
          createLabel("GM.EditDietaryAssessmentMethodPanel.nonConsecutiveOneDaysLabel",
              "GM.EditDietaryAssessmentMethodPanel.nonConsecutiveOneDaysTooltip", true);
      nonConsecutiveOneDayTextField = createTextField();

      dietarySoftwareToolLabel =
          createLabel("GM.EditDietaryAssessmentMethodPanel.dietarySoftwareToolLabel",
              "GM.EditDietaryAssessmentMethodPanel.dietarySoftwareToolTooltip");
      dietarySoftwareToolTextField = createTextField();

      foodItemNumberLabel = createLabel("GM.EditDietaryAssessmentMethodPanel.foodItemNumberLabel",
          "GM.EditDietaryAssessmentMethodPanel.foodItemNumberTooltip");
      foodItemNumberTextField = createTextField();

      recordTypeLabel = createLabel("GM.EditDietaryAssessmentMethodPanel.recordTypeLabel",
          "GM.EditDietaryAssessmentMethodPanel.recordTypeTooltip");
      recordTypeTextField = createTextField();

      foodDescriptionLabel = createLabel("GM.EditDietaryAssessmentMethodPanel.foodDescriptionLabel",
          "GM.EditDietaryAssessmentMethodPanel.foodDescriptionTooltip");
      foodDescriptionComboBox = new JComboBox<>();

      // init combo boxes
      dataCollectionToolField.setPossibleValues(vocabs.get("Method. tool to collect data"));
      vocabs.get("Food descriptors").forEach(it -> foodDescriptionComboBox.addItem(it));


      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(dataCollectionToolLabel, dataCollectionToolField),
              new Pair<>(nonConsecutiveOneDayLabel, nonConsecutiveOneDayTextField),
              new Pair<>(dietarySoftwareToolLabel, dietarySoftwareToolTextField),
              new Pair<>(foodItemNumberLabel, foodItemNumberTextField),
              new Pair<>(recordTypeLabel, recordTypeTextField),
              new Pair<>(foodDescriptionLabel, foodDescriptionComboBox));

      addGridComponents(this, pairs);

      // If simple mode hides advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(DietaryAssessmentMethod method) {
      if (method != null) {
        dataCollectionToolField.setSelectedItem(method.collectionTool);
        nonConsecutiveOneDayTextField
            .setText(Integer.toString(method.numberOfNonConsecutiveOneDay));
        dietarySoftwareToolTextField.setText(method.softwareTool);
        foodItemNumberTextField.setText(method.numberOfFoodItems.get(0));
        recordTypeTextField.setText(method.recordTypes.get(0));
        foodDescriptionComboBox.setSelectedItem(method.foodDescriptors.get(0));
      }
    }

    @Override
    DietaryAssessmentMethod get() {

      final DietaryAssessmentMethod method = new DietaryAssessmentMethod();
      method.collectionTool = (String) dataCollectionToolField.getSelectedItem();

      final String nonConsecutiveOneDayTextFieldText = nonConsecutiveOneDayTextField.getText();
      if (StringUtils.isNotBlank(nonConsecutiveOneDayTextFieldText)) {
        try {
          method.numberOfNonConsecutiveOneDay = Integer.parseInt(nonConsecutiveOneDayTextFieldText);
        } catch (final NumberFormatException exception) {
          LOGGER.warn("numberOfNonConsecutiveOneDay", exception);
        }
      }

      method.softwareTool = dietarySoftwareToolTextField.getText();
      if (foodItemNumberTextField.getText() != null) {
        method.numberOfFoodItems.add(foodItemNumberTextField.getText());
      }
      if (recordTypeTextField.getText() != null) {
        method.recordTypes.add(recordTypeTextField.getText());
      }
      for (final Object o : foodDescriptionComboBox.getSelectedObjects()) {
        method.foodDescriptors.add((String) o);
      }

      return method;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>(2);
      if (!hasValidValue(dataCollectionToolField)) {
        errors.add("Missing "
            + bundle.getString("GM.EditDietaryAssessmentMethodPanel.dataCollectionToolLabel"));
      }
      if (!hasValidValue(nonConsecutiveOneDayTextField)) {
        errors.add("Missing "
            + bundle.getString("GM.EditDietaryAssessmetnMethodPanel.nonConsecutiveOneDayLabel"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(dietarySoftwareToolLabel, dietarySoftwareToolTextField,
          foodItemNumberLabel, foodItemNumberTextField, recordTypeLabel, recordTypeTextField,
          foodDescriptionLabel, foodDescriptionComboBox);
    }
  }

  private class EditHazardPanel extends EditPanel<Hazard> {

    private static final long serialVersionUID = -1981279747311233487L;

    private final JLabel hazardTypeLabel;
    private final AutoSuggestField hazardTypeField;

    private final JLabel hazardNameLabel;
    private final AutoSuggestField hazardNameField;

    private final JLabel hazardDescriptionLabel;
    private final JTextArea hazardDescriptionTextArea;

    private final JLabel hazardUnitLabel;
    private final AutoSuggestField hazardUnitField;

    private final JLabel adverseEffectLabel;
    private final JTextField adverseEffectTextField;

    private final JLabel originLabel;
    private final JTextField originTextField;

    private final JLabel bmdLabel;
    private final JTextField bmdTextField;

    private final JLabel maxResidueLimitLabel;
    private final JTextField maxResidueLimitTextField;

    private final JLabel noObservedAdverseLabel;
    private final JTextField noObservedAdverseTextField;

    private final JLabel acceptableOperatorLabel;
    private final JTextField acceptableOperatorTextField;

    private final JLabel acuteReferenceDoseLabel;
    private final JTextField acuteReferenceDoseTextField;

    private final JLabel acceptableDailyIntakeLabel;
    private final JTextField acceptableDailyIntakeTextField;

    private final JLabel indSumLabel;
    private final AutoSuggestField indSumField;

    private final JLabel labNameLabel;
    private final JTextField labNameTextField;

    private final JLabel labCountryLabel;
    private final AutoSuggestField labCountryField;

    private final JLabel detectionLimitLabel;
    private final JTextField detectionLimitTextField;

    private final JLabel quantificationLimitLabel;
    private final JTextField quantificationLimitTextField;

    private final JLabel leftCensoredDataLabel;
    private final JTextField leftCensoredDataTextField;

    private final JLabel contaminationRangeLabel;
    private final JTextField contaminationRangeTextField;

    EditHazardPanel(final boolean isAdvanced) {

      hazardTypeLabel = createLabel("GM.EditHazardPanel.hazardTypeLabel",
          "GM.EditHazardPanel.hazardTypeTooltip", true);
      hazardTypeField = new AutoSuggestField(10);

      hazardNameLabel = createLabel("GM.EditHazardPanel.hazardNameLabel",
          "GM.EditHazardPanel.hazardNameTooltip", true);
      hazardNameField = new AutoSuggestField(10);

      hazardDescriptionLabel = createLabel("GM.EditHazardPanel.hazardDescriptionLabel",
          "GM.EditHazardPanel.hazardDescriptionTooltip");
      hazardDescriptionTextArea = createTextArea();

      hazardUnitLabel = createLabel("GM.EditHazardPanel.hazardUnitLabel",
          "GM.EditHazardPanel.hazardUnitTooltip", true);
      hazardUnitField = new AutoSuggestField(10);

      adverseEffectLabel = createLabel("GM.EditHazardPanel.adverseEffectLabel",
          "GM.EditHazardPanel.adverseEffectTooltip");
      adverseEffectTextField = createTextField();

      originLabel =
          createLabel("GM.EditHazardPanel.originLabel", "GM.EditHazardPanel.originTooltip");
      originTextField = createTextField();

      bmdLabel = createLabel("GM.EditHazardPanel.bmdLabel", "GM.EditHazardPanel.bmdTooltip");
      bmdTextField = createTextField();

      maxResidueLimitLabel = createLabel("GM.EditHazardPanel.maxResidueLimitLabel",
          "GM.EditHazardPanel.maxResidueLimitTooltip");
      maxResidueLimitTextField = createTextField();

      noObservedAdverseLabel = createLabel("GM.EditHazardPanel.noObservedAdverseLabel",
          "GM.EditHazardPanel.noObservedAdverseTooltip");
      noObservedAdverseTextField = createTextField();

      acceptableOperatorLabel = createLabel("GM.EditHazardPanel.acceptableOperatorLabel",
          "GM.EditHazardPanel.acceptableOperatorTooltip");
      acceptableOperatorTextField = createTextField();

      acuteReferenceDoseLabel = createLabel("GM.EditHazardPanel.acuteReferenceDoseLabel",
          "GM.EditHazardPanel.acuteReferenceDoseTooltip");
      acuteReferenceDoseTextField = createTextField();

      indSumLabel =
          createLabel("GM.EditHazardPanel.indSumLabel", "GM.EditHazardPanel.indSumTooltip");
      indSumField = new AutoSuggestField(10);

      acceptableDailyIntakeLabel = createLabel("GM.EditHazardPanel.acceptableDailyIntakeLabel",
          "GM.EditHazardPanel.acceptableDailyIntakeTooltip");
      acceptableDailyIntakeTextField = createTextField();

      labNameLabel =
          createLabel("GM.EditHazardPanel.labNameLabel", "GM.EditHazardPanel.labNameTooltip");
      labNameTextField = createTextField();

      labCountryLabel =
          createLabel("GM.EditHazardPanel.labCountryLabel", "GM.EditHazardPanel.labCountryTooltip");
      labCountryField = new AutoSuggestField(10);

      detectionLimitLabel = createLabel("GM.EditHazardPanel.detectionLimitLabel",
          "GM.EditHazardPanel.detectionLimitTooltip");
      detectionLimitTextField = createTextField();

      quantificationLimitLabel = createLabel("GM.EditHazardPanel.quantificationLimitLabel",
          "GM.EditHazardPanel.quantificationLimitTooltip");
      quantificationLimitTextField = createTextField();

      leftCensoredDataLabel = createLabel("GM.EditHazardPanel.leftCensoredDataLabel",
          "GM.EditHazardPanel.leftCensoredDataTooltip");
      leftCensoredDataTextField = createTextField();

      contaminationRangeLabel = createLabel("GM.EditHazardPanel.contaminationRangeLabel",
          "GM.EditHazardPanel.contaminationRangeTooltip");
      contaminationRangeTextField = createTextField();

      // Init combo boxes
      hazardTypeField.setPossibleValues(vocabs.get("Hazard type"));
      hazardNameField.setPossibleValues(vocabs.get("Hazard name"));
      hazardUnitField.setPossibleValues(vocabs.get("Hazard unit"));
      indSumField.setPossibleValues(vocabs.get("Hazard ind sum"));
      labCountryField.setPossibleValues(vocabs.get("Laboratory country"));

      // Create labels
      final List<Pair<JLabel, JComponent>> pairs = Arrays.asList(
          new Pair<>(hazardTypeLabel, hazardTypeField),
          new Pair<>(hazardNameLabel, hazardNameField),
          new Pair<>(hazardDescriptionLabel, hazardDescriptionTextArea),
          new Pair<>(hazardUnitLabel, hazardUnitField),
          new Pair<>(adverseEffectLabel, adverseEffectTextField),
          new Pair<>(originLabel, originTextField), new Pair<>(bmdLabel, bmdTextField),
          new Pair<>(maxResidueLimitLabel, maxResidueLimitTextField),
          new Pair<>(noObservedAdverseLabel, noObservedAdverseTextField),
          new Pair<>(acceptableOperatorLabel, acceptableOperatorTextField),
          new Pair<>(acuteReferenceDoseLabel, acuteReferenceDoseTextField),
          new Pair<>(indSumLabel, indSumField),
          new Pair<>(acceptableDailyIntakeLabel, acceptableDailyIntakeTextField),
          new Pair<>(labNameLabel, labNameTextField), new Pair<>(labCountryLabel, labCountryField),
          new Pair<>(detectionLimitLabel, detectionLimitTextField),
          new Pair<>(quantificationLimitLabel, quantificationLimitTextField),
          new Pair<>(leftCensoredDataLabel, leftCensoredDataTextField),
          new Pair<>(contaminationRangeLabel, contaminationRangeTextField));
      addGridComponents(this, pairs);

      // If simple mode hide advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(Hazard hazard) {
      if (hazard != null) {
        hazardTypeField.setSelectedItem(hazard.hazardType);
        hazardNameField.setSelectedItem(hazard.hazardName);
        hazardDescriptionTextArea.setText(hazard.hazardDescription);
        hazardUnitField.setSelectedItem(hazard.hazardUnit);
        adverseEffectTextField.setText(hazard.adverseEffect);
        originTextField.setText(hazard.origin);
        bmdTextField.setText(hazard.benchmarkDose);
        maxResidueLimitTextField.setText(hazard.maximumResidueLimit);
        noObservedAdverseTextField.setText(hazard.noObservedAdverse);
        acceptableOperatorTextField.setText(hazard.acceptableOperator);
        acuteReferenceDoseTextField.setText(hazard.acuteReferenceDose);
        indSumField.setSelectedItem(hazard.hazardIndSum);
        acceptableDailyIntakeTextField.setText(hazard.acceptableDailyIntake);
        labNameTextField.setText(hazard.laboratoryName);
        labCountryField.setSelectedItem(hazard.laboratoryCountry);
        detectionLimitTextField.setText(hazard.detectionLimit);
        quantificationLimitTextField.setText(hazard.quantificationLimit);
        leftCensoredDataTextField.setText(hazard.leftCensoredData);
        contaminationRangeTextField.setText(hazard.rangeOfContamination);
      }
    }

    @Override
    Hazard get() {

      final Hazard hazard = new Hazard();
      hazard.hazardType = (String) hazardTypeField.getSelectedItem();
      hazard.hazardName = (String) hazardNameField.getSelectedItem();
      hazard.hazardUnit = (String) hazardUnitField.getSelectedItem();

      hazard.hazardDescription = hazardDescriptionTextArea.getText();
      hazard.adverseEffect = adverseEffectTextField.getText();
      hazard.origin = originTextField.getText();
      hazard.benchmarkDose = bmdTextField.getText();
      hazard.maximumResidueLimit = maxResidueLimitTextField.getText();
      hazard.noObservedAdverse = noObservedAdverseTextField.getText();
      hazard.acceptableOperator = acceptableOperatorTextField.getText();
      hazard.acuteReferenceDose = acuteReferenceDoseTextField.getText();
      hazard.acceptableDailyIntake = acceptableDailyIntakeTextField.getText();
      hazard.hazardIndSum = (String) indSumField.getSelectedItem();
      hazard.laboratoryName = labNameTextField.getText();
      hazard.laboratoryCountry = (String) labCountryField.getSelectedItem();
      hazard.detectionLimit = detectionLimitTextField.getText();
      hazard.leftCensoredData = leftCensoredDataTextField.getText();
      hazard.rangeOfContamination = contaminationRangeTextField.getText();

      return hazard;
    }

    @Override
    List<String> validatePanel() {
      final List<String> errors = new ArrayList<>();
      if (!hasValidValue(hazardNameField)) {
        errors.add("Missing " + bundle.getString("GM.EditHazardPanel.hazardTypeLabel"));
      }
      if (!hasValidValue(hazardNameField)) {
        errors.add("Missing " + bundle.getString("GM.EditHazardPanel.hazardNameLabel"));
      }
      if (!hasValidValue(hazardUnitField)) {
        errors.add("Missing " + bundle.getString("GM.EditHazardPanel.hazardUnitLabel"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(hazardDescriptionLabel, hazardDescriptionTextArea, adverseEffectLabel,
          adverseEffectTextField, originLabel, originTextField, bmdLabel, bmdTextField,
          maxResidueLimitLabel, maxResidueLimitTextField, acuteReferenceDoseLabel,
          acuteReferenceDoseTextField, acceptableDailyIntakeLabel, acceptableDailyIntakeTextField,
          indSumLabel, indSumField, labNameLabel, labNameTextField, labCountryLabel,
          labCountryField, detectionLimitLabel, detectionLimitTextField, quantificationLimitLabel,
          quantificationLimitTextField, leftCensoredDataLabel, leftCensoredDataTextField,
          contaminationRangeLabel, contaminationRangeTextField);
    }
  }

  private class EditModelEquationPanel extends EditPanel<ModelEquation> {

    private static final long serialVersionUID = 3586499490386620791L;

    private final JLabel equationNameLabel;
    private final JTextField equationNameTextField;

    private final JLabel equationClassLabel;
    private final JTextField equationClassTextField;

    private final ReferencePanel referencePanel;

    private final JLabel scriptLabel;
    private final JTextArea scriptTextArea;

    EditModelEquationPanel(final ModelEquation modelEquation, final boolean isAdvanced) {

      equationNameLabel = createLabel("GM.EditModelEquationPanel.nameLabel",
          "GM.EditModelEquationPanel.nameTooltip", true);
      equationNameTextField = createTextField();

      equationClassLabel = createLabel("GM.EditModelEquationPanel.classLabel",
          "GM.EditModelEquationPanel.classTooltip");
      equationClassTextField = createTextField();

      referencePanel = new ReferencePanel(isAdvanced);

      scriptLabel = createLabel("GM.EditModelEquationPanel.scriptLabel",
          "GM.EditModelEquationPanel.scriptTooltip", true);
      scriptTextArea = createTextArea();

      EditorNodeDialog.add(this, equationNameLabel, 0, 0);
      EditorNodeDialog.add(this, equationNameTextField, 0, 1);
      EditorNodeDialog.add(this, equationClassLabel, 1, 0);
      EditorNodeDialog.add(this, equationClassTextField, 1, 1);
      EditorNodeDialog.add(this, referencePanel, 3, 0);
      EditorNodeDialog.add(this, scriptLabel, 4, 0);
      EditorNodeDialog.add(this, scriptTextArea, 4, 1);

      // If simple mode hide avanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(final ModelEquation modelEquation) {

      if (modelEquation != null) {
        equationNameTextField.setText(modelEquation.equationName);
        equationClassTextField.setText(modelEquation.equationClass);
        referencePanel.init(modelEquation.equationReference);
        scriptTextArea.setText(modelEquation.equation);
      }
    }

    @Override
    List<String> validatePanel() {
      final List<String> errors = new ArrayList<>();
      if (!hasValidValue(equationNameTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditHazardPanel.nameLabel"));
      }
      if (!hasValidValue(scriptTextArea)) {
        errors.add("Missing " + bundle.getString("GM.EditHazardPanel.scriptLabel"));
      }

      return errors;
    }

    @Override
    ModelEquation get() {
      final ModelEquation modelEquation = new ModelEquation();
      modelEquation.equationName = equationNameTextField.getText();
      modelEquation.equation = scriptTextArea.getText();
      modelEquation.equationClass = equationClassTextField.getText();
      referencePanel.refs.forEach(it -> modelEquation.equationReference.add(it));

      return modelEquation;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(equationClassLabel, equationClassTextField, referencePanel);
    }
  }

  private class EditParameterPanel extends EditPanel<Parameter> {

    private static final long serialVersionUID = 1826555468897327895L;

    private final JLabel idLabel;
    private final JTextField idTextField;

    private final JLabel classificationLabel;
    private final JComboBox<Parameter.Classification> classificationComboBox;

    private final JLabel nameLabel;
    private final JTextField nameTextField;

    private final JLabel descriptionLabel;
    private final JTextArea descriptionTextArea;

    private final JLabel typeLabel;
    private final AutoSuggestField typeField;

    private final JLabel unitLabel;
    private final AutoSuggestField unitField;

    private final JLabel unitCategoryLabel;
    private final AutoSuggestField unitCategoryField;

    private final JLabel dataTypeLabel;
    private final AutoSuggestField dataTypeField;

    private final JLabel sourceLabel;
    private final AutoSuggestField sourceField;

    private final JLabel subjectLabel;
    private final AutoSuggestField subjectField;

    private final JLabel distributionLabel;
    private final AutoSuggestField distributionField;

    private final JLabel valueLabel;
    private final JTextField valueTextField;

    private final JLabel referenceLabel;
    private final JTextField referenceTextField;

    private final JLabel variabilitySubjectLabel;
    private final JTextArea variabilitySubjectTextArea;

    private final JLabel applicabilityLabel;
    private final JTextArea applicabilityTextArea;

    private final JLabel errorLabel;
    private final JSpinner errorSpinner;

    private SpinnerNumberModel errorSpinnerModel;

    public EditParameterPanel(final boolean isAdvanced) {

      idLabel =
          createLabel("GM.EditParameterPanel.idLabel", "GM.EditParameterPanel.idTooltip", true);
      idTextField = createTextField();

      classificationLabel = createLabel("GM.EditParameterPanel.classificationLabel",
          "GM.EditParameterPanel.classificationTooltip", true);
      classificationComboBox = new JComboBox<>(Parameter.Classification.values());

      nameLabel = createLabel("GM.EditParameterPanel.parameterNameLabel",
          "GM.EditParameterPanel.parameterNameTooltip", true);
      nameTextField = createTextField();

      descriptionLabel = createLabel("GM.EditParameterPanel.descriptionLabel",
          "GM.EditParameterPanel.descriptionTooltip");
      descriptionTextArea = createTextArea();

      typeLabel =
          createLabel("GM.EditParameterPanel.typeLabel", "GM.EditParameterPanel.typeTooltip");
      typeField = new AutoSuggestField(10);

      unitLabel =
          createLabel("GM.EditParameterPanel.unitLabel", "GM.EditParameterPanel.unitTooltip", true);
      unitField = new AutoSuggestField(10);

      unitCategoryLabel = createLabel("GM.EditParameterPanel.unitCategoryLabel",
          "GM.EditParameterPanel.unitCategoryTooltip", true);
      unitCategoryField = new AutoSuggestField(10);

      dataTypeLabel = createLabel("GM.EditParameterPanel.dataTypeLabel",
          "GM.EditParameterPanel.dataTypeTooltip", true);
      dataTypeField = new AutoSuggestField(10);

      sourceLabel =
          createLabel("GM.EditParameterPanel.sourceLabel", "GM.EditParameterPanel.sourceTooltip");
      sourceField = new AutoSuggestField(10);

      subjectLabel =
          createLabel("GM.EditParameterPanel.subjectLabel", "GM.EditParameterPanel.subjectTooltip");
      subjectField = new AutoSuggestField(10);

      distributionLabel = createLabel("GM.EditParameterPanel.distributionLabel",
          "GM.EditParameterPanel.distributionTooltip");
      distributionField = new AutoSuggestField(10);

      valueLabel =
          createLabel("GM.EditParameterPanel.valueLabel", "GM.EditParameterPanel.valueTooltip");
      valueTextField = createTextField();

      referenceLabel = createLabel("GM.EditParameterPanel.referenceLabel",
          "GM.EditParameterPanel.referenceTooltip");
      referenceTextField = createTextField();

      variabilitySubjectLabel = createLabel("GM.EditParameterPanel.variabilitySubjectLabel",
          "GM.EditParameterPanel.variabilitySubjectTooltip");
      variabilitySubjectTextArea = createTextArea();

      applicabilityLabel = createLabel("GM.EditParameterPanel.applicabilityLabel",
          "GM.EditParameterPanel.applicabilityTooltip");
      applicabilityTextArea = createTextArea();

      errorLabel =
          createLabel("GM.EditParameterPanel.errorLabel", "GM.EditParameterPanel.errorTooltip");
      errorSpinnerModel = createSpinnerDoubleModel();
      errorSpinner = createSpinner(errorSpinnerModel);

      // init combo boxes
      typeField.setPossibleValues(vocabs.get("Parameter type"));
      unitField.setPossibleValues(vocabs.get("Parameter unit"));
      unitCategoryField.setPossibleValues(vocabs.get("Parameter unit category"));
      dataTypeField.setPossibleValues(vocabs.get("Parameter data type"));
      sourceField.setPossibleValues(vocabs.get("Parameter source"));
      subjectField.setPossibleValues(vocabs.get("Parameter subject"));
      distributionField.setPossibleValues(vocabs.get("Parameter distribution"));

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs = Arrays.asList(new Pair<>(idLabel, idTextField),
          new Pair<>(classificationLabel, classificationComboBox),
          new Pair<>(nameLabel, nameTextField), new Pair<>(descriptionLabel, descriptionTextArea),
          new Pair<>(typeLabel, typeField), new Pair<>(unitLabel, unitField),
          new Pair<>(unitCategoryLabel, unitCategoryField),
          new Pair<>(dataTypeLabel, dataTypeField), new Pair<>(sourceLabel, sourceField),
          new Pair<>(subjectLabel, subjectField), new Pair<>(distributionLabel, distributionField),
          new Pair<>(valueLabel, valueTextField), new Pair<>(referenceLabel, referenceTextField),
          new Pair<>(variabilitySubjectLabel, variabilitySubjectTextArea),
          new Pair<>(applicabilityLabel, applicabilityTextArea),
          new Pair<>(errorLabel, errorSpinner));
      addGridComponents(this, pairs);

      // If simple mode hide advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(Parameter t) {
      if (t != null) {
        idTextField.setText(t.id);
        classificationComboBox.setSelectedItem(t.classification);
        nameTextField.setText(t.name);
        descriptionTextArea.setText(t.description);
        // TODO: typeField
        unitField.setSelectedItem(t.unit);
        unitCategoryField.setSelectedItem(t.unitCategory);
        dataTypeField.setSelectedItem(t.dataType);
        sourceField.setSelectedItem(t.dataType);
        subjectField.setSelectedItem(t.subject);
        distributionField.setSelectedItem(t.distribution);
        valueTextField.setText(t.value);
        referenceTextField.setText(t.reference);
        variabilitySubjectTextArea.setText(t.variabilitySubject);
        // TODO: fix model applicability
        errorSpinnerModel.setValue(t.error);
      }
    }

    @Override
    Parameter get() {

      final Parameter param = new Parameter();
      param.id = idTextField.getText();
      param.classification = (Parameter.Classification) classificationComboBox.getSelectedItem();
      param.name = nameTextField.getText();
      param.unit = (String) unitField.getSelectedItem();
      param.unitCategory = (String) unitCategoryField.getSelectedItem();
      param.dataType = (String) dataTypeField.getSelectedItem();
      // TODO: model applicability

      param.description = descriptionTextArea.getText();
      param.source = (String) sourceField.getSelectedItem();
      param.subject = (String) subjectField.getSelectedItem();
      param.distribution = (String) distributionField.getSelectedItem();
      param.value = valueTextField.getText();
      param.reference = referenceTextField.getText();
      param.variabilitySubject = variabilitySubjectTextArea.getText();
      param.error = errorSpinnerModel.getNumber().doubleValue();

      return param;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>();
      if (!hasValidValue(idTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditParameterPanel.idLabel"));
      }
      if (classificationComboBox.getSelectedIndex() == -1) {
        errors.add("Missing " + bundle.getString("GM.EditParameterPanel.classificationLabel"));
      }
      if (!hasValidValue(nameTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditParameterPanel.nameLabel"));
      }
      if (!hasValidValue(unitField)) {
        errors.add("Missing " + bundle.getString("GM.EditParameterPanel.unitLabel"));
      }
      if (!hasValidValue(unitCategoryField)) {
        errors.add("Missing " + bundle.getString("GM.EditParameterPanel.unitCategoryLabel"));
      }
      if (!hasValidValue(dataTypeField)) {
        errors.add("Missing " + bundle.getString("GM.EditParametersPanel.dataTypeLabel"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(descriptionLabel, descriptionTextArea, typeLabel, typeField, sourceLabel,
          sourceField, subjectLabel, subjectField, distributionLabel, distributionField, valueLabel,
          valueTextField, referenceLabel, referenceTextField, variabilitySubjectLabel,
          variabilitySubjectTextArea, applicabilityLabel, applicabilityTextArea, errorLabel,
          errorSpinner);
    }
  }

  private class EditPopulationGroupPanel extends EditPanel<PopulationGroup> {

    private static final long serialVersionUID = -4520186348489618333L;

    private final JLabel populationNameLabel;
    private final JTextField populationNameTextField;

    private final JLabel targetPopulationLabel;
    private final JTextField targetPopulationTextField;

    private final JLabel populationSpanLabel;
    private final JTextField populationSpanTextField;

    private final JLabel populationDescriptionLabel;
    private final JTextArea populationDescriptionTextArea;

    private final JLabel populationAgeLabel;
    private final JTextField populationAgeTextField;

    private final JLabel populationGenderLabel;
    private final JTextField populationGenderTextField;

    private final JLabel bmiLabel;
    private final JTextField bmiTextField;

    private final JLabel specialDietGroupLabel;
    private final JTextField specialDietGroupTextField;

    private final JLabel patternConsumptionLabel;
    private final JTextField patternConsumptionTextField;

    private final JLabel regionLabel;
    private final JComboBox<String> regionComboBox;

    private final JLabel countryLabel;
    private final JComboBox<String> countryComboBox;

    private final JLabel riskLabel;
    private final JTextField riskTextField;

    private final JLabel seasonLabel;
    private final JTextField seasonTextField;

    public EditPopulationGroupPanel(final boolean isAdvanced) {

      populationNameLabel = createLabel("GM.EditPopulationGroupPanel.populationNameLabel",
          "GM.EditPopulationGroupPanel.populationNameTooltip", true);
      populationNameTextField = createTextField();

      targetPopulationLabel = createLabel("GM.EditPopulationGroupPanel.targetPopulationLabel",
          "GM.EditPopulationGroupPanel.targetPopulationTooltip");
      targetPopulationTextField = createTextField();

      populationSpanLabel = createLabel("GM.EditPopulationGroupPanel.populationSpanLabel",
          "GM.EditPopulationGroupPanel.populationSpanTooltip");
      populationSpanTextField = createTextField();

      populationDescriptionLabel =
          createLabel("GM.EditPopulationGroupPanel.populationDescriptionLabel",
              "GM.EditPopulationGroupPanel.populationDescriptionTooltip");
      populationDescriptionTextArea = createTextArea();

      populationAgeLabel = createLabel("GM.EditPopulationGroupPanel.populationAgeLabel",
          "GM.EditPopulationGroupPanel.populationAgeTooltip");
      populationAgeTextField = createTextField();

      populationGenderLabel = createLabel("GM.EditPopulationGroupPanel.populationGenderLabel",
          "GM.EditPopulationGroupPanel.populationGenderTooltip");
      populationGenderTextField = createTextField();

      bmiLabel = createLabel("GM.EditPopulationGroupPanel.bmiLabel",
          "GM.EditPopulationGroupPanel.bmiTooltip");
      bmiTextField = createTextField();

      specialDietGroupLabel = createLabel("GM.EditPopulationGroupPanel.specialDietGroupsLabel",
          "GM.EditPopulationGroupPanel.specialDietGroupsTooltip");
      specialDietGroupTextField = createTextField();

      patternConsumptionLabel = createLabel("GM.EditPopulationGroupPanel.patternConsumptionLabel",
          "GM.EditPopulationGroupPanel.patternConsumptionTooltip");
      patternConsumptionTextField = createTextField();

      regionLabel = createLabel("GM.EditPopulationGroupPanel.regionLabel",
          "GM.EditPopulationGroupPanel.regionTooltip");
      regionComboBox = new JComboBox<>();

      countryLabel = createLabel("GM.EditPopulationGroupPanel.countryLabel",
          "GM.EditPopulationGroupPanel.countryTooltip");
      countryComboBox = new JComboBox<>();

      riskLabel = createLabel("GM.EditPopulationGroupPanel.riskAndPopulationLabel",
          "GM.EditPopulationGroupPanel.riskAndPopulationTooltip");
      riskTextField = createTextField();

      seasonLabel = createLabel("GM.EditPopulationGroupPanel.seasonLabel",
          "GM.EditPopulationGroupPanel.seasonTooltip");
      seasonTextField = createTextField();

      // init combo boxes
      vocabs.get("Region").forEach(it -> regionComboBox.addItem(it));
      vocabs.get("Country").forEach(it -> countryComboBox.addItem(it));


      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(populationNameLabel, populationNameTextField),
              new Pair<>(targetPopulationLabel, targetPopulationTextField),
              new Pair<>(populationSpanLabel, populationSpanTextField),
              new Pair<>(populationDescriptionLabel, populationDescriptionTextArea),
              new Pair<>(populationAgeLabel, populationAgeTextField),
              new Pair<>(populationGenderLabel, populationGenderTextField),
              new Pair<>(bmiLabel, bmiTextField),
              new Pair<>(specialDietGroupLabel, specialDietGroupTextField),
              new Pair<>(patternConsumptionLabel, patternConsumptionTextField),
              new Pair<>(regionLabel, regionComboBox), new Pair<>(countryLabel, countryComboBox),
              new Pair<>(riskLabel, riskTextField), new Pair<>(seasonLabel, seasonTextField));
      addGridComponents(this, pairs);

      // If simple mode hide advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(final PopulationGroup t) {
      if (t != null) {
        populationNameTextField.setText(t.populationName);
        targetPopulationTextField.setText(t.targetPopulation);
        populationSpanTextField.setText(t.populationSpan.get(0));
        populationDescriptionTextArea.setText(t.populationDescription.get(0));
        populationAgeTextField.setText(t.populationAge.get(0));
        populationGenderTextField.setText(t.populationGender);
        bmiTextField.setText(t.bmi.get(0));
        specialDietGroupTextField.setText(t.specialDietGroups.get(0));
        patternConsumptionTextField.setText(t.patternConsumption.get(0));
        regionComboBox.setSelectedItem(t.region);
        countryComboBox.setSelectedItem(t.country);
        riskTextField.setText(t.populationRiskFactor.get(0));
        seasonTextField.setText(t.season.get(0));
      }
    }

    @Override
    PopulationGroup get() {
      final PopulationGroup populationGroup = new PopulationGroup();
      populationGroup.populationName = populationNameTextField.getText();
      populationGroup.targetPopulation = targetPopulationTextField.getText();
      populationGroup.populationSpan.add(populationSpanTextField.getText());
      populationGroup.populationDescription.add(populationDescriptionTextArea.getText());
      populationGroup.populationAge.add(populationAgeTextField.getText());
      populationGroup.populationGender = populationGenderTextField.getText();
      populationGroup.bmi.add(bmiTextField.getText());
      populationGroup.specialDietGroups.add(specialDietGroupTextField.getText());
      populationGroup.patternConsumption.add(patternConsumptionTextField.getText());
      populationGroup.region.add((String) regionComboBox.getSelectedItem());
      populationGroup.country.add((String) countryComboBox.getSelectedItem());
      populationGroup.populationRiskFactor.add(riskTextField.getText());
      populationGroup.season.add(seasonTextField.getText());

      return populationGroup;
    }

    @Override
    List<String> validatePanel() {
      final List<String> errors = new ArrayList<>(1);
      if (!hasValidValue(populationNameTextField)) {
        errors
            .add("Missing " + bundle.getString("GM.EditPopulationGroupPanel.populationNameLabel"));
      }
      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {

      return Arrays.asList(targetPopulationLabel, targetPopulationTextField, populationSpanLabel,
          populationSpanTextField, populationDescriptionLabel, populationDescriptionTextArea,
          populationAgeLabel, populationAgeTextField, bmiLabel, bmiTextField, specialDietGroupLabel,
          specialDietGroupTextField, patternConsumptionLabel, patternConsumptionTextField,
          regionLabel, regionComboBox, countryLabel, countryComboBox, riskLabel, riskTextField,
          seasonLabel, seasonTextField);
    }
  }

  private class EditProductPanel extends EditPanel<Product> {

    private static final long serialVersionUID = -7400646603919832139L;

    private final JLabel envNameLabel;
    private final AutoSuggestField envNameField;

    private final JLabel envDescriptionLabel;
    private final JTextArea envDescriptionTextArea;

    private final JLabel envUnitLabel;
    private final AutoSuggestField envUnitField;

    private final JLabel productionMethodLabel;
    private final JComboBox<String> productionMethodComboBox;

    private final JLabel packagingLabel;
    private final JComboBox<String> packagingComboBox;

    private final JLabel productTreatmentLabel;
    private final JComboBox<String> productTreatmentComboBox;

    private final JLabel originCountryLabel;
    private final AutoSuggestField originCountryField;

    private final JLabel originAreaLabel;
    private final AutoSuggestField originAreaField;

    private final JLabel fisheriesAreaLabel;
    private final AutoSuggestField fisheriesAreaField;

    private final JLabel productionDateLabel;
    private final FixedDateChooser productionDateChooser;

    private final JLabel expirationDateLabel;
    private final FixedDateChooser expirationDateChooser;

    public EditProductPanel(boolean isAdvanced) {

      envNameLabel = createLabel("GM.EditProductPanel.envNameLabel",
          "GM.EditProductPanel.envNameTooltip", true);
      envNameField = new AutoSuggestField(10);

      envDescriptionLabel = createLabel("GM.EditProductPanel.envDescriptionLabel",
          "GM.EditProductPanel.envDescriptionTooltip");
      envDescriptionTextArea = createTextArea();

      envUnitLabel = createLabel("GM.EditProductPanel.envUnitLabel",
          "GM.EditProductPanel.envUnitTooltip", true);
      envUnitField = new AutoSuggestField(10);

      productionMethodLabel = createLabel("GM.EditProductPanel.productionMethodLabel",
          "GM.EditProductPanel.productionMethodTooltip");
      productionMethodComboBox = new JComboBox<>();

      packagingLabel =
          createLabel("GM.EditProductPanel.packagingLabel", "GM.EditProductPanel.packagingTooltip");
      packagingComboBox = new JComboBox<>();

      productTreatmentLabel = createLabel("GM.EditProductPanel.productTreatmentLabel",
          "GM.EditProductPanel.productTreatmentTooltip");
      productTreatmentComboBox = new JComboBox<>();

      originCountryLabel = createLabel("GM.EditProductPanel.originCountryLabel",
          "GM.EditProductPanel.originCountryTooltip");
      originCountryField = new AutoSuggestField(10);

      originAreaLabel = createLabel("GM.EditProductPanel.originAreaLabel",
          "GM.EditProductPanel.originAreaTooltip");
      originAreaField = new AutoSuggestField(10);

      fisheriesAreaLabel = createLabel("GM.EditProductPanel.fisheriesAreaLabel",
          "GM.EditProductPanel.fisheriesAreaTooltip");
      fisheriesAreaField = new AutoSuggestField(10);

      productionDateLabel = createLabel("GM.EditProductPanel.productionDateLabel",
          "GM.EditProductPanel.productionDateTooltip");
      productionDateChooser = new FixedDateChooser();

      expirationDateChooser = new FixedDateChooser();
      expirationDateLabel = createLabel("GM.EditProductPanel.expirationDateLabel",
          "GM.EditProductPanel.expirationDateTooltip");

      // Init combo boxes
      envNameField.setPossibleValues(vocabs.get("Product-matrix name"));
      envUnitField.setPossibleValues(vocabs.get("Product-matrix unit"));
      vocabs.get("Method of production").forEach(it -> productionMethodComboBox.addItem(it));
      originCountryField.setPossibleValues(vocabs.get("Country of origin"));
      originAreaField.setPossibleValues(vocabs.get("Area of origin"));
      fisheriesAreaField.setPossibleValues(vocabs.get("Fisheries area"));

      // Create labels

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(envNameLabel, envNameField),
              new Pair<>(envDescriptionLabel, envDescriptionTextArea),
              new Pair<>(envUnitLabel, envUnitField),
              new Pair<>(productionMethodLabel, productionMethodComboBox),
              new Pair<>(packagingLabel, packagingComboBox),
              new Pair<>(productTreatmentLabel, productTreatmentComboBox),
              new Pair<>(originCountryLabel, originCountryField),
              new Pair<>(originAreaLabel, originAreaField),
              new Pair<>(fisheriesAreaLabel, fisheriesAreaField),
              new Pair<>(productionDateLabel, productionDateChooser),
              new Pair<>(expirationDateLabel, expirationDateChooser));
      addGridComponents(this, pairs);

      // If simple mode hides the advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(final Product t) {

      if (t != null) {
        envNameField.setSelectedItem(t.environmentName);
        envDescriptionTextArea.setText(t.environmentDescription);
        envUnitField.setSelectedItem(t.environmentUnit);
        // TODO: productonMethodComboBox
        // TODO: packagingComboBox
        // TODO: productTreatmentComboBox
        originCountryField.setSelectedItem(t.originCountry);
        originAreaField.setSelectedItem(t.originArea);
        fisheriesAreaField.setSelectedItem(t.fisheriesArea);
        productionDateChooser.setDate(t.productionDate);
        expirationDateChooser.setDate(t.expirationDate);
      }
    }

    @Override
    Product get() {

      final Product product = new Product();
      product.environmentName = (String) envNameField.getSelectedItem();
      product.environmentDescription = envDescriptionTextArea.getText();
      product.environmentUnit = (String) envUnitField.getSelectedItem();
      Arrays.stream(productionMethodComboBox.getSelectedObjects()).map(it -> (String) it)
          .forEach(product.productionMethod::add);
      Arrays.stream(packagingComboBox.getSelectedObjects()).map(it -> (String) it)
          .forEach(product.packaging::add);
      Arrays.stream(productTreatmentComboBox.getSelectedObjects()).map(it -> (String) it)
          .forEach(product.productTreatment::add);
      product.originCountry = (String) originCountryField.getSelectedItem();
      product.originArea = (String) originAreaField.getSelectedItem();
      product.fisheriesArea = (String) fisheriesAreaField.getSelectedItem();
      product.productionDate = productionDateChooser.getDate();
      product.expirationDate = expirationDateChooser.getDate();

      return product;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>(2);
      if (!hasValidValue(envNameField)) {
        errors.add("Missing " + bundle.getString("GM.EditProductPanel.envName"));
      }
      if (!hasValidValue(envUnitField)) {
        errors.add("Missing " + bundle.getString("GM.EditProductPanel.envUnit"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(envDescriptionLabel, envDescriptionTextArea, productionMethodLabel,
          productionMethodComboBox, packagingLabel, packagingComboBox, productTreatmentLabel,
          productTreatmentComboBox, originCountryLabel, originCountryField, originAreaLabel,
          originAreaField, fisheriesAreaLabel, fisheriesAreaField, productionDateLabel,
          productionDateChooser, expirationDateLabel, expirationDateChooser);
    }
  }

  private class EditReferencePanel extends EditPanel<Record> {

    private static final long serialVersionUID = -6874752919377124455L;

    private static final String dateFormatStr = "yyyy-MM-dd";

    private final JCheckBox isReferenceDescriptionCheckBox;

    private final JLabel typeLabel;
    private final JComboBox<Type> typeComboBox;

    private final JLabel dateLabel;
    private final FixedDateChooser dateChooser;

    private final JLabel pmidLabel;
    private final JTextField pmidTextField;

    private final JLabel doiLabel;
    private final JTextField doiTextField;

    private final JLabel authorListLabel;
    private final JTextField authorListTextField;

    private final JLabel titleLabel;
    private final JTextField titleTextField;

    private final JLabel abstractLabel;
    private final JTextArea abstractTextArea;

    private final JLabel journalLabel;
    private final JTextField journalTextField;

    private final JLabel volumeLabel;
    private final SpinnerNumberModel volumeSpinnerModel;
    private final JSpinner volumeSpinner;

    private final JLabel issueLabel;
    private final SpinnerNumberModel issueSpinnerModel;
    private final JSpinner issueSpinner;

    private final JLabel pageLabel;
    private final JTextField pageTextField;

    private final JLabel statusLabel;
    private final JTextField statusTextField;

    private final JLabel websiteLabel;
    private final JTextField websiteTextField;

    private final JLabel commentLabel;
    private final JTextArea commentTextArea;

    EditReferencePanel(final boolean isAdvanced) {

      // Create fields
      isReferenceDescriptionCheckBox = new JCheckBox("Is reference description *");

      typeLabel = createLabel("GM.EditReferencePanel.typeLabel");
      typeComboBox = new JComboBox<>();

      dateLabel = createLabel("GM.EditReferencePanel.dateLabel");
      dateChooser = new FixedDateChooser();

      pmidLabel = createLabel("GM.EditReferencePanel.pmidLabel");
      pmidTextField = createTextField();

      doiLabel = createLabel("GM.EditReferencePanel.doiLabel", true);
      doiTextField = createTextField();

      authorListLabel = createLabel("GM.EditReferencePanel.authorListLabel");
      authorListTextField = createTextField();

      titleLabel = createLabel("GM.EditReferencePanel.titleLabel", true);
      titleTextField = createTextField();

      abstractLabel = createLabel("GM.EditReferencePanel.abstractLabel");
      abstractTextArea = createTextArea();

      journalLabel = createLabel("GM.EditReferencePanel.journalLabel");
      journalTextField = createTextField();

      volumeLabel = createLabel("GM.EditReferencePanel.volumeLabel");
      // Create integer spinner model starting with 0 and taking positive ints only
      volumeSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
      volumeSpinner = createSpinner(volumeSpinnerModel);

      issueLabel = createLabel("GM.EditReferencePanel.issueLabel");
      // Create integer spinner model starting with 0 and taking positive ints only
      issueSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
      issueSpinner = createSpinner(issueSpinnerModel);

      pageLabel = createLabel("GM.EditReferencePanel.pageLabel");
      pageTextField = createTextField();

      statusLabel = createLabel("GM.EditReferencePanel.statusLabel");
      statusTextField = createTextField();

      websiteLabel = createLabel("GM.EditReferencePanel.websiteLabel");
      websiteTextField = createTextField();

      commentLabel = createLabel("GM.EditReferencePanel.commentLabel");
      commentTextArea = createTextArea();

      // Init combo boxes
      Arrays.stream(Type.values()).forEach(typeComboBox::addItem);

      // Create labels

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs = Arrays.asList(
          new Pair<>(typeLabel, typeComboBox), new Pair<>(dateLabel, dateChooser),
          new Pair<>(pmidLabel, pmidTextField), new Pair<>(doiLabel, doiTextField),
          new Pair<>(authorListLabel, authorListTextField), new Pair<>(titleLabel, titleTextField),
          new Pair<>(abstractLabel, abstractTextArea), new Pair<>(journalLabel, journalTextField),
          new Pair<>(volumeLabel, volumeSpinner), new Pair<>(issueLabel, issueSpinner),
          new Pair<>(pageLabel, pageTextField), new Pair<>(statusLabel, statusTextField),
          new Pair<>(websiteLabel, websiteTextField), new Pair<>(commentLabel, commentTextArea));

      EditorNodeDialog.add(this, isReferenceDescriptionCheckBox, 0, 0);
      for (int index = 0; index < pairs.size(); index++) {
        final Pair<JLabel, JComponent> pair = pairs.get(index);
        final JLabel label = pair.getFirst();
        final JComponent field = pair.getSecond();
        label.setLabelFor(field);

        EditorNodeDialog.add(this, label, 0, index + 1);
        EditorNodeDialog.add(this, field, 1, index + 1);
      }

      // If simple mode hide advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(final Record t) {
      if (t != null) {
        typeComboBox.setSelectedItem(t.getType());

        final String dateString = t.getDate();
        if (dateString != null) {
          try {
            final SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatStr);
            dateChooser.setDate(dateFormat.parse(dateString));
          } catch (final ParseException exception) {
            LOGGER.warn("Invalid date", exception);
          }
        }

        doiTextField.setText(t.getDoi());
        authorListTextField.setText(String.join(";", t.getAuthors()));
        titleTextField.setText(t.getTitle());
        abstractTextArea.setText(t.getAbstr());
        journalTextField.setText(t.getSecondaryTitle());

        final String volumeNumberString = t.getVolumeNumber();
        if (volumeNumberString != null) {
          try {
            int volumeNumber = Integer.parseInt(volumeNumberString);
            volumeSpinnerModel.setValue(volumeNumber);
          } catch (final NumberFormatException exception) {
          }
        }

        final Integer issueNumber = t.getIssueNumber();
        if (issueNumber != null) {
          issueSpinnerModel.setValue(issueNumber);
        }
        websiteTextField.setText(t.getWebsiteLink());
      }
    }

    @Override
    Record get() {

      final Record record = new Record();
      // TODO: isReferenceDescriptionCheckBox
      record.setType((Type) typeComboBox.getSelectedItem());

      final Date date = dateChooser.getDate();
      if (date != null) {
        record.setDate(new SimpleDateFormat(dateFormatStr).format(date));
      }

      record.setDoi(doiTextField.getText());

      final String authors = authorListTextField.getText();
      if (authors != null) {
        Arrays.stream(authors.split(";")).forEach(record::addAuthor);
      }

      record.setTitle(titleTextField.getText());
      record.setAbstr(abstractTextArea.getText());
      record.setSecondaryTitle(journalTextField.getText());

      final Number volumeNumber = volumeSpinnerModel.getNumber();
      if (volumeNumber != null) {
        record.setVolumeNumber(volumeNumber.toString());
      }

      final Number issueNumber = issueSpinnerModel.getNumber();
      if (issueNumber != null) {
        record.setIssueNumber(issueNumber.intValue());
      }

      // TODO: status
      record.setWebsiteLink(websiteTextField.getText());
      // TODO: comment

      return record;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>(2);
      if (!hasValidValue(doiTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditReferencePanel.doiLabel"));
      }
      if (!hasValidValue(titleTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditReferencePanel.titleLabel"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(typeLabel, typeComboBox, dateLabel, dateChooser, pmidLabel,
          pmidTextField, authorListLabel, authorListTextField, abstractLabel, abstractTextArea,
          journalLabel, journalTextField, volumeLabel, volumeSpinner, issueLabel, issueSpinner,
          pageLabel, pageTextField, statusLabel, statusTextField, websiteLabel, websiteTextField,
          commentLabel, commentTextArea);
    }
  }

  private class EditStudySamplePanel extends EditPanel<StudySample> {

    private static final long serialVersionUID = -4740851101237646103L;

    private final JLabel sampleNameLabel;
    private final JTextField sampleNameTextField;

    private final JLabel moisturePercentageLabel;
    private final SpinnerNumberModel moisturePercentageSpinnerModel;
    private final JSpinner moisturePercentageSpinner;

    private final JLabel fatPercentageLabel;
    private final SpinnerNumberModel fatPercentageSpinnerModel;
    private final JSpinner fatPercentageSpinner;

    private final JLabel sampleProtocolLabel;
    private final JTextField sampleProtocolTextField;

    private final JLabel samplingStrategyLabel;
    private final AutoSuggestField samplingStrategyField;

    private final JLabel samplingTypeLabel;
    private final AutoSuggestField samplingTypeField;

    private final JLabel samplingMethodLabel;
    private final AutoSuggestField samplingMethodField;

    private final JLabel samplingPlanLabel;
    private final JTextField samplingPlanTextField;

    private final JLabel samplingWeightLabel;
    private final JTextField samplingWeightTextField;

    private final JLabel samplingSizeLabel;
    private final JTextField samplingSizeTextField;

    private final JLabel lotSizeUnitLabel;
    private final AutoSuggestField lotSizeUnitField;

    private final JLabel samplingPointLabel;
    private final AutoSuggestField samplingPointField;

    public EditStudySamplePanel(final boolean isAdvanced) {

      // Create labels and fields
      sampleNameLabel = createLabel("GM.EditStudySamplePanel.sampleNameLabel",
          "GM.EditStudySamplePanel.sampleNameTooltip", true);
      sampleNameTextField = createTextField();

      moisturePercentageLabel = createLabel("GM.EditStudySamplePanel.moisturePercentageLabel",
          "GM.EditStudySamplePanel.moisturePercentageTooltip");
      moisturePercentageSpinnerModel = createSpinnerDoubleModel();
      moisturePercentageSpinner = createSpinner(moisturePercentageSpinnerModel);

      fatPercentageLabel = createLabel("GM.EditStudySamplePanel.fatPercentageLabel",
          "GM.EditStudySamplePanel.fatPercentageTooltip");
      fatPercentageSpinnerModel = createSpinnerDoubleModel();
      fatPercentageSpinner = createSpinner(fatPercentageSpinnerModel);

      sampleProtocolLabel = createLabel("GM.EditStudySamplePanel.sampleProtocolLabel",
          "GM.EditStudySamplePanel.sampleProtocolTooltip", true);
      sampleProtocolTextField = createTextField();

      samplingStrategyLabel = createLabel("GM.EditStudySamplePanel.samplingStrategyLabel",
          "GM.EditStudySamplePanel.samplingStrategyTooltip");
      samplingStrategyField = new AutoSuggestField(10);

      samplingTypeLabel = createLabel("GM.EditStudySamplePanel.samplingTypeLabel",
          "GM.EditStudySamplePanel.samplingTypeTooltip");
      samplingTypeField = new AutoSuggestField(10);

      samplingMethodLabel = createLabel("GM.EditStudySamplePanel.samplingMethodLabel",
          "GM.EditStudySamplePanel.samplingMethodTooltip");
      samplingMethodField = new AutoSuggestField(10);

      samplingPlanLabel = createLabel("GM.EditStudySamplePanel.samplingPlanLabel",
          "GM.EditStudySamplePanel.samplingPlanTooltip", true);
      samplingPlanTextField = createTextField();

      samplingWeightLabel = createLabel("GM.EditStudySamplePanel.samplingWeightLabel",
          "GM.EditStudySamplePanel.samplingWeightTooltip", true);
      samplingWeightTextField = createTextField();

      samplingSizeLabel = createLabel("GM.EditStudySamplePanel.samplingSizeLabel",
          "GM.EditStudySamplePanel.samplingSizeTooltip", true);
      samplingSizeTextField = createTextField();

      lotSizeUnitLabel = createLabel("GM.EditStudySamplePanel.lotSizeUnitLabel",
          "GM.EditStudySamplePanel.lotSizeUnitTooltip");
      lotSizeUnitField = new AutoSuggestField(10);

      samplingPointLabel = createLabel("GM.EditStudySamplePanel.samplingPointLabel",
          "GM.EditStudySamplePanel.samplingPointTooltip");
      samplingPointField = new AutoSuggestField(10);

      // Init combo boxes
      samplingStrategyField.setPossibleValues(vocabs.get("Sampling strategy"));
      samplingTypeField.setPossibleValues(vocabs.get("Type of sampling program"));
      samplingMethodField.setPossibleValues(vocabs.get("Sampling method"));
      lotSizeUnitField.setPossibleValues(vocabs.get("Lot size unit"));
      samplingPointField.setPossibleValues(vocabs.get("Sampling point"));

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(sampleNameLabel, sampleNameTextField),
              new Pair<>(moisturePercentageLabel, moisturePercentageSpinner),
              new Pair<>(fatPercentageLabel, fatPercentageSpinner),
              new Pair<>(sampleProtocolLabel, sampleProtocolTextField),
              new Pair<>(samplingStrategyLabel, samplingStrategyField),
              new Pair<>(samplingTypeLabel, samplingTypeField),
              new Pair<>(samplingMethodLabel, samplingMethodField),
              new Pair<>(samplingPlanLabel, samplingPlanTextField),
              new Pair<>(samplingWeightLabel, samplingWeightTextField),
              new Pair<>(samplingSizeLabel, samplingSizeTextField),
              new Pair<>(lotSizeUnitLabel, lotSizeUnitField),
              new Pair<>(samplingPointLabel, samplingPointField));
      addGridComponents(this, pairs);

      // If simple mode hide advanced components
      if (!isAdvanced) {
        getAdvancedComponents().forEach(it -> it.setVisible(false));
      }
    }

    @Override
    void init(final StudySample t) {
      if (t != null) {
        sampleNameTextField.setText(t.sample);
        if (t.moisturePercentage != null) {
          moisturePercentageSpinnerModel.setValue(t.moisturePercentage);
        }
        if (t.fatPercentage != null) {
          fatPercentageSpinnerModel.setValue(t.fatPercentage);
        }
        sampleProtocolTextField.setText(t.collectionProtocol);
        samplingStrategyField.setSelectedItem(t.samplingStrategy);
        samplingTypeField.setSelectedItem(t.samplingProgramType);
        samplingMethodField.setSelectedItem(t.samplingMethod);
        samplingPlanTextField.setText(t.samplingPlan);
        samplingWeightTextField.setText(t.samplingWeight);
        samplingSizeTextField.setText(t.samplingSize);
        lotSizeUnitField.setSelectedItem(t.lotSizeUnit);
        samplingPointField.setSelectedItem(t.samplingPoint);
      }
    }

    @Override
    StudySample get() {

      final StudySample studySample = new StudySample();
      studySample.sample = sampleNameTextField.getText();
      studySample.collectionProtocol = sampleProtocolTextField.getText();
      studySample.samplingPlan = samplingPlanTextField.getText();
      studySample.samplingWeight = samplingWeightTextField.getText();
      studySample.samplingSize = samplingSizeTextField.getText();

      studySample.moisturePercentage = moisturePercentageSpinnerModel.getNumber().doubleValue();
      studySample.fatPercentage = fatPercentageSpinnerModel.getNumber().doubleValue();
      studySample.samplingStrategy = (String) samplingStrategyField.getSelectedItem();
      studySample.samplingProgramType = (String) samplingTypeField.getSelectedItem();
      studySample.samplingMethod = (String) samplingMethodField.getSelectedItem();
      studySample.lotSizeUnit = (String) lotSizeUnitField.getSelectedItem();
      studySample.samplingPoint = (String) samplingPointField.getSelectedItem();

      return studySample;
    }

    @Override
    List<String> validatePanel() {

      final List<String> errors = new ArrayList<>(5);
      if (!hasValidValue(sampleNameTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditStudySamplePanel.sampleNameLabel"));
      }
      if (!hasValidValue(sampleProtocolTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditStudySamplePanel.sampleProtocolLabel"));
      }
      if (!hasValidValue(samplingPlanTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditStudySamplePanel.samplingPlanLabel"));
      }
      if (!hasValidValue(samplingWeightTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditStudySamplePanel.samplingWeightLabel"));
      }
      if (!hasValidValue(samplingSizeTextField)) {
        errors.add("Missing " + bundle.getString("GM.EditStudySamplePanel.samplingSizeTextField"));
      }

      return errors;
    }

    @Override
    List<JComponent> getAdvancedComponents() {
      return Arrays.asList(moisturePercentageLabel, moisturePercentageSpinner, fatPercentageLabel,
          fatPercentageSpinner, samplingTypeLabel, samplingTypeField, samplingMethodLabel,
          samplingMethodField, samplingPlanLabel, samplingPlanTextField, samplingWeightLabel,
          samplingWeightTextField, samplingSizeLabel, samplingSizeTextField, lotSizeUnitLabel,
          lotSizeUnitField, samplingPointLabel, samplingPointField);
    }
  }

  // Validation methods
  private static boolean hasValidValue(final JTextField textField) {
    return StringUtils.isNotBlank(textField.getText());
  }

  private static boolean hasValidValue(final JTextArea textArea) {
    return StringUtils.isNotBlank(textArea.getText());
  }

  private static boolean hasValidValue(final AutoSuggestField field) {
    final JTextField textField = (JTextField) field.getEditor().getEditorComponent();
    return StringUtils.isNotBlank(textField.getText());
  }

  // other
  private static void addGridComponents(final JPanel panel,
      final List<Pair<JLabel, JComponent>> pairs) {

    final GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.gridx = 0;
    labelConstraints.ipadx = 10;
    labelConstraints.ipady = 10;
    labelConstraints.anchor = GridBagConstraints.LINE_START;

    final GridBagConstraints fieldConstraints = new GridBagConstraints();
    fieldConstraints.gridx = 1;
    fieldConstraints.ipadx = 10;
    fieldConstraints.ipady = 10;
    fieldConstraints.anchor = GridBagConstraints.LINE_START;

    for (int index = 0; index < pairs.size(); index++) {

      final Pair<JLabel, JComponent> entry = pairs.get(index);
      final JLabel label = entry.getFirst();
      final JComponent field = entry.getSecond();
      label.setLabelFor(field);

      labelConstraints.gridy = index;
      panel.add(label, labelConstraints);

      fieldConstraints.gridy = index;
      panel.add(field, fieldConstraints);
    }
  }

  /** Fixes JDateChooser and disables the text field. */
  private class FixedDateChooser extends JDateChooser {

    private static final long serialVersionUID = 2475793638936369100L;

    FixedDateChooser() {

      // Fixes bug AP-5865
      popup.setFocusable(false);

      /*
       * Text field is disabled so that the dates are only chooseable through the calendar widget.
       * Then there is no need to validate the dates.
       */
      dateEditor.setEnabled(true);
    }
  }

  private class GeneralInformationPanel extends Box {

    private static final long serialVersionUID = 2705689661594031061L;

    private final JCheckBox advancedCheckBox;

    private final JTextField studyNameTextField;
    private final JTextField identifierTextField;
    private final CreatorPanel creatorPanel;
    private final FixedDateChooser creationDateChooser;
    private final AutoSuggestField rightsField;
    private final JCheckBox availabilityCheckBox;
    private final JTextField urlTextField;
    private final AutoSuggestField formatField;
    private final ReferencePanel referencePanel;
    private final AutoSuggestField languageField;
    private final AutoSuggestField softwareField;
    private final AutoSuggestField languageWrittenInField;
    private final AutoSuggestField statusField;
    private final JTextField objectiveTextField;
    private final JTextField descriptionTextField;

    public GeneralInformationPanel() {

      super(BoxLayout.PAGE_AXIS);

      // Create fields
      advancedCheckBox = new JCheckBox("Advanced");

      studyNameTextField = createTextField();
      identifierTextField = createTextField();
      creatorPanel = new CreatorPanel();
      creationDateChooser = new FixedDateChooser();
      rightsField = new AutoSuggestField(10);
      availabilityCheckBox = new JCheckBox();
      urlTextField = createTextField();
      formatField = new AutoSuggestField(10);
      referencePanel = new ReferencePanel(advancedCheckBox.isSelected());
      languageField = new AutoSuggestField(10);
      softwareField = new AutoSuggestField(10);
      languageWrittenInField = new AutoSuggestField(10);
      statusField = new AutoSuggestField(10);
      objectiveTextField = createTextField();
      descriptionTextField = createTextField();

      // Init combo boxes
      rightsField.setPossibleValues(vocabs.get("Rights"));
      formatField.setPossibleValues(vocabs.get("Format"));
      languageField.setPossibleValues(vocabs.get("Language"));
      softwareField.setPossibleValues(vocabs.get("Software"));
      languageWrittenInField.setPossibleValues(vocabs.get("Language written in"));
      statusField.setPossibleValues(vocabs.get("Status"));

      // Create labels
      final JLabel studyNameLabel = createLabel("GM.GeneralInformationPanel.studyNameLabel",
          "GM.GeneralInformationPanel.studyNameTooltip");
      final JLabel identifierLabel = createLabel("GM.GeneralInformationPanel.identifierLabel",
          "GM.GeneralInformationPanel.identifierTooltip");
      final JLabel creationDateLabel = createLabel("GM.GeneralInformationPanel.creationDateLabel",
          "GM.GeneralInformationPanel.creationDateTooltip");
      final JLabel rightsLabel = createLabel("GM.GeneralInformationPanel.rightsLabel",
          "GM.GeneralInformationPanel.rightsTooltip");
      final JLabel urlLabel = createLabel("GM.GeneralInformationPanel.urlLabel",
          "GM.GeneralInformationPanel.urlTooltip");
      final JLabel formatLabel = createLabel("GM.GeneralInformationPanel.formatLabel",
          "GM.GeneralInformationPanel.formatTooltip");
      final JLabel languageLabel = createLabel("GM.GeneralInformationPanel.languageLabel",
          "GM.GeneralInformationPanel.languageTooltip");
      final JLabel softwareLabel = createLabel("GM.GeneralInformationPanel.softwareLabel",
          "GM.GeneralInformationPanel.softwareTooltip");
      final JLabel languageWrittenInLabel =
          createLabel("GM.GeneralInformationPanel.languageWrittenInLabel",
              "GM.GeneralInformationPanel.languageWrittenInTooltip");
      final JLabel statusLabel = createLabel("GM.GeneralInformationPanel.statusLabel",
          "GM.GeneralInformationPanel.statusTooltip");
      final JLabel objectiveLabel = createLabel("GM.GeneralInformationPanel.objectiveLabel",
          "GM.GeneralInformationPanel.objectiveTooltip");
      final JLabel descriptionLabel = createLabel("GM.GeneralInformationPanel.descriptionLabel",
          "GM.GeneralInformationPanel.descriptionTooltip");

      // Hide initially advanced components
      final List<JComponent> advancedComponents = Arrays.asList(formatLabel, formatField,
          languageLabel, languageField, softwareLabel, softwareField, languageWrittenInLabel,
          languageWrittenInField, statusLabel, statusField, objectiveLabel, objectiveTextField,
          descriptionLabel, descriptionTextField);
      advancedComponents.forEach(it -> it.setVisible(false));

      // Build UI
      final JPanel propertiesPanel = new JPanel(new GridBagLayout());

      EditorNodeDialog.add(propertiesPanel, studyNameLabel, 0, 1);
      EditorNodeDialog.add(propertiesPanel, studyNameTextField, 1, 1, 2);

      EditorNodeDialog.add(propertiesPanel, identifierLabel, 0, 2);
      EditorNodeDialog.add(propertiesPanel, identifierTextField, 1, 2, 2);

      EditorNodeDialog.add(propertiesPanel, creatorPanel, 0, 3);

      EditorNodeDialog.add(propertiesPanel, creationDateLabel, 0, 4);
      EditorNodeDialog.add(propertiesPanel, creationDateChooser, 1, 4);

      EditorNodeDialog.add(propertiesPanel, rightsLabel, 0, 5);
      EditorNodeDialog.add(propertiesPanel, rightsField, 1, 5, 2);

      availabilityCheckBox
          .setText(bundle.getString("GM.GeneralInformationPanel.availabilityLabel"));
      availabilityCheckBox.setToolTipText("GM.GeneralInformationPanel.availabilityTooltip");
      EditorNodeDialog.add(propertiesPanel, availabilityCheckBox, 0, 6);

      EditorNodeDialog.add(propertiesPanel, urlLabel, 0, 7);
      EditorNodeDialog.add(propertiesPanel, urlTextField, 1, 7, 2);

      EditorNodeDialog.add(propertiesPanel, formatLabel, 0, 8);
      EditorNodeDialog.add(propertiesPanel, formatField, 1, 8, 2);

      EditorNodeDialog.add(propertiesPanel, referencePanel, 0, 9);

      EditorNodeDialog.add(propertiesPanel, languageLabel, 0, 10);
      EditorNodeDialog.add(propertiesPanel, languageField, 1, 10, 2);

      EditorNodeDialog.add(propertiesPanel, softwareLabel, 0, 11);
      EditorNodeDialog.add(propertiesPanel, softwareField, 1, 11);

      EditorNodeDialog.add(propertiesPanel, languageWrittenInLabel, 0, 12);
      EditorNodeDialog.add(propertiesPanel, languageWrittenInField, 1, 12);

      EditorNodeDialog.add(propertiesPanel, statusLabel, 0, 13);
      EditorNodeDialog.add(propertiesPanel, statusField, 1, 13, 2);

      EditorNodeDialog.add(propertiesPanel, objectiveLabel, 0, 14);
      EditorNodeDialog.add(propertiesPanel, objectiveTextField, 1, 14, 2);

      EditorNodeDialog.add(propertiesPanel, descriptionLabel, 0, 15);
      EditorNodeDialog.add(propertiesPanel, descriptionTextField, 1, 15, 2);

      advancedCheckBox.addItemListener(event -> {
        final boolean showAdvanced = advancedCheckBox.isSelected();
        advancedComponents.forEach(it -> it.setVisible(showAdvanced));
        referencePanel.isAdvanced = showAdvanced;
      });

      add(createAdvancedPanel(advancedCheckBox));
      add(Box.createGlue());
      add(propertiesPanel);
    }

    void init(final GeneralInformation generalInformation) {

      if (generalInformation != null) {
        studyNameTextField.setText(generalInformation.name);
        identifierTextField.setText(generalInformation.identifier);
        creatorPanel.init(generalInformation.creators);
        creationDateChooser.setDate(generalInformation.creationDate);
        rightsField.setSelectedItem(generalInformation.rights);
        availabilityCheckBox.setSelected(generalInformation.isAvailable);
        urlTextField.setText(generalInformation.url.toString());
        formatField.setSelectedItem(generalInformation.format);
        referencePanel.init(generalInformation.reference);
        languageField.setSelectedItem(generalInformation.language);
        softwareField.setSelectedItem(generalInformation.software);
        languageWrittenInField.setSelectedItem(generalInformation.languageWrittenIn);
        statusField.setSelectedItem(generalInformation.status);
        objectiveTextField.setText(generalInformation.objective);
        descriptionTextField.setText(generalInformation.description);
      }
    }

    GeneralInformation get() {

      final GeneralInformation generalInformation = new GeneralInformation();
      generalInformation.name = studyNameTextField.getText();
      generalInformation.identifier = identifierTextField.getText();
      generalInformation.creationDate = creationDateChooser.getDate();
      generalInformation.rights = (String) rightsField.getSelectedItem();
      generalInformation.isAvailable = availabilityCheckBox.isSelected();
      try {
        generalInformation.url = new URL(urlTextField.getText());
      } catch (MalformedURLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      generalInformation.creators.addAll(creatorPanel.creators);
      generalInformation.format = (String) formatField.getSelectedItem();
      generalInformation.reference.addAll(referencePanel.refs);
      generalInformation.language = (String) languageField.getSelectedItem();
      generalInformation.software = (String) softwareField.getSelectedItem();
      generalInformation.languageWrittenIn = (String) languageWrittenInField.getSelectedItem();
      generalInformation.status = (String) statusField.getSelectedItem();
      generalInformation.objective = objectiveTextField.getText();
      generalInformation.description = descriptionTextField.getText();

      return generalInformation;
    }
  }

  private class ReferencePanel extends JPanel {

    private static final long serialVersionUID = 7457092378015891750L;

    final NonEditableTableModel tableModel = new NonEditableTableModel();
    final List<Record> refs = new ArrayList<>();
    boolean isAdvanced;

    public ReferencePanel(final boolean isAdvanced) {

      super(new BorderLayout());
      setBorder(BorderFactory.createTitledBorder("References"));

      this.isAdvanced = isAdvanced;

      final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

        private static final long serialVersionUID = 8702844072231755585L;

        protected void setValue(Object value) {
          if (value == null) {
            setText("");
          } else {
            final Record record = (Record) value;

            final String firstAuthor = record.getAuthors().get(0);
            final String publicationYear = record.getPubblicationYear();
            final String title = record.getTitle();
            setText(String.format("%s_%s_%s", firstAuthor, publicationYear, title));
          }
        };
      };

      final HeadlessTable myTable = new HeadlessTable(tableModel, renderer);

      // buttons
      final ButtonsPanel buttonsPanel = new ButtonsPanel();
      buttonsPanel.addButton.addActionListener(event -> {

        final EditReferencePanel editPanel = new EditReferencePanel(this.isAdvanced);
        final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Create reference");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          tableModel.addRow(new Record[] {editPanel.get()});
        }
      });

      buttonsPanel.modifyButton.addActionListener(event -> {

        final int rowToEdit = myTable.getSelectedRow();
        if (rowToEdit != -1) {

          final Record ref = (Record) tableModel.getValueAt(rowToEdit, 0);

          final EditReferencePanel editPanel = new EditReferencePanel(this.isAdvanced);
          editPanel.init(ref);

          final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Modify reference");
          if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
            tableModel.setValueAt(editPanel.get(), rowToEdit, 0);
          }
        }
      });

      buttonsPanel.removeButton.addActionListener(event -> {
        final int rowToDelete = myTable.getSelectedRow();
        if (rowToDelete != -1) {
          tableModel.removeRow(rowToDelete);
        }
      });

      add(myTable, BorderLayout.NORTH);
      add(buttonsPanel, BorderLayout.SOUTH);
    }

    void init(final List<Record> references) {
      references.forEach(it -> tableModel.addRow(new Record[] {it}));
      refs.addAll(references);
    }
  }

  private class CreatorPanel extends JPanel {

    private static final long serialVersionUID = 3543570665869685092L;
    final NonEditableTableModel tableModel = new NonEditableTableModel();
    final List<VCard> creators = new ArrayList<>();

    public CreatorPanel() {

      super(new BorderLayout());

      setBorder(BorderFactory.createTitledBorder("Creators"));

      final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

        private static final long serialVersionUID = 1L;

        protected void setValue(Object value) {

          if (value != null) {
            final VCard creator = (VCard) value;

            final String givenName = creator.getNickname().getValues().get(0);
            final String familyName = creator.getFormattedName().getValue();
            final String contact = creator.getEmails().get(0).getValue();

            setText(String.format("%s_%s_%s", givenName, familyName, contact));
          }
        };
      };

      final JTable myTable = new HeadlessTable(tableModel, renderer);

      // buttons
      final ButtonsPanel buttonsPanel = new ButtonsPanel();
      buttonsPanel.addButton.addActionListener(event -> {

        final EditCreatorPanel editPanel = new EditCreatorPanel();
        final int result = showConfirmDialog(editPanel, "Create creator");
        if (result == JOptionPane.OK_OPTION) {
          tableModel.addRow(new VCard[] {editPanel.toVCard()});
        }
      });

      buttonsPanel.modifyButton.addActionListener(event -> {

        final int rowToEdit = myTable.getSelectedRow();
        if (rowToEdit != -1) {

          final VCard creator = (VCard) tableModel.getValueAt(rowToEdit, 0);

          final EditCreatorPanel editPanel = new EditCreatorPanel();
          editPanel.init(creator);
          final int result = showConfirmDialog(editPanel, "Modify creator");
          if (result == JOptionPane.OK_OPTION) {
            tableModel.setValueAt(editPanel.toVCard(), rowToEdit, 0);
          }
        }
      });

      buttonsPanel.removeButton.addActionListener(event -> {
        final int rowToDelete = myTable.getSelectedRow();
        if (rowToDelete != -1) {
          tableModel.removeRow(rowToDelete);
        }
      });

      add(myTable, BorderLayout.NORTH);
      add(buttonsPanel, BorderLayout.SOUTH);
    }

    void init(final List<VCard> vcards) {
      vcards.forEach(it -> tableModel.addRow(new VCard[] {it}));
      creators.addAll(vcards);
    }
  }

  private class EditCreatorPanel extends JPanel {

    private static final long serialVersionUID = 3472281253338213542L;

    private final JTextField givenNameTextField;
    private final JTextField familyNameTextField;
    private final JTextField contactTextField;

    public EditCreatorPanel() {

      super(new GridBagLayout());

      // Create fields
      givenNameTextField = createTextField();
      familyNameTextField = createTextField();
      contactTextField = createTextField();

      // Create labels
      final JLabel givenNameLabel = createLabel("GM.EditCreatorPanel.givenNameLabel");
      final JLabel familyNameLabel = createLabel("GM.EditCreatorPanel.familyNameLabel");
      final JLabel contactLabel = createLabel("GM.EditCreatorPanel.contactLabel");

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(givenNameLabel, givenNameTextField),
              new Pair<>(familyNameLabel, familyNameTextField),
              new Pair<>(contactLabel, contactTextField));
      addGridComponents(this, pairs);
    }

    void init(final VCard creator) {

      if (creator != null) {
        givenNameTextField.setText(creator.getNickname().getValues().get(0));
        familyNameTextField.setText(creator.getFormattedName().getValue());
        contactTextField.setText(creator.getEmails().get(0).getValue());
      }
    }

    VCard toVCard() {

      final VCard vCard = new VCard();

      final String givenNameText = givenNameTextField.getText();
      if (StringUtils.isNotEmpty(givenNameText)) {
        vCard.setNickname(givenNameText);
      }

      final String familyNameText = familyNameTextField.getText();
      if (StringUtils.isNotEmpty(familyNameText)) {
        vCard.setFormattedName(familyNameText);
      }

      final String contactText = contactTextField.getText();
      if (StringUtils.isNotEmpty(contactText)) {
        vCard.addEmail(contactText);
      }

      return vCard;
    }
  }

  private class ScopePanel extends Box {

    private static final long serialVersionUID = 8153319336584952056L;

    final JButton productButton;
    final JButton hazardButton;
    final JButton populationButton;
    final JTextArea commentField;
    final FixedDateChooser dateChooser;
    final AutoSuggestField regionField;
    final AutoSuggestField countryField;

    final JCheckBox advancedCheckBox;

    // TODO: advanced mode
    private final EditProductPanel editProductPanel = new EditProductPanel(false);
    // TODO: advanced mode
    private final EditHazardPanel editHazardPanel = new EditHazardPanel(false);
    // TODO: advanced mode
    private final EditPopulationGroupPanel editPopulationGroupPanel =
        new EditPopulationGroupPanel(false);

    ScopePanel() {

      super(BoxLayout.PAGE_AXIS);

      // Create fields
      productButton = new JButton();
      hazardButton = new JButton();
      populationButton = new JButton();
      commentField = createTextArea();
      dateChooser = new FixedDateChooser();
      regionField = new AutoSuggestField(10);
      countryField = new AutoSuggestField(10);

      advancedCheckBox = new JCheckBox("Advanced");

      // Init combo boxes
      regionField.setPossibleValues(vocabs.get("Region"));
      countryField.setPossibleValues(vocabs.get("Country"));

      // Build UI
      productButton.setToolTipText("Click me to add a product");
      productButton.addActionListener(event -> {
        final ValidatableDialog dlg = new ValidatableDialog(editProductPanel, "Create a product");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final Product product = editProductPanel.get();
          productButton
              .setText(String.format("%s_%s", product.environmentName, product.environmentUnit));
        }
      });

      hazardButton.setToolTipText("Click me to add a hazard");
      hazardButton.addActionListener(event -> {
        final ValidatableDialog dlg = new ValidatableDialog(editHazardPanel, "Create a hazard");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final Hazard hazard = editHazardPanel.get();
          hazardButton.setText(String.format("%s_%s", hazard.hazardName, hazard.hazardUnit));
        }
      });

      populationButton.setToolTipText("Click me to add a Population group");
      populationButton.addActionListener(event -> {
        final ValidatableDialog dlg =
            new ValidatableDialog(editPopulationGroupPanel, "Create a Population Group");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final PopulationGroup populationGroup = editPopulationGroupPanel.get();
          populationButton.setText(populationGroup.populationName);
        }
      });

      // Create labels
      final JLabel productLabel = createLabel("GM.ScopePanel.productLabel");
      final JLabel hazardLabel = createLabel("GM.ScopePanel.hazardLabel");
      final JLabel populationLabel = createLabel("GM.ScopePanel.populationGroupLabel");
      final JLabel commentLabel =
          createLabel("GM.ScopePanel.commentLabel", "GM.ScopePanel.commentTooltip");
      final JLabel temporalInformationLabel = createLabel("GM.ScopePanel.temporalInformationLabel",
          "GM.ScopePanel.temporalInformationTooltip");
      final JLabel regionLabel =
          createLabel("GM.ScopePanel.regionLabel", "GM.ScopePanel.regionTooltip");
      final JLabel countryLabel =
          createLabel("GM.ScopePanel.countryLabel", "GM.ScopePanel.countryTooltip");

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs = Arrays.asList(
          new Pair<>(productLabel, productButton), new Pair<>(hazardLabel, hazardButton),
          new Pair<>(populationLabel, populationButton), new Pair<>(commentLabel, commentField),
          new Pair<>(temporalInformationLabel, dateChooser), new Pair<>(regionLabel, regionField),
          new Pair<>(countryLabel, countryField));

      final JPanel propertiesPanel = new JPanel(new GridBagLayout());
      addGridComponents(propertiesPanel, pairs);

      // Advanced checkbox
      advancedCheckBox.addItemListener(event -> {
        // TODO: not implemented yet
        System.out.println("Dummy listener");
      });

      add(createAdvancedPanel(advancedCheckBox));
      add(Box.createGlue());
      add(propertiesPanel);
    }

    void init(final Scope scope) {
      if (scope != null) {
        editProductPanel.init(scope.product);
        editHazardPanel.init(scope.hazard);
        editPopulationGroupPanel.init(scope.populationGroup);
        if (StringUtils.isNotBlank(scope.temporalInformation)) {
          try {
            dateChooser
                .setDate(new SimpleDateFormat("yyyy-MM-dd").parse(scope.temporalInformation));
          } catch (ParseException e) {
            e.printStackTrace();
          }
        }
        if (!scope.region.isEmpty()) {
          regionField.setSelectedItem(scope.region.get(0));
        }
        if (!scope.country.isEmpty()) {
          countryField.setSelectedItem(scope.country.get(0));
        }
      }
    }

    Scope get() {
      final Scope scope = new Scope();
      scope.product = editProductPanel.get();
      scope.hazard = editHazardPanel.get();
      scope.populationGroup = editPopulationGroupPanel.get();

      final Date date = dateChooser.getDate();
      if (date != null) {
        scope.temporalInformation =
            new SimpleDateFormat(dateChooser.getDateFormatString()).format(date);
      }
      scope.region.add((String) regionField.getSelectedItem());
      scope.country.add((String) countryField.getSelectedItem());

      return scope;
    }
  }

  private class DataBackgroundPanel extends Box {

    private static final long serialVersionUID = 5789423098065477610L;

    final JCheckBox advancedCheckBox = new JCheckBox("Advanced");

    final AutoSuggestField laboratoryAccreditationField = new AutoSuggestField(10);

    // TODO: advanced mode
    private final EditStudySamplePanel editStudySamplePanel = new EditStudySamplePanel(false);

    // TODO: advanced mode
    private final EditDietaryAssessmentMethodPanel editDietaryAssessmentMethodPanel =
        new EditDietaryAssessmentMethodPanel(false);

    // TODO: advanced mode
    private final EditAssayPanel editAssayPanel = new EditAssayPanel(false);

    DataBackgroundPanel() {

      super(BoxLayout.PAGE_AXIS);

      final StudyPanel studyPanel = new StudyPanel();
      studyPanel.setBorder(BorderFactory.createTitledBorder("Study"));

      final JButton studySampleButton = new JButton();
      studySampleButton.setToolTipText("Click me to add Study Sample");
      studySampleButton.addActionListener(event -> {

        final ValidatableDialog dlg =
            new ValidatableDialog(editStudySamplePanel, "Create Study sample");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final StudySample studySample = editStudySamplePanel.get();
          // Update button's text
          studySampleButton.setText(studySample.sample);
        }
      });

      final JButton dietaryAssessmentMethodButton = new JButton();
      dietaryAssessmentMethodButton.setToolTipText("Click me to add Dietary assessment method");
      dietaryAssessmentMethodButton.addActionListener(event -> {
        final ValidatableDialog dlg = new ValidatableDialog(editDietaryAssessmentMethodPanel,
            "Create dietary assessment method");

        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final DietaryAssessmentMethod method = editDietaryAssessmentMethodPanel.get();
          // Update button's text
          dietaryAssessmentMethodButton.setText(method.collectionTool);
        }
      });

      laboratoryAccreditationField.setPossibleValues(vocabs.get("Laboratory accreditation"));

      final JButton assayButton = new JButton();
      assayButton.setToolTipText("Click me to add Assay");
      assayButton.addActionListener(event -> {
        final ValidatableDialog dlg = new ValidatableDialog(editAssayPanel, "Create assay");
        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          final Assay assay = editAssayPanel.get();
          // Update button's text
          assayButton.setText(assay.name);
        }
      });

      final JLabel studySampleLabel = createLabel("GM.DataBackgroundPanel.studySampleLabel");
      final JLabel dietaryAssessmentMethodLabel =
          createLabel("GM.DataBackgroundPanel.dietaryAssessmentMethodLabel");
      final JLabel laboratoryAccreditationLabel =
          createLabel("GM.DataBackgroundPanel.laboratoryAccreditationLabel");
      final JLabel assayLabel = createLabel("GM.DataBackgroundPanel.assayLabel");

      final JPanel propertiesPanel = new JPanel(new GridBagLayout());

      EditorNodeDialog.add(propertiesPanel, studyPanel, 0, 0, 3);

      EditorNodeDialog.add(propertiesPanel, studySampleLabel, 0, 1);
      EditorNodeDialog.add(propertiesPanel, studySampleButton, 1, 1);

      EditorNodeDialog.add(propertiesPanel, dietaryAssessmentMethodLabel, 0, 2);
      EditorNodeDialog.add(propertiesPanel, dietaryAssessmentMethodButton, 1, 2);

      EditorNodeDialog.add(propertiesPanel, laboratoryAccreditationLabel, 0, 3);
      EditorNodeDialog.add(propertiesPanel, laboratoryAccreditationField, 1, 3);

      EditorNodeDialog.add(propertiesPanel, assayLabel, 0, 4);
      EditorNodeDialog.add(propertiesPanel, assayButton, 1, 4);

      // Advanced `checkbox`
      advancedCheckBox.addItemListener(event -> {
        // TODO: Implement listener
        studyPanel.advancedComponents.forEach(it -> it.setVisible(advancedCheckBox.isSelected()));
      });

      add(createAdvancedPanel(advancedCheckBox));
      add(Box.createGlue());
      add(propertiesPanel);
    }

    void init(final DataBackground dataBackground) {
      if (dataBackground != null) {
        editStudySamplePanel.init(dataBackground.studySample);
        editDietaryAssessmentMethodPanel.init(dataBackground.dietaryAssessmentMethod);
        editAssayPanel.init(dataBackground.assay);
      }
    }

    DataBackground get() {
      final DataBackground dataBackground = new DataBackground();
      dataBackground.studySample = editStudySamplePanel.get();
      dataBackground.dietaryAssessmentMethod = editDietaryAssessmentMethodPanel.get();
      dataBackground.assay = editAssayPanel.get();

      return dataBackground;
    }
  }

  private class StudyPanel extends JPanel {

    private static final long serialVersionUID = -6572236073945735826L;

    private final JTextField studyIdentifierTextField;
    private final JTextField studyTitleTextField;
    private final JTextArea studyDescriptionTextArea;
    private final AutoSuggestField studyDesignTypeField;
    private final AutoSuggestField studyAssayMeasurementsTypeField;
    private final AutoSuggestField studyAssayTechnologyTypeField;
    private final JTextField studyAssayTechnologyPlatformTextField;
    private final AutoSuggestField accreditationProcedureField;
    private final JTextField studyProtocolNameTextField;
    private final AutoSuggestField studyProtocolTypeField;
    private final JTextField studyProtocolDescriptionTextField;
    private final JTextField studyProtocolURITextField;
    private final JTextField studyProtocolVersionTextField;
    private final AutoSuggestField studyProtocolParametersField;
    private final AutoSuggestField studyProtocolComponentsTypeField;

    private final List<JComponent> advancedComponents;

    StudyPanel() {

      super(new GridBagLayout());

      // Create fields
      studyIdentifierTextField = createTextField();
      studyTitleTextField = createTextField();
      studyDescriptionTextArea = createTextArea();
      studyDesignTypeField = new AutoSuggestField(10);
      studyAssayMeasurementsTypeField = new AutoSuggestField(10);
      studyAssayTechnologyTypeField = new AutoSuggestField(10);
      studyAssayTechnologyPlatformTextField = createTextField();
      accreditationProcedureField = new AutoSuggestField(10);
      studyProtocolNameTextField = createTextField();
      studyProtocolTypeField = new AutoSuggestField(10);
      studyProtocolDescriptionTextField = createTextField();
      studyProtocolURITextField = createTextField();
      studyProtocolVersionTextField = createTextField();
      studyProtocolParametersField = new AutoSuggestField(10);
      studyProtocolComponentsTypeField = new AutoSuggestField(10);

      // Create labels
      final JLabel studyIdentifierLabel = createLabel("GM.StudyPanel.studyIdentifierLabel",
          "GM.StudyPanel.studyIdentifierTooltip", true);
      final JLabel studyTitleLabel =
          createLabel("GM.StudyPanel.studyTitleLabel", "GM.StudyPanel.studyTitleTooltip", true);
      final JLabel studyDescriptionLabel = createLabel("GM.StudyPanel.studyDescriptionLabel",
          "GM.StudyPanel.studyDescriptionTooltip");
      final JLabel studyDesignTypeLabel =
          createLabel("GM.StudyPanel.studyDesignTypeLabel", "GM.StudyPanel.studyDesignTypeTooltip");
      final JLabel studyAssayMeasurementsTypeLabel =
          createLabel("GM.StudyPanel.studyAssayMeasurementsTypeLabel",
              "GM.StudyPanel.studyAssayMeasurementsTypeTooltip");
      final JLabel studyAssayTechnologyTypeLabel =
          createLabel("GM.StudyPanel.studyAssayTechnologyTypeLabel",
              "GM.StudyPanel.studyAssayTechnologyTypeTooltip");
      final JLabel studyAssayTechnologyPlatformLabel =
          createLabel("GM.StudyPanel.studyAssayTechnologyPlatformLabel",
              "GM.StudyPanel.studyAssayTechnologyPlatformTooltip");
      final JLabel accreditationProcedureLabel =
          createLabel("GM.StudyPanel.accreditationProcedureLabel",
              "GM.StudyPanel.accreditationProcedureTooltip");
      final JLabel studyProtocolNameLabel =
          createLabel("GM.StudyPanel.protocolNameLabel", "GM.StudyPanel.protocolNameTooltip");
      final JLabel studyProtocolTypeLabel =
          createLabel("GM.StudyPanel.protocolTypeLabel", "GM.StudyPanel.protocolTypeTooltip");
      final JLabel studyProtocolDescriptionLabel = createLabel(
          "GM.StudyPanel.protocolDescriptionLabel", "GM.StudyPanel.protocolDescriptionTooltip");
      final JLabel studyProtocolURILabel =
          createLabel("GM.StudyPanel.protocolURILabel", "GM.StudyPanel.protocolURITooltip");
      final JLabel studyProtocolVersionLabel = createLabel("GM.StudyPanel.protocolDescriptionLabel",
          "GM.StudyPanel.protocolDescriptionTooltip");
      final JLabel studyProtocolParametersLabel =
          createLabel("GM.StudyPanel.parametersLabel", "GM.StudyPanel.parametersTooltip");
      final JLabel studyProtocolComponentsTypeLabel =
          createLabel("GM.StudyPanel.componentsTypeLabel", "GM.StudyPanel.componentsTypeTooltip");

      // Init combo boxes
      studyDesignTypeField.setPossibleValues(vocabs.get("Study Design Type"));
      studyAssayMeasurementsTypeField.setPossibleValues(vocabs.get("Study Assay Measurement Type"));
      studyAssayTechnologyTypeField.setPossibleValues(vocabs.get("Study Assay Technology Type"));
      accreditationProcedureField.setPossibleValues(vocabs.get("Accreditation procedure Ass.Tec"));
      studyProtocolTypeField.setPossibleValues(vocabs.get("Study Protocol Type"));
      studyProtocolParametersField.setPossibleValues(vocabs.get("Study Protocol Parameters Name"));
      studyProtocolComponentsTypeField
          .setPossibleValues(vocabs.get("Study Protocol Components Type"));

      // Build UI
      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(studyIdentifierLabel, studyIdentifierTextField),
              new Pair<>(studyTitleLabel, studyTitleTextField),
              new Pair<>(studyDescriptionLabel, studyDescriptionTextArea),
              new Pair<>(studyDesignTypeLabel, studyDesignTypeField),
              new Pair<>(studyAssayMeasurementsTypeLabel, studyAssayMeasurementsTypeField),
              new Pair<>(studyAssayTechnologyTypeLabel, studyAssayTechnologyTypeField),
              new Pair<>(studyAssayTechnologyPlatformLabel, studyAssayTechnologyPlatformTextField),
              new Pair<>(accreditationProcedureLabel, accreditationProcedureField),
              new Pair<>(studyProtocolNameLabel, studyProtocolNameTextField),
              new Pair<>(studyProtocolTypeLabel, studyProtocolTypeField),
              new Pair<>(studyProtocolDescriptionLabel, studyProtocolDescriptionTextField),
              new Pair<>(studyProtocolURILabel, studyProtocolURITextField),
              new Pair<>(studyProtocolParametersLabel, studyProtocolParametersField),
              new Pair<>(studyProtocolComponentsTypeLabel, studyProtocolComponentsTypeField));
      EditorNodeDialog.addGridComponents(this, pairs);

      advancedComponents = Arrays.asList(studyDescriptionLabel, studyDescriptionTextArea,
          studyDesignTypeLabel, studyDesignTypeField, studyAssayMeasurementsTypeLabel,
          studyAssayMeasurementsTypeField, studyAssayTechnologyTypeLabel,
          studyAssayTechnologyTypeField, studyAssayTechnologyPlatformLabel,
          studyAssayTechnologyPlatformTextField, accreditationProcedureLabel,
          accreditationProcedureField, studyProtocolNameLabel, studyProtocolNameTextField,
          studyProtocolTypeLabel, studyProtocolTypeField, studyProtocolDescriptionLabel,
          studyProtocolDescriptionTextField, studyProtocolURILabel, studyProtocolURITextField,
          studyProtocolVersionLabel, studyProtocolVersionTextField, studyProtocolParametersLabel,
          studyProtocolParametersField, studyProtocolComponentsTypeLabel,
          studyProtocolComponentsTypeField);

      advancedComponents.forEach(it -> it.setVisible(false));
    }
  }

  private class ModelMathPanel extends Box {

    private static final long serialVersionUID = -7488943574135793595L;

    final JCheckBox advancedCheckBox = new JCheckBox("Advanced");

    ModelMathPanel() {

      super(BoxLayout.PAGE_AXIS);

      final boolean isAdvanced = advancedCheckBox.isSelected();

      final ParametersPanel parametersPanel = new ParametersPanel(isAdvanced);
      final QualityMeasuresPanel qualityMeasuresPanel =
          new QualityMeasuresPanel(null, null, null, null, null, null);
      final ModelEquationsPanel modelEquationPanel = new ModelEquationsPanel(isAdvanced);

      final JPanel propertiesPanel = new JPanel(new GridBagLayout());
      EditorNodeDialog.add(propertiesPanel, parametersPanel, 0, 0);
      EditorNodeDialog.add(propertiesPanel, qualityMeasuresPanel, 0, 1);
      EditorNodeDialog.add(propertiesPanel, modelEquationPanel, 0, 2);

      add(createAdvancedPanel(advancedCheckBox));
      add(Box.createGlue());
      add(propertiesPanel);

      advancedCheckBox.addItemListener(event -> {
        parametersPanel.isAdvanced = advancedCheckBox.isSelected();
        modelEquationPanel.isAdvanced = advancedCheckBox.isSelected();
      });
    }
  }

  private class ParametersPanel extends JPanel {

    private static final long serialVersionUID = -5986975090954482038L;

    boolean isAdvanced;

    ParametersPanel(final boolean isAdvanced) {

      super(new BorderLayout());

      this.isAdvanced = isAdvanced;

      setBorder(BorderFactory.createTitledBorder("Parameters"));

      final NonEditableTableModel tableModel = new NonEditableTableModel();
      // TODO: parameters

      final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
        private static final long serialVersionUID = -2930961770705064623L;

        protected void setValue(Object value) {
          setText(((Parameter) value).id);
        };
      };
      final JTable myTable = new HeadlessTable(tableModel, renderer);

      // buttons
      final ButtonsPanel buttonsPanel = new ButtonsPanel();
      buttonsPanel.addButton.addActionListener(event -> {
        final EditParameterPanel editPanel = new EditParameterPanel(this.isAdvanced);
        final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Create parameter");
        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          tableModel.addRow(new Parameter[] {editPanel.get()});
        }
      });

      buttonsPanel.modifyButton.addActionListener(event -> {
        final int rowToEdit = myTable.getSelectedRow();
        if (rowToEdit != -1) {

          final Parameter param = (Parameter) tableModel.getValueAt(rowToEdit, 0);
          final EditParameterPanel editPanel = new EditParameterPanel(this.isAdvanced);
          editPanel.init(param);

          final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Modify parameter");
          if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
            tableModel.setValueAt(editPanel.get(), rowToEdit, 0);
          }
        }
      });

      buttonsPanel.removeButton.addActionListener(event -> {
        final int rowToDelete = myTable.getSelectedRow();
        if (rowToDelete != -1) {
          tableModel.removeRow(rowToDelete);
        }
      });

      add(myTable, BorderLayout.NORTH);
      add(buttonsPanel, BorderLayout.SOUTH);
    }
  }

  private class QualityMeasuresPanel extends JPanel {

    private static final long serialVersionUID = -5829602676812905793L;

    final SpinnerNumberModel sseSpinnerModel = createSpinnerDoubleModel();
    final SpinnerNumberModel mseSpinnerModel = createSpinnerDoubleModel();
    final SpinnerNumberModel rmseSpinnerModel = createSpinnerDoubleModel();
    final SpinnerNumberModel r2SpinnerModel = createSpinnerDoubleModel();
    final SpinnerNumberModel aicSpinnerModel = createSpinnerDoubleModel();
    final SpinnerNumberModel bicSpinnerModel = createSpinnerDoubleModel();

    QualityMeasuresPanel(final Double sse, final Double mse, final Double rmse, final Double r2,
        final Double aic, final Double bic) {

      final JLabel sseLabel = new JLabel("SSE");
      final JSpinner sseSpinner = createSpinner(sseSpinnerModel);

      final JLabel mseLabel = new JLabel("MSE");
      final JSpinner mseSpinner = createSpinner(mseSpinnerModel);

      final JLabel rmseLabel = new JLabel("RMSE");
      final JSpinner rmseSpinner = createSpinner(rmseSpinnerModel);

      final JLabel r2Label = new JLabel("r-Squared");
      final JSpinner r2Spinner = createSpinner(r2SpinnerModel);

      final JLabel aicLabel = new JLabel("AIC");
      final JSpinner aicSpinner = createSpinner(aicSpinnerModel);

      final JLabel bicLabel = new JLabel("BIC");
      final JSpinner bicSpinner = createSpinner(bicSpinnerModel);

      final List<Pair<JLabel, JComponent>> pairs =
          Arrays.asList(new Pair<>(sseLabel, sseSpinner), new Pair<>(mseLabel, mseSpinner),
              new Pair<>(rmseLabel, rmseSpinner), new Pair<>(r2Label, r2Spinner),
              new Pair<>(aicLabel, aicSpinner), new Pair<>(bicLabel, bicSpinner));
      EditorNodeDialog.addGridComponents(this, pairs);

      setBorder(BorderFactory.createTitledBorder("Quality measures"));
    }
  }

  private class ModelEquationsPanel extends JPanel {

    private static final long serialVersionUID = 7194287921709100267L;

    final NonEditableTableModel tableModel = new NonEditableTableModel();
    final List<ModelEquation> equations = new ArrayList<>();
    boolean isAdvanced;

    ModelEquationsPanel(final boolean isAdvanced) {

      super(new BorderLayout());
      this.isAdvanced = isAdvanced;

      setBorder(BorderFactory.createTitledBorder("Model equation"));
      equations.forEach(it -> tableModel.addRow(new ModelEquation[] {it}));

      final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
        private static final long serialVersionUID = 1L;

        protected void setValue(Object value) {
          setText(((ModelEquation) value).equationName);
        }
      };

      final HeadlessTable myTable = new HeadlessTable(tableModel, renderer);

      final ButtonsPanel buttonsPanel = new ButtonsPanel();

      buttonsPanel.addButton.addActionListener(event -> {
        final EditModelEquationPanel editPanel = new EditModelEquationPanel(null, isAdvanced);
        final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Create equation");
        if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
          tableModel.addRow(new ModelEquation[] {editPanel.get()});
        }
      });

      buttonsPanel.modifyButton.addActionListener(event -> {
        final int rowToEdit = myTable.getSelectedRow();
        if (rowToEdit != -1) {
          final ModelEquation equation = (ModelEquation) tableModel.getValueAt(rowToEdit, 0);
          final EditModelEquationPanel editPanel = new EditModelEquationPanel(equation, isAdvanced);
          final ValidatableDialog dlg = new ValidatableDialog(editPanel, "Modify equation");
          if (dlg.getValue().equals(JOptionPane.OK_OPTION)) {
            tableModel.setValueAt(editPanel.get(), rowToEdit, 0);
          }
        }
      });

      buttonsPanel.removeButton.addActionListener(event -> {
        final int rowToDelete = myTable.getSelectedRow();
        if (rowToDelete != -1) {
          tableModel.removeRow(rowToDelete);
        }
      });

      add(myTable, BorderLayout.NORTH);
      add(buttonsPanel, BorderLayout.SOUTH);
    }
  }
}