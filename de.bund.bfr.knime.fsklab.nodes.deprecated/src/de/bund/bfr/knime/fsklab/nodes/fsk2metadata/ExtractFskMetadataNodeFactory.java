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
package de.bund.bfr.knime.fsklab.nodes.fsk2metadata;

import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeView;
import org.knime.core.node.StatelessModel;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import de.bund.bfr.knime.fsklab.nodes.FskMetaDataTuple;
import de.bund.bfr.knime.pmm.fskx.port.FskPortObject;

@Deprecated
public class ExtractFskMetadataNodeFactory extends NodeFactory<NodeModel> {

  // Input and output port types
  private static final PortType[] IN_TYPES = {FskPortObject.TYPE};
  private static final PortType[] OUT_TYPES = {BufferedDataTable.TYPE};

  @Override
  public NodeModel createNodeModel() {
    return new StatelessModel(IN_TYPES, OUT_TYPES) {
      @Override
      protected PortObjectSpec[] configure(PortObjectSpec[] inSpecs)
          throws InvalidSettingsException {
        return new PortObjectSpec[] {FskMetaDataTuple.createSpec()};
      }

      @Override
      protected PortObject[] execute(PortObject[] inObjects, ExecutionContext exec) {
        FskPortObject inObj = (FskPortObject) inObjects[0];

        final BufferedDataContainer container =
            exec.createDataContainer(FskMetaDataTuple.createSpec());

        final FskMetaDataTuple tuple = new FskMetaDataTuple(inObj.template);
        container.addRowToTable(tuple);
        container.close();

        return new PortObject[] {container.getTable()};
      }
    };
  }

  @Override
  public int getNrNodeViews() {
    return 0;
  }

  @Override
  public NodeView<NodeModel> createNodeView(final int viewIndex, final NodeModel nodeModel) {
    return null;
  }

  @Override
  public boolean hasDialog() {
    return false;
  }

  @Override
  public NodeDialogPane createNodeDialogPane() {
    return new DefaultNodeSettingsPane();
  }
}
