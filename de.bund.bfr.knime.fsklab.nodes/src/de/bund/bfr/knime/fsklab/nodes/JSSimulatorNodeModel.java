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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectHolder;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.web.ValidationError;
import org.knime.js.core.node.AbstractWizardNodeModel;
import de.bund.bfr.knime.fsklab.FskPortObject;
import de.bund.bfr.knime.fsklab.FskSimulation;
import de.bund.bfr.knime.fsklab.nodes.JSSimulatorViewValue.JSSimulation;
import de.bund.bfr.knime.fsklab.rakip.Parameter;

class JSSimulatorNodeModel
    extends AbstractWizardNodeModel<JSSimulatorViewRepresentation, JSSimulatorViewValue>
    implements PortObjectHolder {

  private FskPortObject port;

  // Input and output port types
  private static final PortType[] IN_TYPES = {FskPortObject.TYPE};
  private static final PortType[] OUT_TYPES = {FskPortObject.TYPE};

  private static final String VIEW_NAME = new JSSimulatorNodeFactory().getInteractiveViewName();

  public JSSimulatorNodeModel() {
    super(IN_TYPES, OUT_TYPES, VIEW_NAME);
  }

  @Override
  public JSSimulatorViewRepresentation createEmptyViewRepresentation() {
    return new JSSimulatorViewRepresentation();
  }

  @Override
  public JSSimulatorViewValue createEmptyViewValue() {
    return new JSSimulatorViewValue();
  }

  @Override
  public String getJavascriptObjectID() {
    return "de.bund.bfr.knime.fsklab.nodes.jssimulator";
  }

  @Override
  public boolean isHideInWizard() {
    return false;
  }

  @Override
  public ValidationError validateViewValue(JSSimulatorViewValue viewContent) {
    return null;
  }

  @Override
  public void saveCurrentValue(NodeSettingsWO content) {}

  @Override
  public JSSimulatorViewValue getViewValue() {
    JSSimulatorViewValue val;
    synchronized (getLock()) {
      val = super.getViewValue();

      if (val == null) {
        val = createEmptyViewValue();
      }

      if (val.simulations == null && port != null && port.simulations != null) {
        // Convert from FskSimulation(s) to JSSimulation(s)
        val.simulations = port.simulations.stream()
            .map(it -> toJSSimulation(it, port.genericModel.modelMath.parameter))
            .collect(Collectors.toList());
      }
    }

    return val;
  }

  @Override
  public JSSimulatorViewRepresentation getViewRepresentation() {
    JSSimulatorViewRepresentation rep;
    synchronized (getLock()) {
      rep = super.getViewRepresentation();
      if (rep == null) {
        rep = createEmptyViewRepresentation();
      }
      if (rep.parameters == null && port != null) {
        // Take only input parameters from metadata
        rep.parameters = port.genericModel.modelMath.parameter.stream()
            .filter(p -> p.classification.equals("Input-environmental factor")
                || p.classification.equals("Input-hazard"))
            .collect(Collectors.toList());
      }
    }

    return rep;
  }

  @Override
  protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs) throws InvalidSettingsException {
    return inSpecs;
  }

  @Override
  protected PortObject[] performExecute(PortObject[] inObjects, ExecutionContext exec)
      throws IOException {

    FskPortObject inObj = (FskPortObject) inObjects[0];
    FskPortObject outObj = new FskPortObject(inObj.model, inObj.param, inObj.viz,
        inObj.genericModel, inObj.workspace, inObj.libs, inObj.workingDirectory);

    synchronized (getLock()) {

      JSSimulatorViewValue val = getViewValue();

      // If not executed
      if (val.simulations == null) {

        // Convert FskSimulation(s) to JSSimulation(s)
        val.simulations = inObj.simulations.stream()
            .map(it -> toJSSimulation(it, inObj.genericModel.modelMath.parameter))
            .collect(Collectors.toList());

        port = inObj;
      }

      // Takes modified simulations from val

      // Converts JSSimulation(s) back to FskSimulation(s)
      for (JSSimulation jsSimulation : val.simulations) {
        FskSimulation fskSimulation = new FskSimulation(jsSimulation.name);

        List<Parameter> inputParams = getViewRepresentation().parameters;

        for (int i = 0; i < inputParams.size(); i++) {
          String paramName = inputParams.get(i).id;
          String paramValue = jsSimulation.values.get(i);

          fskSimulation.getParameters().put(paramName, paramValue);
        }
        outObj.simulations.add(fskSimulation);
      }

      port = inObj;
    }

    exec.setProgress(1);

    return new PortObject[] {outObj};
  }

  @Override
  protected void performReset() {
    port = null;
  }

  @Override
  protected void useCurrentValueAsDefault() {}

  @Override
  protected void saveSettingsTo(NodeSettingsWO settings) {}

  @Override
  protected void validateSettings(NodeSettingsRO settings) throws InvalidSettingsException {}

  @Override
  protected void loadValidatedSettingsFrom(NodeSettingsRO settings)
      throws InvalidSettingsException {}

  @Override
  public PortObject[] getInternalPortObjects() {
    return new PortObject[] {port};
  }

  @Override
  public void setInternalPortObjects(PortObject[] portObjects) {
    port = (FskPortObject) portObjects[0];
  }

  public void setHideInWizard(boolean hide) {}

  private JSSimulation toJSSimulation(FskSimulation fskSim, List<Parameter> params) {
    JSSimulation jsSim = new JSSimulation();
    jsSim.name = fskSim.getName();
    jsSim.values = params.stream()
        .filter(p -> p.classification.equals("Input-environmental factor")
            || p.classification.equals("Input-hazard"))
        .map(p -> fskSim.getParameters().get(p.id)).collect(Collectors.toList());

    return jsSim;
  }
}