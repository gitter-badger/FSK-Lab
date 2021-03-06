/*
 ***************************************************************************************************
 * Copyright (c) 2018 Federal Institute for Risk Assessment (BfR), Germany
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.ecore.util.EcoreUtil.EqualityHelper;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.sbml.jsbml.validator.SyntaxChecker;
import de.bund.bfr.knime.fsklab.FskPortObject;
import de.bund.bfr.knime.fsklab.FskSimulation;
import de.bund.bfr.knime.fsklab.SimulationEntity;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FLabel;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FPanel;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FSpinner;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FTextField;
import de.bund.bfr.knime.fsklab.nodes.common.ui.UIUtils;
import metadata.MetadataFactory;
import metadata.ModelMath;
import metadata.Parameter;
import metadata.ParameterType;

public class SimulatorNodeDialog extends DataAwareNodeDialogPane {
  private static NodeLogger LOGGER = NodeLogger.getLogger("SimulatorNodeDialog");
  /*
   * TODO: addString and removeString should be replaced with ResourceBundle.
   */
  private static final String addString = "Add";
  private static final String removeString = "Remove";

  private JList<SimulationEntity> list;

  private DefaultListModel<SimulationEntity> simulation_listModel;

  private JButton addButton;

  private JTextField simulationName;

  private FPanel simulationSettingPanel;

  private SimulationEntity defaultSimulation;

  private SimulatorNodeSettings settings;

  private Map<String, Parameter> parameterMap;

  private SimulationEntity currentSimulation;

  private JPanel settingPanel;

  private ModelMath modelMath = MetadataFactory.eINSTANCE.createModelMath();
  
  EqualityHelper equalHelper = new EqualityHelper();
  
  private FskPortObject inObj;
  
  public SimulatorNodeDialog() {

    simulation_listModel = new DefaultListModel<>();
    list = new JList<>(simulation_listModel);

    addButton = UIUtils.createAddButton();

    simulationName = new JTextField(10);

    simulationSettingPanel = new FPanel();
    
    simulationSettingPanel.setLayout(new BorderLayout());

    // TODO: defaultSimulation should be initialized here

    settings = new SimulatorNodeSettings();

    parameterMap = new TreeMap<>();

    // TODO: currentSimulation should be initialized here

    settingPanel = new JPanel(new BorderLayout());

    modelMath = MetadataFactory.eINSTANCE.createModelMath();

    createUI();
    addTab("Options", settingPanel);
  }

  private void createUI() {

    JButton removeButton = UIUtils.createRemoveButton();

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {

        if (e.getValueIsAdjusting() == false) {

          @SuppressWarnings("unchecked")
          JList<SimulationEntity> list = (JList<SimulationEntity>) e.getSource();

          int selections[] = list.getSelectedIndices();
          Object selectionValues[] = list.getSelectedValuesList().toArray();

          for (int i = 0, n = selections.length; i < n; i++) {
            currentSimulation = (SimulationEntity) selectionValues[i];
          }

          // If selection enable the fire button
          removeButton.setEnabled((list.getSelectedIndex() != -1
              && !currentSimulation.getSimulationName().equals("defaultSimulation")));

          updatePanel();
        }
      }
    });

    AddSimulationListener addSimulationListener = new AddSimulationListener();

    addButton.setActionCommand(addString);
    addButton.addActionListener(addSimulationListener);
    addButton.setEnabled(false);

    removeButton.setActionCommand(removeString);
    removeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // This method can be called only if there's a valid selection so go ahead and remove
        // whatever's selected.
        int index = list.getSelectedIndex();

        // Default simulation (at position 0) should be fixed (cannot be removed)
        if (!list.getSelectedValue().getSimulationName().equals("defaultSimulation")) {
          simulation_listModel.remove(index);

          int size = simulation_listModel.getSize();

          if (size == 0) { // Nobody's left, disable firing.
            removeButton.setEnabled(false);
          } else { // Select an index.
            if (index == simulation_listModel.getSize()) {
              // removed item in last position
              index--;
            }

            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
          }
        }
      }
    });

    simulationName.addActionListener(addSimulationListener);
    simulationName.getDocument().addDocumentListener(addSimulationListener);

    JPanel centerPanel = new JPanel(new GridBagLayout());

    GridBagConstraints contraints = new GridBagConstraints();
    contraints.weightx = 1.0;
    contraints.weighty = 1.0;
    contraints.fill = GridBagConstraints.BOTH;
    contraints.anchor = GridBagConstraints.NORTHWEST;
    centerPanel.add(simulationSettingPanel, contraints);

    JScrollPane scroll = new JScrollPane();
    scroll.setViewportView(simulationSettingPanel);

    JScrollPane listScrollPane = new JScrollPane(list);
    FPanel buttonPane = UIUtils.createHorizontalPanel(removeButton, simulationName, addButton);

    // Create a panel that uses BoxLayout.
    JPanel leftPane = new JPanel(new BorderLayout());
    leftPane.add(listScrollPane, BorderLayout.CENTER);
    leftPane.add(buttonPane, BorderLayout.SOUTH);

    settingPanel.setPreferredSize(new Dimension(600, 400));
    settingPanel.setBackground(UIUtils.WHITE);
    settingPanel.add(scroll, BorderLayout.CENTER);
    settingPanel.add(leftPane, BorderLayout.WEST);
  }

  public void updatePanel() {
    if (parameterMap.isEmpty() && modelMath != null) {
      for (Parameter param : modelMath.getParameter()) {
        parameterMap.put(param.getParameterID().toLowerCase().trim(), param);
      }
    }

    simulationSettingPanel.removeAll();
    simulationSettingPanel.revalidate();
    simulationSettingPanel.repaint();

    // Remove titled border if no simulation is selected
    if (simulation_listModel.size() == 0) {
      simulationSettingPanel.setBorder(null);
      return;
    }

    Border titledBorder = BorderFactory.createTitledBorder(currentSimulation.getSimulationName());
    Border emptyBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
    Border compoundBorder = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    simulationSettingPanel.setBorder(compoundBorder);

    List<Parameter> parameters = currentSimulation.getSimulationParameters();

    List<FLabel> labels = new ArrayList<>(parameters.size());
    List<JComponent> fields = new ArrayList<>(parameters.size());

    // Listener for text fields holding parameter values
    FocusListener focusListener = new FocusListener() {

      @Override
      public void focusLost(FocusEvent e) {
        JTextField field = (JTextField) e.getComponent();
        try {
          Double.parseDouble(field.getText());
        } catch (NumberFormatException exception) {
          field.setText("0.0");
          JOptionPane.showMessageDialog(simulationSettingPanel,
              "Please provide numeric value only!", "Error", JOptionPane.ERROR_MESSAGE);
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        // does nothing
      }
    };
  
    for (Parameter param : parameters) {
      try {
      Parameter fullParam = parameterMap.get(param.getParameterID().toLowerCase().trim());

      final ParameterType dataType = fullParam.getParameterDataType();

      if (dataType != null && (dataType == ParameterType.INTEGER || dataType == ParameterType.DOUBLE
          || dataType == ParameterType.NUMBER)) {

        SpinnerNumberModel spinnerModel = null;

        final String minValue = fullParam.getParameterValueMin();
        final String maxValue = fullParam.getParameterValueMax();
        final String value = param.getParameterValue();
        if (fullParam.getParameterDataType() == ParameterType.INTEGER) {
         
          int min = minValue !=null ? minValue.isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(minValue):Integer.MIN_VALUE ;
          int max = maxValue !=null ? maxValue.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(maxValue):Integer.MAX_VALUE ;  

          if (value != null && !value.isEmpty()) {
            try {
              spinnerModel = new SpinnerNumberModel(Integer.parseInt(value), min, max, 1);
            } catch (java.lang.NumberFormatException e) {
              spinnerModel = new SpinnerNumberModel(Double.parseDouble(value), min, max, 1);
            }
          } else {
            spinnerModel = new SpinnerNumberModel(0, min, max, 1);
          }
        }

        else if (fullParam.getParameterDataType() == ParameterType.DOUBLE) {

          double min = minValue !=null ?minValue.isEmpty() ? Integer.MIN_VALUE : Double.parseDouble(minValue):Double.MIN_VALUE ;
          double max = maxValue !=null ?maxValue.isEmpty() ? Integer.MAX_VALUE : Double.parseDouble(maxValue):Double.MAX_VALUE ;

          if (value != null && !value.isEmpty()) {
            Double doubleValue = Double.parseDouble(value);
            spinnerModel = new SpinnerNumberModel(doubleValue.doubleValue()<0?doubleValue.doubleValue()*-1:doubleValue.doubleValue(), min, max, 0.1);
            spinnerModel.setValue(doubleValue.doubleValue());
          } else {
            spinnerModel = new SpinnerNumberModel(0.0, min, max, 0.1);
          }
        }

        FSpinner paramField = new FSpinner(spinnerModel, false);
        paramField.addFocusListener(focusListener);
        prepareField(paramField, param.getParameterID(), currentSimulation.getSimulationName());

        labels.add(createParameterLabel(paramField, param.getParameterID(),
            fullParam.getParameterDescription()));
        fields.add(createParameterPanel(paramField, fullParam.getParameterUnit()));
      } else {
        FTextField paramField = new FTextField();
        paramField.setColumns(10);
        paramField.setText(param.getParameterID());
        prepareField(paramField, param.getParameterID(), currentSimulation.getSimulationName());

        labels.add(createParameterLabel(paramField, param.getParameterID(), ""));
        fields.add(createParameterPanel(paramField, fullParam.getParameterUnit()));
      }
      }catch(Exception ex ) {
        ex.printStackTrace();
      }
    }
    

    FPanel formPanel = UIUtils.createFormPanel(labels, fields);
    FPanel northPanel = UIUtils.createNorthPanel(formPanel);

    simulationSettingPanel.add(northPanel, BorderLayout.CENTER);
  }

  /**
   * Helper function for {@link #updatePanel()}.
   * 
   * @param valueField JComponent with parameter's value
   * @param unit Parameter's unit
   * @return panel with parameter's value and unit.
   */
  private static FPanel createParameterPanel(JComponent valueField, String unit) {

    FPanel panel = new FPanel();
    panel.setLayout(new FlowLayout(FlowLayout.LEFT));
    panel.add(valueField);
    panel.add(new JLabel(unit));

    return panel;
  }

  /**
   * Helper function for {@link #updatePanel()}.
   *
   * @param comp component with the parameter's value
   * @param name parameter name
   * @param toolTip parameter description
   * @return label with parameter name and its description as tooltip
   */
  private static FLabel createParameterLabel(JComponent comp, String name, String toolTip) {

    FLabel label = new FLabel(name);
    label.setLabelFor(comp);
    label.setToolTipText(toolTip);

    return label;
  }

  /**
   * Helper function for {@link #updatePanel()}.
   * 
   * @param comp component with the parameter's value
   * @param parameterName
   * @param simulationName
   */
  private void prepareField(JComponent comp, String parameterName, String simulationName) {
    comp.putClientProperty("id", parameterName);
    comp.setEnabled(!simulationName.equals("defaultSimulation"));
    if (comp instanceof JSpinner) {
      ((JSpinner) comp).addChangeListener(new SpinnerListerner());
    }
    comp.addKeyListener(new SimulationParameterValueListener());
  }

  class SpinnerListerner implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent e) {
      SpinnerModel model = ((JSpinner) e.getSource()).getModel();
      JSpinner source = ((JSpinner) e.getSource());
      for (Parameter param : currentSimulation.getSimulationParameters()) {
        if (param.getParameterID().equalsIgnoreCase((String) source.getClientProperty("id"))) {
          param.setParameterValue("" + model.getValue());
        }
      }
    }
  }
  class SimulationParameterValueListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent arg0) {}

    @Override
    public void keyReleased(KeyEvent arg0) {

      JTextField source = ((JTextField) arg0.getSource());

      for (Parameter param : currentSimulation.getSimulationParameters()) {
        if (param.getParameterID().equalsIgnoreCase((String) source.getClientProperty("id"))) {

          try {
            // Try to parse the value as integer or double. If no error set the background
            // to white. Otherwise red.
            if (param.getParameterDataType() == ParameterType.INTEGER) {
              Integer.parseInt(source.getText());
            } else if (param.getParameterDataType() == ParameterType.DOUBLE) {
              Double.parseDouble(source.getText());
            } else {
              break;
            }
            source.setBackground(Color.WHITE);
          } catch (Exception e) {
            param.setParameterValue(source.getText());
            source.setBackground(Color.RED);
          }
        }
      }
    }

    @Override
    public void keyTyped(KeyEvent arg0) {}
  }

  // This listener is shared by the text field and the hire button.
  class AddSimulationListener implements ActionListener, DocumentListener {

    private boolean alreadyEnabled = false;

    // Required by ActionListener.
    public void actionPerformed(ActionEvent e) {

      String name = simulationName.getText();

      // User didn't type in a unique name...
      if (StringUtils.isBlank(name) || alreadyInList(name)
          || !SyntaxChecker.isValidId(name, 3, 1)) {
        Toolkit.getDefaultToolkit().beep();
        simulationName.requestFocusInWindow();
        simulationName.selectAll();
        return;
      }

      int index = list.getSelectedIndex(); // get selected index
      // If no selection (-1) insert at the beginning (0). Otherwise add after the selected item.
      index = index == -1 ? 0 : index + 1;

      SimulationEntity sE = new SimulationEntity();
      sE.setSimulationName(simulationName.getText());
      if (defaultSimulation == null) {
        defaultSimulation = simulation_listModel.get(0);
      }

      List<Parameter> simulationParameters = new ArrayList<>();
      for (Parameter entry : defaultSimulation.getSimulationParameters()) {

        Parameter p = MetadataFactory.eINSTANCE.createParameter();

        p.setParameterID(entry.getParameterID().toLowerCase().trim());
        p.setParameterValue(entry.getParameterValue());

        simulationParameters.add(p);
      }
      sE.setSimulationParameters(simulationParameters);

      simulation_listModel.addElement(sE);
      // If we just wanted to add to the end, we'd do this:
      // listModel.addElement(simulationName.getText());

      // Reset the text field.
      simulationName.requestFocusInWindow();
      simulationName.setText("");

      // Select the new item and make it visible.
      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
      updatePanel();
    }

    // This method tests for string equality. You could certainly
    // get more sophisticated about the algorithm. For example,
    // you might want to ignore white space and capitalization.
    protected boolean alreadyInList(String name) {
      for (int i = 0; i < simulation_listModel.size(); i++) {
        if (name.equals(simulation_listModel.getElementAt(i).getSimulationName()))
          return true;
      }
      return false;
    }

    // Required by DocumentListener.
    public void insertUpdate(DocumentEvent e) {
      enableButton();
    }

    // Required by DocumentListener.
    public void removeUpdate(DocumentEvent e) {
      handleEmptyTextField(e);
    }

    // Required by DocumentListener.
    public void changedUpdate(DocumentEvent e) {
      if (!handleEmptyTextField(e)) {
        enableButton();
      }
    }

    private void enableButton() {
      if (!alreadyEnabled) {
        addButton.setEnabled(true);
      }
    }

    private boolean handleEmptyTextField(DocumentEvent e) {

      if (e.getDocument().getLength() <= 0) {
        addButton.setEnabled(false);
        alreadyEnabled = false;
        return true;
      }

      return false;
    }
  }

  @Override
  protected void loadSettingsFrom(NodeSettingsRO settings, PortObject[] input)
      throws NotConfigurableException {

    final SimulatorNodeSettings simulationSettings = new SimulatorNodeSettings();
    try {
      simulationSettings.loadSettings(settings);
    } catch (InvalidSettingsException exception) {
      throw new NotConfigurableException("InvalidSettingsException", exception);
    }
    inObj = (FskPortObject) input[0];

    // If connected to same FSK model (input) then load simulations from settings
    
    if (simulationSettings.getModelStript() != null && simulationSettings.getModelStript().equals(inObj.model)) {

      // Updates settings
      this.settings = simulationSettings;
      this.settings.setModelStript(simulationSettings.getModelStript());
      modelMath = inObj.modelMath;

      simulation_listModel = new DefaultListModel<>();

      list.removeAll();
      list.revalidate();
      list.repaint();
      list.setModel(simulation_listModel);

      List<SimulationEntity> savedList = simulationSettings.getListOfSimulation();
      if (savedList != null && !savedList.isEmpty()) {
        savedList.forEach(simulation_listModel::addElement);
      }

      list.setSelectedIndex(0);
      currentSimulation = savedList.get(0);
      updatePanel();
    }

    // If different (or new) connected FSK model then load simulations from input model
    else {
      this.settings.setModelStript(simulationSettings.getModelStript());
      modelMath = inObj.modelMath;

      simulation_listModel.clear();

      
      if (parameterMap.isEmpty()) {
        for (Parameter param : modelMath.getParameter()) {
          parameterMap.put(param.getParameterID().toLowerCase().trim(), param);
        }
      }

      // Add simulations from input model
      for (FskSimulation fskSimulation : inObj.simulations) {

        List<Parameter> params = new ArrayList<>(fskSimulation.getParameters().size());
        for (Map.Entry<String, String> entry : fskSimulation.getParameters().entrySet()) {
          Parameter p = MetadataFactory.eINSTANCE.createParameter();

          p.setParameterID(parameterMap.get(entry.getKey().toLowerCase().trim()).getParameterID().toLowerCase().trim());
          p.setParameterValue(entry.getValue());

          params.add(p);
        }

        SimulationEntity se = new SimulationEntity();
        se.setSimulationName(fskSimulation.getName());
        se.setSimulationParameters(params);

        simulation_listModel.addElement(se);
      }

      currentSimulation = simulation_listModel.firstElement();
      list.setSelectedIndex(0);
      list.ensureIndexIsVisible(0);
      list.revalidate();
      list.repaint();
      updatePanel();
    }
  }

  public static List<SimulationEntity> asList(final DefaultListModel<SimulationEntity> model) {
    return new AbstractList<SimulationEntity>() {

      @Override
      public SimulationEntity get(int index) {
        return (SimulationEntity) model.getElementAt(index);
      }

      @Override
      public int size() {
        return model.size();
      }
    };
  }

  @Override
  protected void saveSettingsTo(NodeSettingsWO settings) throws InvalidSettingsException {
    List<SimulationEntity> simulationList = asList(simulation_listModel);
    this.settings.setModelStript( inObj.model);
    this.settings.setListOfSimulation(simulationList);
    this.settings.saveSettings(settings);
  }
}
